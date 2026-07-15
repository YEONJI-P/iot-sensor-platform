/* ============================================================
   센서 대시보드 — 실시간 텔레메트리 모니터링
   - window.Auth(공유 인증) 사용, Chart.js 라인/바 차트
   - SSE 실시간 스트림 + 30초 폴링 폴백
   ============================================================ */
(function () {
  'use strict';

  const ALLOWED = ['SYSTEM_ADMIN', 'MEMBER', 'VIEWER'];

  /* 산업 계기판 팔레트 (app.css 토큰과 정합) */
  const C = {
    signal: '#2dd4bf', // 틸 — 정상 텔레메트리
    brand:  '#f2a63b', // 앰버 — 임계값
    alarm:  '#ff5d52', // 레드 — 초과 지점
    grid:   '#1f2630',
    tick:   '#5c6775',
  };

  const MAX_POINTS = 50;

  /* ── 상태 ── */
  let lineChart = null;
  let barChart = null;
  let currentDeviceId = '';
  let currentThreshold = null;
  let pollTimer = null;
  let clockTimer = null;
  let sse = null;

  /* ── DOM 헬퍼 ── */
  const $ = (id) => document.getElementById(id);

  function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function num(v) {
    return (typeof v === 'number' && isFinite(v)) ? v : null;
  }
  function fmtNum(v) {
    const n = num(v);
    return n == null ? '—' : (Number.isInteger(n) ? String(n) : n.toFixed(1));
  }

  function fmtTime(dt) {
    if (!dt) return '';
    const d = new Date(dt);
    if (isNaN(d)) return '';
    return d.toLocaleTimeString('ko-KR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
  function fmtDateTime(dt) {
    if (!dt) return '';
    const d = new Date(dt);
    if (isNaN(d)) return '';
    return d.toLocaleString('ko-KR', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  let toastTimer = null;
  function toast(msg, isErr) {
    const el = $('toast');
    if (!el) return;
    el.textContent = msg;
    el.className = 'toast show' + (isErr ? ' err' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.className = 'toast' + (isErr ? ' err' : ''); }, 3200);
  }

  /* ── 시계 ── */
  function startClock() {
    const el = $('clock');
    const tick = () => { el.textContent = new Date().toLocaleTimeString('ko-KR', { hour12: false }); };
    tick();
    if (!clockTimer) clockTimer = setInterval(tick, 1000);
  }

  /* ── SSE 연결 램프 ── */
  function setSseLamp(state) {
    const lamp = $('sseLamp');
    const label = $('sseLabel');
    if (state === 'ok') {
      lamp.className = 'lamp ok';
      label.textContent = 'STREAM · 연결됨';
    } else if (state === 'alarm') {
      lamp.className = 'lamp alarm';
      label.textContent = 'STREAM · 재연결 중';
    } else {
      lamp.className = 'lamp idle';
      label.textContent = 'STREAM · 대기';
    }
  }

  /* ── 정체성(사원번호·역할) ── */
  function renderIdentity() {
    const role = Auth.getRole();
    const rl = Auth.ROLE_LABEL[role] || { text: role || '—', cls: '' };
    $('navEmployee').textContent = Auth.getEmployeeId() || '';
    const rt = $('navRole');
    rt.textContent = rl.text;
    rt.className = 'tag ' + rl.cls;
  }

  /* ── 차트 초기화 ── */
  function baseScales(yStep) {
    return {
      x: { ticks: { color: C.tick, maxTicksLimit: 8, font: { family: 'IBM Plex Mono' } }, grid: { color: C.grid } },
      y: {
        beginAtZero: yStep === 1 ? true : false,
        ticks: { color: C.tick, font: { family: 'IBM Plex Mono' }, stepSize: yStep },
        grid: { color: C.grid },
      },
    };
  }

  function initCharts() {
    const lineCtx = $('lineChart').getContext('2d');
    lineChart = new Chart(lineCtx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: '센서값',
            data: [],
            borderColor: C.signal,
            backgroundColor: 'rgba(45,212,191,.08)',
            borderWidth: 2,
            pointRadius: 3,
            pointHoverRadius: 5,
            pointBackgroundColor: [],
            tension: 0.25,
            fill: true,
          },
          {
            label: '임계값',
            data: [],
            borderColor: C.brand,
            borderWidth: 1.5,
            borderDash: [6, 4],
            pointRadius: 0,
            fill: false,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { labels: { color: C.tick, boxWidth: 12, font: { family: 'IBM Plex Sans' } } } },
        scales: baseScales(),
        animation: { duration: 250 },
      },
    });

    const barCtx = $('barChart').getContext('2d');
    barChart = new Chart(barCtx, {
      type: 'bar',
      data: {
        labels: [],
        datasets: [{
          label: '알림 건수',
          data: [],
          backgroundColor: 'rgba(255,93,82,.6)',
          borderColor: C.alarm,
          borderWidth: 1,
          borderRadius: 4,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: baseScales(1),
        animation: { duration: 250 },
      },
    });
  }

  function pointColor(v) {
    return (currentThreshold != null && num(v) != null && v > currentThreshold) ? C.alarm : C.signal;
  }

  /* ── 차트/캔버스 표시 토글 ── */
  function showLineChart(show) {
    $('lineChart').classList.toggle('hidden', !show);
    $('lineEmpty').classList.toggle('hidden', show);
  }
  function showBarChart(show) {
    $('barChart').classList.toggle('hidden', !show);
    $('barEmpty').classList.toggle('hidden', show);
  }

  /* ── 장치 목록 로드 ── */
  async function loadDevices() {
    const sel = $('deviceSelect');
    const res = await Auth.apiFetch('/devices');
    if (!res) { sel.innerHTML = '<option value="">장치를 불러오지 못했습니다</option>'; return; }
    if (res.status === 403) { toast('장치 목록 접근 권한이 없습니다.', true); return; }
    if (!res.ok) { sel.innerHTML = '<option value="">장치를 불러오지 못했습니다</option>'; return; }

    let devices;
    try { devices = await res.json(); } catch { devices = []; }

    if (!Array.isArray(devices) || devices.length === 0) {
      sel.innerHTML = '<option value="">등록된 장치가 없습니다</option>';
      return;
    }

    const opts = ['<option value="">채널을 선택하세요</option>'];
    devices.forEach((d) => {
      const text = `${d.name} (${d.deviceType}) · ${d.location}`;
      opts.push(
        `<option value="${escapeHtml(String(d.id))}" data-threshold="${d.thresholdValue == null ? '' : escapeHtml(String(d.thresholdValue))}">${escapeHtml(text)}</option>`
      );
    });
    sel.innerHTML = opts.join('');
  }

  /* ── 대시보드 리셋(미선택 상태) ── */
  function resetDashboard() {
    currentThreshold = null;
    $('thrBadge').textContent = '임계값 —';
    $('refreshTag').textContent = '갱신 대기';
    ['roAlertCount', 'roWeekCount', 'roDataCount'].forEach((id) => { $(id).textContent = '—'; });
    ['roAlertLamp', 'roWeekLamp', 'roDataLamp'].forEach((id) => { $(id).className = 'lamp idle'; });

    lineChart.data.labels = [];
    lineChart.data.datasets[0].data = [];
    lineChart.data.datasets[0].pointBackgroundColor = [];
    lineChart.data.datasets[1].data = [];
    lineChart.update();
    barChart.data.labels = [];
    barChart.data.datasets[0].data = [];
    barChart.update();

    showLineChart(false);
    showBarChart(false);
    $('alertBody').innerHTML =
      '<tr id="alertEmptyRow"><td colspan="6"><div class="empty">채널을 선택하면 최근 알림이 표시됩니다.</div></td></tr>';
  }

  /* ── 장치 선택 ── */
  function onDeviceChange() {
    const sel = $('deviceSelect');
    currentDeviceId = sel.value;
    if (!currentDeviceId) { resetDashboard(); return; }

    const opt = sel.options[sel.selectedIndex];
    const thr = opt ? opt.dataset.threshold : '';
    currentThreshold = (thr === '' || thr == null) ? null : parseFloat(thr);
    $('thrBadge').textContent = currentThreshold == null ? '임계값 —' : `임계값 ${fmtNum(currentThreshold)}`;

    loadAll();
  }

  /* ── 전체 데이터 로드 ── */
  async function loadAll() {
    if (!currentDeviceId) return;
    await Promise.all([loadSensorData(), loadDailyCount(), loadRecentAlerts()]);
    $('refreshTag').textContent = '갱신 ' + new Date().toLocaleTimeString('ko-KR', { hour12: false });
  }

  /* ── 센서 데이터(라인 차트) ── */
  async function loadSensorData() {
    const res = await Auth.apiFetch(`/sensor-data/${encodeURIComponent(currentDeviceId)}`);
    if (!res || !res.ok) { if (res && res.status === 403) toast('센서 데이터 접근 권한이 없습니다.', true); return; }
    let data;
    try { data = await res.json(); } catch { return; }
    if (!Array.isArray(data)) return;

    $('roDataCount').textContent = data.length.toLocaleString('ko-KR');
    $('roDataLamp').className = data.length > 0 ? 'lamp ok' : 'lamp idle';

    const recent = data.slice(0, MAX_POINTS).reverse();
    const labels = recent.map((d) => fmtTime(d.recordedAt));
    const values = recent.map((d) => d.value);

    lineChart.data.labels = labels;
    lineChart.data.datasets[0].data = values;
    lineChart.data.datasets[0].pointBackgroundColor = values.map(pointColor);
    lineChart.data.datasets[1].data =
      currentThreshold == null ? [] : values.map(() => currentThreshold);
    lineChart.update();

    showLineChart(recent.length > 0);
  }

  /* ── 일별 알림(바 차트) ── */
  async function loadDailyCount() {
    const res = await Auth.apiFetch(`/alerts/daily-count?deviceId=${encodeURIComponent(currentDeviceId)}&days=7`);
    if (!res || !res.ok) { if (res && res.status === 403) toast('알림 통계 접근 권한이 없습니다.', true); return; }
    let data;
    try { data = await res.json(); } catch { return; }
    if (!Array.isArray(data)) return;

    const total = data.reduce((s, d) => s + (num(d.count) || 0), 0);
    $('roWeekCount').textContent = total.toLocaleString('ko-KR');
    $('roWeekLamp').className = total > 0 ? 'lamp warn' : 'lamp ok';

    barChart.data.labels = data.map((d) => (d.date ? String(d.date).substring(5) : ''));
    barChart.data.datasets[0].data = data.map((d) => num(d.count) || 0);
    barChart.update();

    showBarChart(data.length > 0);
  }

  /* ── 최근 알림(테이블) ── */
  async function loadRecentAlerts() {
    const res = await Auth.apiFetch(`/alerts/recent?deviceId=${encodeURIComponent(currentDeviceId)}&limit=20`);
    if (!res || !res.ok) { if (res && res.status === 403) toast('알림 접근 권한이 없습니다.', true); return; }
    let alerts;
    try { alerts = await res.json(); } catch { return; }
    if (!Array.isArray(alerts)) return;

    $('roAlertCount').textContent = alerts.length >= 20 ? '20+' : String(alerts.length);
    $('roAlertLamp').className = alerts.length > 0 ? 'lamp warn' : 'lamp ok';

    const tbody = $('alertBody');
    if (alerts.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6"><div class="empty">최근 알림이 없습니다.</div></td></tr>';
      return;
    }
    tbody.innerHTML = alerts.map(alertRowHtml).join('');
  }

  function isExceed(a) {
    const v = num(a.sensorValue), t = num(a.thresholdValue);
    return v != null && t != null && v > t;
  }

  function alertRowHtml(a) {
    const exceed = isExceed(a);
    const lampCls = exceed ? 'lamp alarm' : 'lamp warn';
    const evi = eviHtml(a);
    return `<tr class="${exceed ? 'row-alarm' : ''}">
      <td><span class="${lampCls}"></span></td>
      <td class="mono dim">${escapeHtml(String(a.deviceId ?? ''))}</td>
      <td class="num ${exceed ? 'cell-alarm' : ''}">${fmtNum(a.sensorValue)}</td>
      <td class="num dim">${fmtNum(a.thresholdValue)}</td>
      <td>${escapeHtml(a.message || '')}${evi}</td>
      <td class="mono faint" style="font-size:.8rem;">${escapeHtml(fmtDateTime(a.createdAt))}</td>
    </tr>`;
  }

  function eviHtml(a) {
    const parts = [];
    if (a.evidence) parts.push(`<span class="k">근거</span> ${escapeHtml(a.evidence)}`);
    if (a.recommendation) parts.push(`<span class="k">권고</span> ${escapeHtml(a.recommendation)}`);
    return parts.length ? `<span class="evi">${parts.join(' · ')}</span>` : '';
  }

  /* ── 실시간: 라이브 센서 점 추가 ── */
  function appendLivePoint(d) {
    const ds = lineChart.data.datasets[0];
    lineChart.data.labels.push(fmtTime(d.recordedAt));
    ds.data.push(d.value);
    if (!Array.isArray(ds.pointBackgroundColor)) ds.pointBackgroundColor = [];
    ds.pointBackgroundColor.push(pointColor(d.value));

    while (ds.data.length > MAX_POINTS) {
      lineChart.data.labels.shift();
      ds.data.shift();
      ds.pointBackgroundColor.shift();
    }
    lineChart.data.datasets[1].data =
      currentThreshold == null ? [] : ds.data.map(() => currentThreshold);

    showLineChart(true);
    lineChart.update();
  }

  /* ── 실시간: 알림 행 prepend ── */
  function prependAlertRow(a) {
    const tbody = $('alertBody');
    const placeholder = tbody.querySelector('td[colspan]');
    if (placeholder) tbody.innerHTML = '';
    tbody.insertAdjacentHTML('afterbegin', alertRowHtml(a));

    // 리드아웃 대략 갱신
    const cur = $('roAlertCount').textContent;
    if (cur !== '20+') {
      const n = parseInt(cur, 10);
      const next = isNaN(n) ? 1 : n + 1;
      $('roAlertCount').textContent = next >= 20 ? '20+' : String(next);
      $('roAlertLamp').className = 'lamp warn';
    }
  }

  /* ── SSE 스트림 ── */
  function initSse() {
    const token = Auth.getToken();
    if (!token || sse) return;
    sse = new EventSource('/dashboard/stream?token=' + encodeURIComponent(token));

    sse.addEventListener('connected', () => { setSseLamp('ok'); });

    sse.addEventListener('sensor-data', (e) => {
      let d;
      try { d = JSON.parse(e.data); } catch { return; }
      if (!currentDeviceId || String(d.deviceId) !== String(currentDeviceId)) return;
      appendLivePoint(d);
    });

    sse.addEventListener('alert', (e) => {
      let a;
      try { a = JSON.parse(e.data); } catch { return; }
      // 다른 장치 알림이 섞여 오면 현재 채널만 반영
      if (currentDeviceId && a.deviceId != null && String(a.deviceId) !== String(currentDeviceId)) return;
      prependAlertRow(a);
    });

    sse.onerror = () => {
      setSseLamp('alarm');
      console.warn('[SSE] 연결 오류 — 자동 재연결 대기');
      // EventSource 기본 재연결에 위임 (수동 재연결 안 함)
    };
  }

  /* ── 30초 폴링 폴백 ── */
  function startPolling() {
    if (pollTimer) return;
    pollTimer = setInterval(() => { if (currentDeviceId) loadAll(); }, 30000);
  }

  /* ── 진입 ── */
  function boot() {
    if (!Auth.requireLogin()) return;

    startClock();
    renderIdentity();

    if (!Auth.hasRole(...ALLOWED)) {
      $('noAccess').classList.remove('hidden');
      return;
    }

    $('dash').classList.remove('hidden');
    initCharts();
    $('deviceSelect').addEventListener('change', onDeviceChange);

    loadDevices();
    initSse();
    startPolling();
  }

  document.addEventListener('DOMContentLoaded', boot);
})();

/* ============================================================
   센서 대시보드 — 실시간 텔레메트리 모니터링
   - window.Auth(공유 인증) 사용, Chart.js 라인/바 차트
   - SSE 실시간 스트림 + 30초 폴링 폴백
   ============================================================ */
(function () {
  'use strict';

  const ALLOWED = ['SYSTEM_ADMIN', 'MEMBER', 'VIEWER'];

  /* 산업 계기판 팔레트 — app.css 토큰에서 읽어 라이트/다크에 함께 반응 */
  let C = { signal: '#2dd4bf', brand: '#f2a63b', alarm: '#ff5d52', grid: '#1f2630', tick: '#5c6775' };

  function hexA(hex, a) {
    const h = hex.replace('#', '').trim();
    const n = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
    const r = parseInt(n.slice(0, 2), 16), g = parseInt(n.slice(2, 4), 16), b = parseInt(n.slice(4, 6), 16);
    return `rgba(${r},${g},${b},${a})`;
  }
  function readPalette() {
    const cs = getComputedStyle(document.documentElement);
    const g = (name, fb) => (cs.getPropertyValue(name).trim() || fb);
    return {
      signal: g('--signal', '#2dd4bf'),
      brand:  g('--brand',  '#f2a63b'),
      alarm:  g('--alarm',  '#ff5d52'),
      grid:   g('--line-soft', '#1f2630'),
      tick:   g('--text-faint', '#5c6775'),
    };
  }

  const MAX_POINTS = 50;

  /* ── 상태 ── */
  let lineChart = null;
  let barChart = null;
  let currentChannelId = '';
  let currentDeviceId = null;
  let currentThreshold = null;
  let currentThresholdDirection = 'ABOVE';
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
            label: '센서값 · 붉은 점=임계 초과(순간값)',
            data: [],
            borderColor: C.signal,
            backgroundColor: hexA(C.signal, .08),
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
          backgroundColor: hexA(C.alarm, .6),
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

  /* 테마 전환 시 차트 색을 토큰 기준으로 다시 칠한다 (캔버스는 CSS를 못 읽어 수동 갱신) */
  function applyPalette() {
    if (!lineChart || !barChart) return;
    C = readPalette();
    const l0 = lineChart.data.datasets[0], l1 = lineChart.data.datasets[1];
    l0.borderColor = C.signal;
    l0.backgroundColor = hexA(C.signal, .08);
    l0.pointBackgroundColor = l0.data.map(pointColor);
    l1.borderColor = C.brand;
    barChart.data.datasets[0].borderColor = C.alarm;
    barChart.data.datasets[0].backgroundColor = hexA(C.alarm, .6);
    [lineChart, barChart].forEach((ch) => {
      ch.options.scales.x.ticks.color = C.tick; ch.options.scales.x.grid.color = C.grid;
      ch.options.scales.y.ticks.color = C.tick; ch.options.scales.y.grid.color = C.grid;
    });
    lineChart.options.plugins.legend.labels.color = C.tick;
    lineChart.update(); barChart.update();
  }
  window.addEventListener('sm-theme-change', applyPalette);

  // 표시용 순간값 비교다. 서버 알람은 쿨다운·해제 밴드를 적용하므로 이 결과와 다를 수 있다.
  function exceedsThreshold(value, threshold, direction) {
    const v = num(value), t = num(threshold);
    if (v == null || t == null) return false;
    return direction === 'BELOW' ? v < t : v > t;
  }
  function pointColor(v) {
    return exceedsThreshold(v, currentThreshold, currentThresholdDirection) ? C.alarm : C.signal;
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

  /* ── 채널 목록 로드(대시보드 드롭다운 소스) ── */
  async function loadChannels() {
    const sel = $('deviceSelect');
    const res = await Auth.apiFetch('/channels');
    if (!res) { sel.innerHTML = '<option value="">채널을 불러오지 못했습니다</option>'; return; }
    if (res.status === 403) { toast('채널 목록 접근 권한이 없습니다.', true); return; }
    if (!res.ok) { sel.innerHTML = '<option value="">채널을 불러오지 못했습니다</option>'; return; }

    let channels;
    try { channels = await res.json(); } catch { channels = []; }

    if (!Array.isArray(channels) || channels.length === 0) {
      sel.innerHTML = '<option value="">등록된 채널이 없습니다</option>';
      return;
    }

    const opts = ['<option value="">채널을 선택하세요</option>'];
    channels.forEach((c) => {
      const text = `${c.deviceName} · ${c.code}${c.unit ? ` (${c.unit})` : ''}`;
      opts.push(
        `<option value="${escapeHtml(String(c.id))}"` +
        ` data-device-id="${escapeHtml(String(c.deviceId))}"` +
        ` data-threshold="${c.thresholdValue == null ? '' : escapeHtml(String(c.thresholdValue))}"` +
        ` data-direction="${escapeHtml(c.thresholdDirection || 'ABOVE')}"` +
        `>${escapeHtml(text)}</option>`
      );
    });
    sel.innerHTML = opts.join('');

    // 첫 채널 자동 선택 — 진입 즉시 데이터가 보이게 (빈 화면 방지)
    if (sel.options.length > 1) {
      sel.selectedIndex = 1;
      onChannelChange();
    }
  }

  /* ── 대시보드 리셋(미선택 상태) ── */
  function resetDashboard() {
    currentDeviceId = null;
    currentThreshold = null;
    currentThresholdDirection = 'ABOVE';
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

  /* ── 채널 선택 ── */
  function onChannelChange() {
    const sel = $('deviceSelect');
    currentChannelId = sel.value;
    if (!currentChannelId) { resetDashboard(); return; }

    const opt = sel.options[sel.selectedIndex];
    currentDeviceId = opt ? (opt.dataset.deviceId || null) : null;
    const thr = opt ? opt.dataset.threshold : '';
    currentThreshold = (thr === '' || thr == null) ? null : parseFloat(thr);
    currentThresholdDirection = (opt && opt.dataset.direction) || 'ABOVE';
    const dirLabel = currentThresholdDirection === 'BELOW' ? '미만' : '초과';
    $('thrBadge').textContent = currentThreshold == null ? '임계값 —' : `임계값 ${fmtNum(currentThreshold)} (${dirLabel})`;

    loadAll();
  }

  /* ── 전체 데이터 로드 ── */
  async function loadAll() {
    if (!currentChannelId) return;
    await Promise.all([loadReadings(), loadDailyCount(), loadRecentAlerts()]);
    $('refreshTag').textContent = '갱신 ' + new Date().toLocaleTimeString('ko-KR', { hour12: false });
  }

  /* ── 센서 데이터(라인 차트) ── */
  async function loadReadings() {
    const res = await Auth.apiFetch(`/channels/${encodeURIComponent(currentChannelId)}/readings?limit=500`);
    if (!res || !res.ok) { if (res && res.status === 403) toast('센서 데이터 접근 권한이 없습니다.', true); return; }
    let data;
    try { data = await res.json(); } catch { return; }
    if (!Array.isArray(data)) return;

    $('roDataCount').textContent = data.length.toLocaleString('ko-KR');
    $('roDataLamp').className = data.length > 0 ? 'lamp ok' : 'lamp idle';

    // observed_at desc로 오므로 오래된 순으로 뒤집어 그린다.
    const recent = data.slice(0, MAX_POINTS).reverse();
    const labels = recent.map((d) => fmtTime(d.observedAt));
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
    const res = await Auth.apiFetch(`/alerts/daily-count?channelId=${encodeURIComponent(currentChannelId)}&days=7`);
    if (!res || !res.ok) { if (res && res.status === 403) toast('알림 통계 접근 권한이 없습니다.', true); return; }
    let data;
    try { data = await res.json(); } catch { return; }
    if (!Array.isArray(data)) return;

    const total = data.reduce((s, d) => s + (num(d.count) || 0), 0);
    $('roWeekCount').textContent = total.toLocaleString('ko-KR');
    $('roWeekLamp').className = total > 0 ? 'lamp warn' : 'lamp ok';

    // 오늘(마지막 날) 알림 수 — 리드아웃 상단 카드
    const today = data.length ? (num(data[data.length - 1].count) || 0) : 0;
    $('roAlertCount').textContent = today.toLocaleString('ko-KR');
    $('roAlertLamp').className = today > 0 ? 'lamp warn' : 'lamp ok';

    barChart.data.labels = data.map((d) => (d.date ? String(d.date).substring(5) : ''));
    barChart.data.datasets[0].data = data.map((d) => num(d.count) || 0);
    barChart.update();

    showBarChart(data.length > 0);
  }

  /* ── 최근 알림(테이블) ── */
  async function loadRecentAlerts() {
    const res = await Auth.apiFetch(`/alerts/recent?channelId=${encodeURIComponent(currentChannelId)}&limit=20`);
    if (!res || !res.ok) { if (res && res.status === 403) toast('알림 접근 권한이 없습니다.', true); return; }
    let alerts;
    try { alerts = await res.json(); } catch { return; }
    if (!Array.isArray(alerts)) return;

    const tbody = $('alertBody');
    if (alerts.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6"><div class="empty">최근 알림이 없습니다.</div></td></tr>';
      return;
    }
    tbody.innerHTML = alerts.map(alertRowHtml).join('');
  }

  function isExceed(a) {
    return exceedsThreshold(a.sensorValue, a.thresholdValue, currentThresholdDirection);
  }

  function alertRowHtml(a) {
    const exceed = isExceed(a);
    // 램프/행 강조는 severity 우선(freshness 알림은 sensorValue가 없어 임계 비교로는 약하게 보임).
    const critical = a.severity === 'CRITICAL';
    const lampCls = critical ? 'lamp alarm' : (a.severity === 'WARNING' ? 'lamp warn' : 'lamp ok');
    const rowCls = (critical || exceed) ? 'row-alarm' : '';
    const evi = eviHtml(a);
    return `<tr class="${rowCls}">
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
  function appendLivePoint(reading) {
    const ds = lineChart.data.datasets[0];
    lineChart.data.labels.push(fmtTime(reading.observedAt));
    ds.data.push(reading.value);
    if (!Array.isArray(ds.pointBackgroundColor)) ds.pointBackgroundColor = [];
    ds.pointBackgroundColor.push(pointColor(reading.value));

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

    // 오늘 알림 카운트 +1 (라이브)
    const n = parseInt($('roAlertCount').textContent.replace(/[^\d]/g, ''), 10);
    $('roAlertCount').textContent = (isNaN(n) ? 1 : n + 1).toLocaleString('ko-KR');
    $('roAlertLamp').className = 'lamp warn';
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
      if (!currentChannelId || !Array.isArray(d.readings)) return;
      const reading = d.readings.find((r) => String(r.channelId) === String(currentChannelId));
      if (!reading) return;
      appendLivePoint({ value: reading.value, observedAt: d.observedAt });
    });

    sse.addEventListener('alert', (e) => {
      let a;
      try { a = JSON.parse(e.data); } catch { return; }
      if (!currentChannelId) return;
      // 임계 alert 는 channelId 로, freshness alert(channelId=null) 는 현재 채널의 장치로 매칭.
      const matchesChannel = a.channelId != null && String(a.channelId) === String(currentChannelId);
      const matchesDeviceFreshness =
        a.channelId == null && currentDeviceId != null && String(a.deviceId) === String(currentDeviceId);
      if (!matchesChannel && !matchesDeviceFreshness) return;
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
    pollTimer = setInterval(() => { if (currentChannelId) loadAll(); }, 30000);
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
    C = readPalette();
    initCharts();
    $('deviceSelect').addEventListener('change', onChannelChange);

    loadChannels();
    initSse();
    startPolling();
  }

  document.addEventListener('DOMContentLoaded', boot);
})();

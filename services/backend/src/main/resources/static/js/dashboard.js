/* 장치 중심 대시보드: overview/detail + scoped SSE + 30초 resync */
(function () {
  'use strict';

  const ALLOWED = ['SYSTEM_ADMIN', 'MEMBER', 'VIEWER'];
  const MAX_POINTS = 50;
  const FRESHNESS = {
    NOT_MONITORED: { label: '미감시', lamp: 'idle' },
    NEVER_SEEN: { label: '수신 없음', lamp: 'warn' },
    ONLINE: { label: '온라인', lamp: 'ok' },
    STALE: { label: '수신 지연', lamp: 'alarm' },
  };

  let C = { signal: '#2dd4bf', brand: '#f2a63b', alarm: '#ff5d52', grid: '#1f2630', tick: '#5c6775' };
  let overview = null;
  let deviceIndex = new Map();
  let currentDeviceId = null;
  let currentChannelId = null;
  let lineChart = null;
  let pointAnomalies = [];
  let pollTimer = null;
  let clockTimer = null;
  let sse = null;

  const $ = (id) => document.getElementById(id);

  function escapeHtml(value) {
    if (value == null) return '';
    return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function number(value) {
    return typeof value === 'number' && isFinite(value) ? value : null;
  }

  function fmtNum(value) {
    const n = number(value);
    return n == null ? '—' : new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(n);
  }

  function date(value) {
    if (!value) return null;
    const parsed = new Date(value);
    return isNaN(parsed) ? null : parsed;
  }

  function fmtTime(value) {
    const d = date(value);
    return d ? d.toLocaleTimeString('ko-KR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';
  }

  function fmtDateTime(value) {
    const d = date(value);
    return d ? d.toLocaleString('ko-KR', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—';
  }

  function fmtRelative(value) {
    const d = date(value);
    if (!d) return '수신 없음';
    const seconds = Math.max(0, Math.floor((Date.now() - d.getTime()) / 1000));
    if (seconds < 60) return `${seconds}초 전`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}분 전`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}시간 전`;
    return fmtDateTime(value);
  }

  let toastTimer = null;
  function toast(message, isError) {
    const el = $('toast');
    el.textContent = message;
    el.className = 'toast show' + (isError ? ' err' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.className = 'toast' + (isError ? ' err' : ''); }, 3200);
  }

  function readPalette() {
    const cs = getComputedStyle(document.documentElement);
    const token = (name, fallback) => cs.getPropertyValue(name).trim() || fallback;
    return {
      signal: token('--signal', '#2dd4bf'), brand: token('--brand', '#f2a63b'),
      alarm: token('--alarm', '#ff5d52'), grid: token('--line-soft', '#1f2630'),
      tick: token('--text-faint', '#5c6775'),
    };
  }

  function hexA(hex, alpha) {
    const raw = hex.replace('#', '').trim();
    const h = raw.length === 3 ? raw.split('').map((c) => c + c).join('') : raw;
    return `rgba(${parseInt(h.slice(0, 2), 16)},${parseInt(h.slice(2, 4), 16)},${parseInt(h.slice(4, 6), 16)},${alpha})`;
  }

  function startClock() {
    const tick = () => { $('clock').textContent = new Date().toLocaleTimeString('ko-KR', { hour12: false }); };
    tick();
    if (!clockTimer) clockTimer = setInterval(tick, 1000);
  }

  function renderIdentity() {
    const role = Auth.getRole();
    const label = Auth.ROLE_LABEL[role] || { text: role || '—', cls: '' };
    $('navEmployee').textContent = Auth.getEmployeeId() || '';
    $('navRole').textContent = label.text;
    $('navRole').className = 'tag ' + label.cls;
  }

  function setSseLamp(state) {
    $('sseLamp').className = `lamp ${state === 'ok' ? 'ok' : state === 'alarm' ? 'alarm' : 'idle'}`;
    $('sseLabel').textContent = state === 'ok' ? 'STREAM · 연결됨' : state === 'alarm' ? 'STREAM · 재연결 중' : 'STREAM · 대기';
  }

  function pointColor(anomaly) {
    return anomaly === true ? C.alarm : C.signal;
  }

  function initChart() {
    lineChart = new Chart($('lineChart').getContext('2d'), {
      type: 'line',
      data: { labels: [], datasets: [
        {
          label: '센서값 · 붉은 점=순간 이상', data: [], borderColor: C.signal,
          backgroundColor: hexA(C.signal, .08), borderWidth: 2, pointRadius: 3,
          pointHoverRadius: 5, pointBackgroundColor: [], tension: .25, fill: true,
        },
        { label: '임계값', data: [], borderColor: C.brand, borderWidth: 1.5, borderDash: [6, 4], pointRadius: 0 },
      ] },
      options: {
        responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { labels: { color: C.tick, boxWidth: 12, font: { family: 'IBM Plex Sans' } } } },
        scales: {
          x: { ticks: { color: C.tick, maxTicksLimit: 8, font: { family: 'IBM Plex Mono' } }, grid: { color: C.grid } },
          y: { ticks: { color: C.tick, font: { family: 'IBM Plex Mono' } }, grid: { color: C.grid } },
        },
        animation: { duration: 250 },
      },
    });
  }

  function applyPalette() {
    if (!lineChart) return;
    C = readPalette();
    lineChart.data.datasets[0].borderColor = C.signal;
    lineChart.data.datasets[0].backgroundColor = hexA(C.signal, .08);
    lineChart.data.datasets[0].pointBackgroundColor = pointAnomalies.map(pointColor);
    lineChart.data.datasets[1].borderColor = C.brand;
    ['x', 'y'].forEach((axis) => {
      lineChart.options.scales[axis].ticks.color = C.tick;
      lineChart.options.scales[axis].grid.color = C.grid;
    });
    lineChart.options.plugins.legend.labels.color = C.tick;
    lineChart.update();
  }
  window.addEventListener('sm-theme-change', applyPalette);

  function allDevices() {
    if (!overview || !Array.isArray(overview.factories)) return [];
    return overview.factories.flatMap((factory) => (factory.zones || [])
      .flatMap((zone) => (zone.devices || []).map((device) => ({ factory, zone, device }))));
  }

  function rebuildDeviceIndex() {
    deviceIndex = new Map(allDevices().map((entry) => [String(entry.device.id), entry]));
  }

  function freshnessInfo(value) {
    return FRESHNESS[value] || FRESHNESS.NOT_MONITORED;
  }

  function freshnessHtml(value) {
    const info = freshnessInfo(value);
    return `<span class="freshness"><span class="lamp ${info.lamp}"></span>${escapeHtml(info.label)}</span>`;
  }

  function renderSummary() {
    const devices = allDevices().map((entry) => entry.device);
    const online = devices.filter((device) => device.freshness === 'ONLINE').length;
    const alarmCount = devices.reduce((sum, device) => sum + (Number(device.currentAlarmCount) || 0), 0);
    const latest = devices.map((device) => date(device.lastSeenAt)).filter(Boolean)
      .sort((a, b) => b.getTime() - a.getTime())[0];
    const hasStale = devices.some((device) => device.freshness === 'STALE' || device.freshness === 'NEVER_SEEN');

    $('summaryFreshness').textContent = `${online} / ${devices.length}`;
    $('summaryFreshnessLamp').className = `lamp ${hasStale ? 'warn' : devices.length ? 'ok' : 'idle'}`;
    $('summaryLastSeen').textContent = latest ? fmtRelative(latest.toISOString()) : '수신 없음';
    $('summaryLastSeenLamp').className = `lamp ${latest ? 'ok' : 'idle'}`;
    $('summaryAlarm').textContent = String(alarmCount);
    $('summaryAlarmLamp').className = `lamp ${alarmCount ? 'alarm' : devices.length ? 'ok' : 'idle'}`;
  }

  function deviceCardHtml(device) {
    const location = device.location || device.code || '위치 정보 없음';
    return `<button class="device-card" type="button" data-device-id="${escapeHtml(String(device.id))}">
      <div class="device-head"><div><h3>${escapeHtml(device.name || device.code || '이름 없는 장치')}</h3><div class="device-meta">${escapeHtml(location)}</div></div>${freshnessHtml(device.freshness)}</div>
      <div class="device-metrics">
        <div class="metric"><span>마지막 수신</span><b>${escapeHtml(fmtRelative(device.lastSeenAt))}</b></div>
        <div class="metric"><span>현재 알람</span><b>${Number(device.currentAlarmCount) || 0} 채널</b></div>
      </div>
    </button>`;
  }

  function renderOverview() {
    renderSummary();
    const groups = $('factoryGroups');
    if (!overview || !overview.factories || overview.factories.length === 0) {
      groups.innerHTML = '';
      $('overviewEmpty').classList.remove('hidden');
      $('overviewEmpty').textContent = '접근 가능한 장치가 없습니다.';
      return;
    }
    $('overviewEmpty').classList.add('hidden');
    groups.innerHTML = overview.factories.map((factory) => `<section class="factory-block">
      <div class="factory-name eyebrow">${escapeHtml(factory.name)}</div>
      ${(factory.zones || []).map((zone) => `<div class="zone-block">
        <div class="section-title"><h2>${escapeHtml(zone.name)}</h2><span class="tag">${(zone.devices || []).length} 장치</span></div>
        <div class="device-grid">${(zone.devices || []).map(deviceCardHtml).join('')}</div>
      </div>`).join('')}
    </section>`).join('');
  }

  function currentEntry() {
    return currentDeviceId == null ? null : deviceIndex.get(String(currentDeviceId));
  }

  function channelName(device, channelId) {
    const channel = (device.channels || []).find((item) => String(item.id) === String(channelId));
    return channel ? channel.code : '장치 freshness';
  }

  function thresholdText(channel) {
    if (number(channel.thresholdValue) == null) return '임계값 없음';
    return `${channel.thresholdDirection === 'BELOW' ? '미만' : '초과'} ${fmtNum(channel.thresholdValue)}${channel.unit ? ` ${channel.unit}` : ''}`;
  }

  function channelCardHtml(channel) {
    const selected = String(channel.id) === String(currentChannelId) ? ' selected' : '';
    const alarm = channel.inAlarm ? ' alarm' : '';
    const state = channel.inAlarm ? '<span class="freshness"><span class="lamp alarm"></span>알람</span>'
      : channel.anomaly ? '<span class="freshness"><span class="lamp warn"></span>순간 이상</span>'
        : '<span class="freshness"><span class="lamp ok"></span>정상</span>';
    return `<button class="channel-card${selected}${alarm}" type="button" data-channel-id="${escapeHtml(String(channel.id))}">
      <div class="channel-head"><div><strong>${escapeHtml(channel.code)}</strong><div class="device-meta">${escapeHtml(channel.quantityKind || '측정 채널')}</div></div>${state}</div>
      <div class="latest">${fmtNum(channel.latestValue)} <small>${escapeHtml(channel.unit || '')}</small></div>
      <div class="channel-foot"><span>${escapeHtml(fmtRelative(channel.latestReceivedAt))}</span><span>${escapeHtml(thresholdText(channel))}</span></div>
    </button>`;
  }

  function renderDetail() {
    const entry = currentEntry();
    if (!entry) return;
    const { factory, zone, device } = entry;
    const info = freshnessInfo(device.freshness);
    $('detailName').textContent = device.name || device.code || '이름 없는 장치';
    $('detailBreadcrumb').textContent = [factory.name, zone.name, device.location || device.code].filter(Boolean).join(' · ');
    $('detailFreshness').textContent = info.label;
    $('detailFreshness').className = device.freshness === 'STALE' ? 'tag tag-alarm' : device.freshness === 'ONLINE' ? 'tag tag-signal' : 'tag';
    $('detailFreshnessValue').textContent = info.label;
    $('detailFreshnessLamp').className = `lamp ${info.lamp}`;
    $('detailLastSeen').textContent = fmtRelative(device.lastSeenAt);
    $('detailLastSeenLamp').className = `lamp ${device.lastSeenAt ? 'ok' : 'idle'}`;
    $('detailAlarmCount').textContent = String(Number(device.currentAlarmCount) || 0);
    $('detailAlarmLamp').className = `lamp ${device.currentAlarmCount ? 'alarm' : 'ok'}`;
    $('channelCount').textContent = `${(device.channels || []).length} 채널`;
    $('channelGrid').innerHTML = (device.channels || []).length
      ? device.channels.map(channelCardHtml).join('')
      : '<div class="panel empty">등록된 채널이 없습니다.</div>';

    const select = $('channelSelect');
    select.innerHTML = (device.channels || []).map((channel) =>
      `<option value="${escapeHtml(String(channel.id))}"${String(channel.id) === String(currentChannelId) ? ' selected' : ''}>${escapeHtml(channel.code)}${channel.unit ? ` (${escapeHtml(channel.unit)})` : ''}</option>`).join('');
    const selectedChannel = (device.channels || []).find((channel) => String(channel.id) === String(currentChannelId));
    $('thrBadge').textContent = selectedChannel ? thresholdText(selectedChannel) : '임계값 —';
  }

  function showOverview() {
    currentDeviceId = null;
    currentChannelId = null;
    $('overviewView').classList.remove('hidden');
    $('detailView').classList.add('hidden');
  }

  async function openDevice(deviceId) {
    const entry = deviceIndex.get(String(deviceId));
    if (!entry) return;
    currentDeviceId = String(deviceId);
    if (!(entry.device.channels || []).some((channel) => String(channel.id) === String(currentChannelId))) {
      currentChannelId = entry.device.channels && entry.device.channels.length ? String(entry.device.channels[0].id) : null;
    }
    $('overviewView').classList.add('hidden');
    $('detailView').classList.remove('hidden');
    renderDetail();
    await Promise.all([loadReadings(), loadDeviceAlerts()]);
  }

  async function loadOverview() {
    const res = await Auth.apiFetch('/dashboard/overview');
    if (!res || !res.ok) {
      if (res && res.status === 403) toast('장치 현황 접근 권한이 없습니다.', true);
      else toast('장치 현황을 불러오지 못했습니다.', true);
      return false;
    }
    try { overview = await res.json(); } catch { return false; }
    rebuildDeviceIndex();
    renderOverview();
    $('refreshTag').textContent = '기준 ' + fmtTime(overview.generatedAt);
    if (currentDeviceId != null) {
      if (currentEntry()) renderDetail();
      else showOverview();
    }
    return true;
  }

  function showLineChart(show) {
    $('lineChart').classList.toggle('hidden', !show);
    $('lineEmpty').classList.toggle('hidden', show);
  }

  function resetChart() {
    pointAnomalies = [];
    lineChart.data.labels = [];
    lineChart.data.datasets[0].data = [];
    lineChart.data.datasets[0].pointBackgroundColor = [];
    lineChart.data.datasets[1].data = [];
    lineChart.update();
    showLineChart(false);
  }

  async function loadReadings() {
    if (!currentChannelId) { resetChart(); return; }
    const requestedChannel = String(currentChannelId);
    const res = await Auth.apiFetch(`/channels/${encodeURIComponent(requestedChannel)}/readings?limit=500`);
    if (!res || !res.ok) return;
    let readings;
    try { readings = await res.json(); } catch { return; }
    if (requestedChannel !== String(currentChannelId) || !Array.isArray(readings)) return;
    const entry = currentEntry();
    const channel = entry && (entry.device.channels || []).find((item) => String(item.id) === requestedChannel);
    const recent = readings.slice(0, MAX_POINTS).reverse();
    pointAnomalies = recent.map((reading) => reading.anomaly === true);
    lineChart.data.labels = recent.map((reading) => fmtTime(reading.observedAt));
    lineChart.data.datasets[0].data = recent.map((reading) => reading.value);
    lineChart.data.datasets[0].pointBackgroundColor = pointAnomalies.map(pointColor);
    lineChart.data.datasets[1].data = channel && number(channel.thresholdValue) != null
      ? recent.map(() => channel.thresholdValue) : [];
    lineChart.update();
    showLineChart(recent.length > 0);
  }

  function evidenceHtml(alert) {
    const parts = [];
    if (alert.evidence) parts.push(`근거 ${escapeHtml(alert.evidence)}`);
    if (alert.recommendation) parts.push(`권고 ${escapeHtml(alert.recommendation)}`);
    return parts.length ? `<span class="evi">${parts.join(' · ')}</span>` : '';
  }

  async function loadDeviceAlerts() {
    const entry = currentEntry();
    if (!entry) return;
    const requestedDevice = String(entry.device.id);
    const res = await Auth.apiFetch(`/alerts?deviceId=${encodeURIComponent(requestedDevice)}&size=20&sort=createdAt,desc`);
    if (!res || !res.ok) return;
    let page;
    try { page = await res.json(); } catch { return; }
    if (!currentEntry() || requestedDevice !== String(currentEntry().device.id)) return;
    const alerts = Array.isArray(page.content) ? page.content : [];
    $('alarmList').innerHTML = alerts.length ? alerts.map((alert) => {
      const channel = channelName(entry.device, alert.channelId);
      const lamp = alert.severity === 'CRITICAL' ? 'alarm' : alert.severity === 'WARNING' ? 'warn' : 'ok';
      return `<li><div class="alarm-line"><span class="freshness"><span class="lamp ${lamp}"></span><strong>${escapeHtml(channel)}</strong></span><span class="alarm-time">${escapeHtml(fmtDateTime(alert.createdAt))}</span></div><div class="alarm-message">${escapeHtml(alert.message || '')}${evidenceHtml(alert)}</div></li>`;
    }).join('') : '<li class="empty">최근 알림이 없습니다.</li>';
  }

  function appendLivePoint(value, anomaly, observedAt) {
    const dataset = lineChart.data.datasets[0];
    lineChart.data.labels.push(fmtTime(observedAt));
    dataset.data.push(value);
    pointAnomalies.push(anomaly === true);
    if (!Array.isArray(dataset.pointBackgroundColor)) dataset.pointBackgroundColor = [];
    dataset.pointBackgroundColor.push(pointColor(anomaly));
    while (dataset.data.length > MAX_POINTS) {
      lineChart.data.labels.shift(); dataset.data.shift(); pointAnomalies.shift(); dataset.pointBackgroundColor.shift();
    }
    const entry = currentEntry();
    const channel = entry && (entry.device.channels || []).find((item) => String(item.id) === String(currentChannelId));
    lineChart.data.datasets[1].data = channel && number(channel.thresholdValue) != null
      ? dataset.data.map(() => channel.thresholdValue) : [];
    lineChart.update();
    showLineChart(true);
  }

  function applySensorBatch(batch) {
    if (!batch || !Array.isArray(batch.readings)) return;
    const entry = deviceIndex.get(String(batch.deviceId));
    if (!entry) return;
    const byChannel = new Map((entry.device.channels || []).map((channel) => [String(channel.id), channel]));
    let selectedReading = null;
    batch.readings.forEach((reading) => {
      const channel = byChannel.get(String(reading.channelId));
      if (!channel) return;
      channel.latestValue = reading.value;
      channel.latestObservedAt = batch.observedAt;
      channel.latestReceivedAt = batch.receivedAt;
      channel.anomaly = reading.anomaly === true;
      if (String(reading.channelId) === String(currentChannelId)) selectedReading = reading;
    });
    entry.device.lastSeenAt = batch.receivedAt;
    entry.device.freshness = entry.device.expectedIntervalSeconds == null || entry.device.expectedIntervalSeconds <= 0
      ? 'NOT_MONITORED' : 'ONLINE';

    // 한 batch의 모든 channel 상태를 반영한 뒤 화면을 한 번만 그린다.
    renderOverview();
    if (String(currentDeviceId) === String(batch.deviceId)) {
      renderDetail();
      if (selectedReading) appendLivePoint(selectedReading.value, selectedReading.anomaly, batch.observedAt);
    }
  }

  function initSse() {
    const token = Auth.getToken();
    if (!token || sse) return;
    sse = new EventSource('/dashboard/stream?token=' + encodeURIComponent(token));
    sse.addEventListener('connected', () => setSseLamp('ok'));
    sse.addEventListener('sensor-data', (event) => {
      try { applySensorBatch(JSON.parse(event.data)); } catch {}
    });
    sse.addEventListener('alert', async (event) => {
      let alert;
      try { alert = JSON.parse(event.data); } catch { return; }
      if (!deviceIndex.has(String(alert.deviceId))) return;
      await loadOverview();
      if (String(currentDeviceId) === String(alert.deviceId)) await loadDeviceAlerts();
    });
    sse.onerror = () => { setSseLamp('alarm'); console.warn('[SSE] 연결 오류 — 자동 재연결 대기'); };
  }

  async function resync() {
    const wasDetail = currentDeviceId != null;
    await loadOverview();
    if (wasDetail && currentEntry()) await Promise.all([loadReadings(), loadDeviceAlerts()]);
  }

  function startPolling() {
    if (!pollTimer) pollTimer = setInterval(resync, 30000);
  }

  function bindEvents() {
    $('factoryGroups').addEventListener('click', (event) => {
      const card = event.target.closest('[data-device-id]');
      if (card) openDevice(card.dataset.deviceId);
    });
    $('backButton').addEventListener('click', showOverview);
    $('channelGrid').addEventListener('click', async (event) => {
      const card = event.target.closest('[data-channel-id]');
      if (!card) return;
      currentChannelId = card.dataset.channelId;
      renderDetail();
      await loadReadings();
    });
    $('channelSelect').addEventListener('change', async (event) => {
      currentChannelId = event.target.value || null;
      renderDetail();
      await loadReadings();
    });
  }

  async function boot() {
    if (!Auth.requireLogin()) return;
    startClock();
    renderIdentity();
    if (!Auth.hasRole(...ALLOWED)) {
      $('noAccess').classList.remove('hidden');
      return;
    }
    $('dash').classList.remove('hidden');
    C = readPalette();
    initChart();
    bindEvents();
    await loadOverview();
    initSse();
    startPolling();
  }

  document.addEventListener('DOMContentLoaded', boot);
})();

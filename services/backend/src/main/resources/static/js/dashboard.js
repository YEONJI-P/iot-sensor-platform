/* 장치 중심 대시보드: overview/detail + scoped SSE + 30초 resync */
(function () {
  'use strict';

  const ALLOWED = ['SYSTEM_ADMIN', 'FACTORY_ADMIN', 'MEMBER', 'VIEWER'];
  const RAW_POINT_LIMIT = 500;
  const CHART_POINT_LIMIT = 300;
  const DEFAULT_WINDOW_MINUTES = 5;
  const MAX_FUTURE_SKEW_MS = 60 * 1000;
  const FRESHNESS = {
    NOT_MONITORED: { label: '미감시', lamp: 'idle' },
    PLANNED_OFFLINE: { label: '계획 비가동', lamp: 'idle' },
    RESUMING: { label: '재개 대기', lamp: 'idle' },
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
  let rawReadings = [];
  let channelAlerts = [];
  let windowMinutes = DEFAULT_WINDOW_MINUTES;
  let pointAnomalies = [];
  let readingsRequestId = 0;
  let channelAlertsRequestId = 0;
  let deviceAlertsRequestId = 0;
  let deviceAlerts = [];
  let showAllDeviceAlerts = false;
  let expandedDeviceAlertId = null;
  let pollTimer = null;
  let clockTimer = null;
  let sse = null;
  let sseReconnectTimer = null;
  let sseRecovering = false;

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

  function normalizeReadings(readings) {
    if (!Array.isArray(readings)) return [];
    return readings.map((reading) => {
      const observed = date(reading && reading.observedAt);
      const value = number(reading && reading.value);
      if (!observed || observed.getTime() > Date.now() + MAX_FUTURE_SKEW_MS || value == null) return null;
      return {
        batchId: reading.batchId,
        x: observed.getTime(),
        y: value,
        anomaly: reading.anomaly === true,
      };
    }).filter(Boolean).sort((a, b) => a.x - b.x).slice(-RAW_POINT_LIMIT);
  }

  function filterTimeWindow(points, minutes, nowMillis) {
    const duration = Number(minutes) * 60 * 1000;
    const cutoff = Number(nowMillis) - duration;
    if (!Array.isArray(points) || !Number.isFinite(duration) || duration <= 0 || !Number.isFinite(cutoff)) return [];
    return points.filter((point) => point && Number.isFinite(point.x) && point.x >= cutoff)
      .sort((a, b) => a.x - b.x);
  }

  function downsampleEvenly(points, limit) {
    if (!Array.isArray(points)) return [];
    const max = Math.floor(Number(limit));
    if (!Number.isFinite(max) || max <= 0) return [];
    if (points.length <= max) return points.slice();
    if (max === 1) return [points[points.length - 1]];
    return Array.from({ length: max }, (_, index) =>
      points[Math.round(index * (points.length - 1) / (max - 1))]);
  }

  function mergeReadings(existing, incoming) {
    const merged = new Map();
    [...(Array.isArray(existing) ? existing : []), ...(Array.isArray(incoming) ? incoming : [])]
      .forEach((point) => {
        if (!point || !Number.isFinite(point.x)) return;
        const key = point.batchId == null ? `time:${point.x}` : `batch:${point.batchId}`;
        merged.set(key, point);
      });
    return [...merged.values()].sort((a, b) => a.x - b.x).slice(-RAW_POINT_LIMIT);
  }

  function matchAlertMarkers(points, alerts, channelId) {
    if (!Array.isArray(points) || !Array.isArray(alerts)) return [];
    const readingByBatch = new Map(points.filter((point) => point && point.batchId != null)
      .map((point) => [String(point.batchId), point]));
    return alerts.filter((alert) => alert && alert.batchId != null && alert.channelId != null
      && String(alert.channelId) === String(channelId) && readingByBatch.has(String(alert.batchId)))
      .map((alert) => {
        const reading = readingByBatch.get(String(alert.batchId));
        return { x: reading.x, y: reading.y, alert };
      }).sort((a, b) => a.x - b.x);
  }

  function fmtTime(value) {
    const d = date(value);
    return d ? d.toLocaleTimeString('ko-KR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';
  }

  function fmtDateTime(value) {
    const d = date(value);
    return d ? d.toLocaleString('ko-KR', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—';
  }

  function tooltipLabel(context) {
    const marker = context.raw && context.raw.alert;
    if (marker) {
      return [
        `심각도: ${marker.severity || '—'}`,
        `메시지: ${marker.message || '—'}`,
        `생성: ${fmtDateTime(marker.createdAt)}`,
      ];
    }
    return `${context.dataset.label}: ${fmtNum(context.parsed.y)}`;
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
      data: { datasets: [
        {
          label: '센서값 · 붉은 점=순간 이상', data: [], borderColor: C.signal,
          backgroundColor: hexA(C.signal, .08), borderWidth: 2, pointRadius: 3,
          pointHoverRadius: 5, pointBackgroundColor: [], tension: .25, fill: true,
        },
        { label: '임계값', data: [], borderColor: C.brand, borderWidth: 1.5, borderDash: [6, 4], pointRadius: 0 },
        { label: '절댓값 하한', data: [], borderColor: C.brand, borderWidth: 1.5, borderDash: [6, 4], pointRadius: 0 },
        {
          label: '저장 알림', data: [], borderColor: C.alarm, backgroundColor: C.alarm,
          showLine: false, pointStyle: 'triangle', pointRadius: 7, pointHoverRadius: 9,
        },
      ] },
      options: {
        responsive: true, maintainAspectRatio: false, interaction: { mode: 'nearest', axis: 'xy', intersect: false },
        parsing: false,
        plugins: {
          legend: { labels: { color: C.tick, boxWidth: 12, font: { family: 'IBM Plex Sans' } } },
          tooltip: {
            callbacks: {
              title: (items) => items.length ? fmtDateTime(items[0].parsed.x) : '',
              label: tooltipLabel,
            },
          },
        },
        scales: {
          x: {
            type: 'linear',
            ticks: { callback: (value) => fmtTime(value), color: C.tick, maxTicksLimit: 8, font: { family: 'IBM Plex Mono' } },
            grid: { color: C.grid },
          },
          y: { ticks: { color: C.tick, font: { family: 'IBM Plex Mono' } }, grid: { color: C.grid } },
        },
        animation: false,
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
    lineChart.data.datasets[2].borderColor = C.brand;
    lineChart.data.datasets[3].borderColor = C.alarm;
    lineChart.data.datasets[3].backgroundColor = C.alarm;
    ['x', 'y'].forEach((axis) => {
      lineChart.options.scales[axis].ticks.color = C.tick;
      lineChart.options.scales[axis].grid.color = C.grid;
    });
    lineChart.options.plugins.legend.labels.color = C.tick;
    lineChart.update('none');
  }
  if (typeof window !== 'undefined') window.addEventListener('sm-theme-change', applyPalette);

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
    const monitored = devices.filter((device) => !['NOT_MONITORED', 'PLANNED_OFFLINE', 'RESUMING'].includes(device.freshness));
    const online = monitored.filter((device) => device.freshness === 'ONLINE').length;
    const alarmCount = devices.reduce((sum, device) => sum + (Number(device.currentAlarmCount) || 0), 0);
    const latest = devices.map((device) => date(device.lastSeenAt)).filter(Boolean)
      .sort((a, b) => b.getTime() - a.getTime())[0];
    const hasStale = monitored.some((device) => device.freshness === 'STALE' || device.freshness === 'NEVER_SEEN');

    $('summaryFreshness').textContent = monitored.length ? `${online} / ${monitored.length}` : '운영 예정 없음';
    $('summaryFreshnessLamp').className = `lamp ${hasStale ? 'warn' : monitored.length ? 'ok' : 'idle'}`;
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
        <div class="metric"><span>알람 유지 중 채널</span><b>${Number(device.currentAlarmCount) || 0} 채널</b></div>
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
    const condition = channel.thresholdDirection === 'BELOW' ? '미만'
      : channel.thresholdDirection === 'ABS_ABOVE' ? '|값| 초과' : '초과';
    return `${condition} ${fmtNum(channel.thresholdValue)}${channel.unit ? ` ${channel.unit}` : ''}`;
  }

  function channelCardHtml(channel) {
    const selected = String(channel.id) === String(currentChannelId) ? ' selected' : '';
    const alarm = channel.inAlarm ? ' alarm' : '';
    const state = channel.inAlarm ? '<span class="freshness"><span class="lamp alarm"></span>알람</span>'
      : channel.anomaly ? '<span class="freshness"><span class="lamp warn"></span>순간 이상</span>'
        : '<span class="freshness"><span class="lamp ok"></span>정상</span>';
    return `<button class="channel-card${selected}${alarm}" type="button" data-channel-id="${escapeHtml(String(channel.id))}" aria-pressed="${selected ? 'true' : 'false'}">
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
    $('channelCount').textContent = `${(device.channels || []).length} 채널`;
    $('channelGrid').innerHTML = (device.channels || []).length
      ? device.channels.map(channelCardHtml).join('')
      : '<div class="panel empty">등록된 채널이 없습니다.</div>';

    const selectedChannel = (device.channels || []).find((channel) => String(channel.id) === String(currentChannelId));
    $('chartChannelName').textContent = selectedChannel
      ? `${selectedChannel.code}${selectedChannel.unit ? ` (${selectedChannel.unit})` : ''}` : '채널 추이';
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
    deviceAlerts = [];
    showAllDeviceAlerts = false;
    expandedDeviceAlertId = null;
    renderDeviceAlerts();
    if (!(entry.device.channels || []).some((channel) => String(channel.id) === String(currentChannelId))) {
      currentChannelId = entry.device.channels && entry.device.channels.length ? String(entry.device.channels[0].id) : null;
    }
    resetChartData();
    $('overviewView').classList.add('hidden');
    $('detailView').classList.remove('hidden');
    renderDetail();
    await Promise.all([loadReadings(), loadChannelAlerts(), loadDeviceAlerts()]);
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

  function selectedChannel() {
    const entry = currentEntry();
    return entry && (entry.device.channels || [])
      .find((channel) => String(channel.id) === String(currentChannelId));
  }

  function renderChart() {
    const channel = selectedChannel();
    const wallClockNow = Date.now();
    const latestObservedAt = rawReadings.length ? rawReadings[rawReadings.length - 1].x : wallClockNow;
    const now = Math.max(wallClockNow, latestObservedAt);
    const visibleRaw = filterTimeWindow(rawReadings, windowMinutes, now);
    const displayed = downsampleEvenly(visibleRaw, CHART_POINT_LIMIT);
    const hasThreshold = channel && number(channel.thresholdValue) != null;

    pointAnomalies = displayed.map((reading) => hasThreshold && reading.anomaly === true);
    lineChart.data.datasets[0].data = displayed.map((reading) => ({ x: reading.x, y: reading.y }));
    lineChart.data.datasets[0].pointBackgroundColor = pointAnomalies.map(pointColor);
    lineChart.data.datasets[1].label = channel && channel.thresholdDirection === 'ABS_ABOVE'
      ? '절댓값 상한' : '임계값';
    lineChart.data.datasets[1].data = hasThreshold && displayed.length
      ? [
          { x: now - windowMinutes * 60 * 1000, y: channel.thresholdValue },
          { x: now, y: channel.thresholdValue },
        ] : [];
    lineChart.data.datasets[2].data = hasThreshold && displayed.length
      && channel.thresholdDirection === 'ABS_ABOVE'
      ? [
          { x: now - windowMinutes * 60 * 1000, y: -channel.thresholdValue },
          { x: now, y: -channel.thresholdValue },
        ] : [];
    lineChart.data.datasets[3].data = matchAlertMarkers(visibleRaw, channelAlerts, currentChannelId);
    lineChart.options.scales.x.min = now - windowMinutes * 60 * 1000;
    lineChart.options.scales.x.max = now;
    lineChart.update('none');

    $('chartMeta').textContent = `표시 ${displayed.length}점 · 최근 ${windowMinutes}분 범위`;
    $('lineEmpty').textContent = currentChannelId
      ? `최근 ${windowMinutes}분 판독이 없습니다.` : '채널 카드에서 채널을 선택하세요.';
    showLineChart(displayed.length > 0);
  }

  function resetChartData() {
    readingsRequestId += 1;
    channelAlertsRequestId += 1;
    rawReadings = [];
    channelAlerts = [];
    if (lineChart) renderChart();
  }

  async function loadReadings() {
    if (!currentChannelId) { resetChartData(); return; }
    const requestedChannel = String(currentChannelId);
    const requestId = ++readingsRequestId;
    const res = await Auth.apiFetch(`/channels/${encodeURIComponent(requestedChannel)}/readings?limit=500`);
    if (!res || !res.ok) return;
    let readings;
    try { readings = await res.json(); } catch { return; }
    if (requestId !== readingsRequestId || requestedChannel !== String(currentChannelId) || !Array.isArray(readings)) return;
    // 같은 batch는 서버 재계산 결과가 SSE 시점의 로컬 anomaly 값을 갱신한다.
    rawReadings = mergeReadings(rawReadings, normalizeReadings(readings));
    renderChart();
  }

  async function loadChannelAlerts() {
    if (!currentChannelId) { channelAlerts = []; renderChart(); return; }
    const requestedChannel = String(currentChannelId);
    const requestId = ++channelAlertsRequestId;
    const res = await Auth.apiFetch(`/alerts/channel/${encodeURIComponent(requestedChannel)}`);
    if (!res || !res.ok) return;
    let alerts;
    try { alerts = await res.json(); } catch { return; }
    if (requestId !== channelAlertsRequestId || requestedChannel !== String(currentChannelId) || !Array.isArray(alerts)) return;
    channelAlerts = alerts;
    renderChart();
  }

  function evidenceHtml(alert) {
    const parts = [];
    if (alert.evidence) parts.push(`근거 ${escapeHtml(alert.evidence)}`);
    if (alert.recommendation) parts.push(`권고 ${escapeHtml(alert.recommendation)}`);
    return parts.length ? `<span class="evi">${parts.join(' · ')}</span>` : '';
  }

  function renderDeviceAlerts(focusAlertId) {
    const entry = currentEntry();
    if (!entry) return;
    const visible = showAllDeviceAlerts ? deviceAlerts : deviceAlerts.slice(0, 5);
    if (expandedDeviceAlertId != null
        && !visible.some((alert) => String(alert.id) === String(expandedDeviceAlertId))) {
      expandedDeviceAlertId = null;
    }
    $('alarmList').innerHTML = visible.length ? visible.map((alert) => {
      const channel = channelName(entry.device, alert.channelId);
      const lamp = alert.severity === 'CRITICAL' ? 'alarm' : alert.severity === 'WARNING' ? 'warn' : 'ok';
      const expanded = String(alert.id) === String(expandedDeviceAlertId);
      const detailId = `device-alert-detail-${alert.id}`;
      return `<li><button class="alarm-summary" type="button" data-device-alert-id="${escapeHtml(String(alert.id))}" aria-expanded="${expanded}" aria-controls="${escapeHtml(detailId)}"><span class="alarm-line"><span class="freshness"><span class="lamp ${lamp}"></span><strong>${escapeHtml(channel)}</strong> · ${escapeHtml(alert.severity || 'INFO')}</span><span class="alarm-time">${escapeHtml(fmtDateTime(alert.createdAt))}</span></span><span class="alarm-message">${escapeHtml(alert.message || '')}</span></button><div id="${escapeHtml(detailId)}" class="alarm-detail${expanded ? '' : ' hidden'}">${escapeHtml(alert.message || '')}${evidenceHtml(alert)}</div></li>`;
    }).join('') : '<li class="empty">최근 알림이 없습니다.</li>';
    const toggle = $('alarmListToggle');
    toggle.classList.toggle('hidden', deviceAlerts.length <= 5);
    toggle.textContent = showAllDeviceAlerts ? '접기' : '최근 20건 보기';
    toggle.setAttribute('aria-expanded', String(showAllDeviceAlerts));
    if (focusAlertId != null) {
      const focused = [...$('alarmList').querySelectorAll('[data-device-alert-id]')]
        .find((button) => String(button.dataset.deviceAlertId) === String(focusAlertId));
      if (focused) focused.focus({ preventScroll: true });
    }
  }

  async function loadDeviceAlerts() {
    const entry = currentEntry();
    if (!entry) return;
    const requestedDevice = String(entry.device.id);
    const requestId = ++deviceAlertsRequestId;
    const res = await Auth.apiFetch(`/alerts?deviceId=${encodeURIComponent(requestedDevice)}&size=20&sort=createdAt,desc`);
    if (!res || !res.ok) return;
    let page;
    try { page = await res.json(); } catch { return; }
    if (requestId !== deviceAlertsRequestId || !currentEntry() || requestedDevice !== String(currentEntry().device.id)) return;
    deviceAlerts = Array.isArray(page.content) ? page.content : [];
    if (expandedDeviceAlertId != null
        && !deviceAlerts.some((alert) => String(alert.id) === String(expandedDeviceAlertId))) {
      expandedDeviceAlertId = null;
    }
    renderDeviceAlerts();
  }

  function appendLivePoint(batchId, value, anomaly, observedAt) {
    const observed = date(observedAt);
    const sensorValue = number(value);
    if (!observed || observed.getTime() > Date.now() + MAX_FUTURE_SKEW_MS || sensorValue == null) return;
    if (batchId != null) rawReadings = rawReadings.filter((reading) => String(reading.batchId) !== String(batchId));
    rawReadings.push({ batchId, x: observed.getTime(), y: sensorValue, anomaly: anomaly === true });
    rawReadings.sort((a, b) => a.x - b.x);
    rawReadings = rawReadings.slice(-RAW_POINT_LIMIT);
    renderChart();
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
      if (selectedReading) appendLivePoint(batch.batchId, selectedReading.value, selectedReading.anomaly, batch.observedAt);
    }
  }

  function initSse() {
    const token = Auth.getToken();
    if (!token || sse) return;
    sse = new EventSource('/dashboard/stream?token=' + encodeURIComponent(token));
    sse.addEventListener('connected', () => {
      sseRecovering = false;
      setSseLamp('ok');
    });
    sse.addEventListener('sensor-data', (event) => {
      try { applySensorBatch(JSON.parse(event.data)); } catch {}
    });
    sse.addEventListener('alert', async (event) => {
      let alert;
      try { alert = JSON.parse(event.data); } catch { return; }
      if (!deviceIndex.has(String(alert.deviceId))) return;
      await loadOverview();
      if (String(currentDeviceId) === String(alert.deviceId)) {
        const refreshes = [loadDeviceAlerts()];
        if (alert.channelId != null && String(currentChannelId) === String(alert.channelId)) {
          refreshes.push(loadChannelAlerts());
        }
        await Promise.all(refreshes);
      }
    });
    sse.onerror = async () => {
      setSseLamp('alarm');
      if (!sse || sse.readyState !== EventSource.CLOSED || sseRecovering) {
        console.warn('[SSE] 연결 오류 — 브라우저 자동 재연결 대기');
        return;
      }

      const closed = sse;
      sse = null;
      closed.close();
      sseRecovering = true;
      console.warn('[SSE] 연결 종료 — 토큰 갱신 후 재구독');

      if (!await Auth.refreshAccessToken()) {
        Auth.toLogin();
        return;
      }
      clearTimeout(sseReconnectTimer);
      sseReconnectTimer = setTimeout(() => {
        sseRecovering = false;
        initSse();
      }, 1000);
    };
  }

  async function resync() {
    const wasDetail = currentDeviceId != null;
    await loadOverview();
    if (wasDetail && currentEntry()) await Promise.all([loadReadings(), loadChannelAlerts(), loadDeviceAlerts()]);
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
    $('alarmList').addEventListener('click', (event) => {
      const button = event.target.closest('[data-device-alert-id]');
      if (!button) return;
      const alertId = button.dataset.deviceAlertId;
      expandedDeviceAlertId = String(expandedDeviceAlertId) === String(alertId) ? null : alertId;
      renderDeviceAlerts(alertId);
    });
    $('alarmListToggle').addEventListener('click', () => {
      showAllDeviceAlerts = !showAllDeviceAlerts;
      renderDeviceAlerts();
    });
    $('channelGrid').addEventListener('click', async (event) => {
      const card = event.target.closest('[data-channel-id]');
      if (!card || String(card.dataset.channelId) === String(currentChannelId)) return;
      currentChannelId = card.dataset.channelId;
      resetChartData();
      renderDetail();
      await Promise.all([loadReadings(), loadChannelAlerts()]);
    });
    $('windowSelector').addEventListener('click', (event) => {
      const button = event.target.closest('[data-window-minutes]');
      if (!button) return;
      const nextWindow = Number(button.dataset.windowMinutes);
      if (![1, 5, 15].includes(nextWindow)) return;
      windowMinutes = nextWindow;
      $('windowSelector').querySelectorAll('[data-window-minutes]').forEach((item) => {
        const active = Number(item.dataset.windowMinutes) === windowMinutes;
        item.classList.toggle('active', active);
        item.setAttribute('aria-pressed', String(active));
      });
      renderChart();
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

  if (typeof module === 'object' && module.exports) {
    module.exports = { normalizeReadings, filterTimeWindow, downsampleEvenly, mergeReadings, matchAlertMarkers };
  }
  if (typeof document !== 'undefined') document.addEventListener('DOMContentLoaded', boot);
})();

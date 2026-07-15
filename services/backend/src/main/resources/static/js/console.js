/* ============================================================
   관리 콘솔 — 역할 게이팅 탭 (사용자 승인 / 공장 / 구역 / 장치)
   window.Auth(공유 인증) 사용. 프레임워크 없음.
   ============================================================ */
(function () {
  'use strict';

  if (!Auth.requireLogin()) return;

  /* ── 공통 유틸 ── */
  const $ = (sel, root = document) => root.querySelector(sel);
  const el = (tag, cls, html) => {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html != null) n.innerHTML = html;
    return n;
  };

  function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  let toastTimer = null;
  function toast(msg, isErr) {
    const t = el('div', 'toast' + (isErr ? ' err' : ''));
    t.textContent = msg || (isErr ? '오류가 발생했습니다.' : '완료되었습니다.');
    document.body.appendChild(t);
    requestAnimationFrame(() => t.classList.add('show'));
    setTimeout(() => {
      t.classList.remove('show');
      setTimeout(() => t.remove(), 250);
    }, 2500);
    if (toastTimer) clearTimeout(toastTimer);
  }

  /* apiFetch 래퍼: 성공 시 파싱된 body, 실패 시 throw(메시지 포함) */
  async function readMsg(res, fallback) {
    try {
      const j = await res.json();
      return j && j.message ? j.message : fallback;
    } catch { return fallback; }
  }
  async function apiGet(url) {
    const res = await Auth.apiFetch(url);
    if (!res) throw new Error('세션이 만료되었습니다.');
    if (!res.ok) throw new Error(await readMsg(res, '데이터를 불러오지 못했습니다.'));
    try { return await res.json(); } catch { return null; }
  }
  /* mutation: 성공 메시지 반환. 실패 시 throw */
  async function apiMutate(url, method, body, okFallback) {
    const opts = { method };
    if (body !== undefined) opts.body = JSON.stringify(body);
    const res = await Auth.apiFetch(url, opts);
    if (!res) throw new Error('세션이 만료되었습니다.');
    if (!res.ok) throw new Error(await readMsg(res, '요청을 처리하지 못했습니다.'));
    return await readMsg(res, okFallback);
  }

  function roleTag(role) {
    const rl = Auth.ROLE_LABEL[role] || { text: role || '-', cls: '' };
    return `<span class="tag ${rl.cls}">${escapeHtml(rl.text)}</span>`;
  }

  /* ── 상단바 신원 + 시계 ── */
  (function initTopbar() {
    const role = Auth.getRole();
    const rl = Auth.ROLE_LABEL[role] || { text: role, cls: '' };
    $('#navEmployee').textContent = Auth.getEmployeeId() || '';
    const rt = $('#navRole');
    rt.textContent = rl.text;
    rt.className = 'tag ' + rl.cls;

    const clockEl = $('#clock');
    const tick = () => { clockEl.textContent = new Date().toLocaleTimeString('ko-KR', { hour12: false }); };
    tick();
    setInterval(tick, 1000);
  })();

  /* ── 탭 정의 (역할 게이팅) ── */
  const TABS = [
    { key: 'users',    label: '사용자 승인', roles: ['SYSTEM_ADMIN', 'FACTORY_ADMIN'], render: renderUsers },
    { key: 'factories',label: '공장',        roles: ['SYSTEM_ADMIN'],              render: renderFactories },
    { key: 'zones',    label: '구역',        roles: ['SYSTEM_ADMIN', 'FACTORY_ADMIN'], render: renderZones },
    { key: 'devices',  label: '장치',        roles: ['SYSTEM_ADMIN', 'FACTORY_ADMIN', 'MEMBER'], render: renderDevices },
  ];

  const role = Auth.getRole();
  const allowed = TABS.filter(t => t.roles.includes(role));

  const panel = $('#tabPanel');
  const tabbar = $('#tabbar');

  if (allowed.length === 0) {
    $('#noAccess').classList.remove('hidden');
  } else {
    tabbar.classList.remove('hidden');
    allowed.forEach((t, i) => {
      const btn = el('button', 'tab' + (i === 0 ? ' active' : ''));
      btn.textContent = t.label;
      btn.addEventListener('click', () => activate(t.key));
      btn.dataset.key = t.key;
      tabbar.appendChild(btn);
    });
    activate(allowed[0].key);
  }

  function activate(key) {
    Array.from(tabbar.children).forEach(b => b.classList.toggle('active', b.dataset.key === key));
    const tab = allowed.find(t => t.key === key);
    if (!tab) return;
    panel.innerHTML = '';
    tab.render(panel);
  }

  /* 리스트 컨테이너에 로딩/빈/에러 표시 헬퍼 */
  function setLoading(node) { node.innerHTML = '<div class="empty">불러오는 중…</div>'; }
  function setEmpty(node, msg) { node.innerHTML = `<div class="empty">${escapeHtml(msg || '표시할 항목이 없습니다.')}</div>`; }
  function setError(node, msg) { node.innerHTML = `<div class="empty">${escapeHtml(msg)}</div>`; }

  /* ==========================================================
     탭 1) 사용자 승인
     ========================================================== */
  function renderUsers(root) {
    root.innerHTML = `
      <div class="panel">
        <div class="panel-head">
          <div class="flex items-center gap-sm"><span class="lamp ok"></span><h3>사용자</h3></div>
          <label class="flex items-center gap-sm" style="font-size:.82rem; color:var(--text-dim); cursor:pointer;">
            <input type="checkbox" id="pendingOnly"/> 대기만 보기
          </label>
        </div>
        <div class="table-wrap" id="usersList"><div class="empty">불러오는 중…</div></div>
      </div>`;
    const listNode = $('#usersList', root);
    const pendingOnly = $('#pendingOnly', root);

    async function load() {
      setLoading(listNode);
      try {
        const url = pendingOnly.checked ? '/admin/users/pending' : '/admin/users';
        const users = await apiGet(url);
        if (!users || users.length === 0) { setEmpty(listNode, '해당하는 사용자가 없습니다.'); return; }
        renderTable(users);
      } catch (e) { setError(listNode, e.message); }
    }

    const STATUS_TAG = {
      PENDING: 'tag-brand', ACTIVE: 'tag-signal', REJECTED: 'tag-alarm',
    };

    function renderTable(users) {
      const rows = users.map(u => {
        const statusCls = STATUS_TAG[u.status] || '';
        const actions = u.status === 'PENDING'
          ? `<div class="actions-cell">
               <button class="btn btn-sm btn-primary" data-act="approve" data-id="${u.id}">승인</button>
               <button class="btn btn-sm btn-danger" data-act="reject" data-id="${u.id}">반려</button>
             </div>`
          : '<span class="faint">—</span>';
        return `<tr>
          <td class="num">${escapeHtml(u.employeeId)}</td>
          <td>${escapeHtml(u.name)}</td>
          <td class="dim">${escapeHtml(u.factoryName) || '—'}</td>
          <td>${roleTag(u.role)}</td>
          <td><span class="tag ${statusCls}">${escapeHtml(u.status)}</span></td>
          <td>${actions}</td>
        </tr>`;
      }).join('');
      listNode.innerHTML = `<table class="table">
        <thead><tr>
          <th>사번</th><th>이름</th><th>공장</th><th>역할</th><th>상태</th><th>액션</th>
        </tr></thead>
        <tbody>${rows}</tbody></table>`;

      listNode.querySelectorAll('button[data-act]').forEach(btn => {
        const id = btn.dataset.id;
        btn.addEventListener('click', () => {
          if (btn.dataset.act === 'approve') {
            openApproveModal(users.find(u => String(u.id) === String(id)));
          } else {
            reject(id);
          }
        });
      });
    }

    async function reject(id) {
      try {
        const msg = await apiMutate(`/admin/users/${id}/reject`, 'PATCH', undefined, '반려 처리되었습니다.');
        toast(msg);
        load();
      } catch (e) { toast(e.message, true); }
    }

    /* 승인 = 역할 부여 + 구역 배정. 부여 가능 역할은 호출자 역할로 제한(백엔드도 검증). */
    async function openApproveModal(user) {
      if (!user) return;
      const grantable = Auth.getRole() === 'SYSTEM_ADMIN'
        ? ['VIEWER', 'MEMBER', 'FACTORY_ADMIN']
        : ['VIEWER', 'MEMBER'];

      let zones = [];
      try { zones = (await apiGet('/admin/zones')) || []; }
      catch (e) { toast(e.message, true); return; }
      // 대상 사용자 공장으로 필터(공장 미지정이면 전체 노출, 백엔드가 동일 공장 강제)
      if (user.factoryId != null) {
        zones = zones.filter(z => String(z.factoryId) === String(user.factoryId));
      }

      const roleOpts = grantable
        .map(r => `<option value="${r}">${(Auth.ROLE_LABEL[r] || {}).text || r}</option>`).join('');
      const zoneRows = zones.length
        ? zones.map(z => `<label><input type="checkbox" value="${z.id}"/> ${escapeHtml(z.name)}
             <span class="faint mono" style="font-size:.72rem;">${escapeHtml(z.factoryName)}</span></label>`).join('')
        : '<div class="faint" style="font-size:.85rem;">배정 가능한 구역이 없습니다. 역할만 부여합니다.</div>';

      const overlay = el('div', 'modal-overlay');
      const box = el('div', 'modal-box');
      box.innerHTML = `
        <h3>승인 — ${escapeHtml(user.name)} <span class="faint mono">${escapeHtml(user.employeeId)}</span></h3>
        <div class="field">
          <label>부여 역할</label>
          <select class="input" id="apRole">${roleOpts}</select>
        </div>
        <div class="field">
          <label>구역 배정 <span class="faint">(복수 선택 가능)</span></label>
          <div class="zone-picker" id="apZones">${zoneRows}</div>
        </div>
        <div class="modal-actions">
          <button class="btn btn-ghost btn-sm" id="apCancel">취소</button>
          <button class="btn btn-primary btn-sm" id="apConfirm">승인 확정</button>
        </div>`;
      overlay.appendChild(box);
      document.body.appendChild(overlay);

      const close = () => overlay.remove();
      overlay.addEventListener('click', e => { if (e.target === overlay) close(); });
      $('#apCancel', box).addEventListener('click', close);
      $('#apConfirm', box).addEventListener('click', async () => {
        const role = $('#apRole', box).value;
        const zoneIds = Array.from(box.querySelectorAll('#apZones input:checked')).map(c => Number(c.value));
        try {
          const msg = await apiMutate(`/admin/users/${user.id}/approve`, 'PATCH', { role, zoneIds }, '승인 처리되었습니다.');
          toast(msg); close(); load();
        } catch (e) { toast(e.message, true); }
      });
    }

    pendingOnly.addEventListener('change', load);
    load();
  }

  /* ==========================================================
     탭 2) 공장 (SYSTEM_ADMIN)
     ========================================================== */
  function renderFactories(root) {
    root.innerHTML = `
      <div class="panel panel-pad">
        <div class="eyebrow" style="margin-bottom:.9rem;">CREATE · 공장 등록</div>
        <div class="form-grid">
          <div class="field"><label for="fName">이름</label><input id="fName" class="input" type="text" placeholder="예: 1공장"/></div>
          <div class="field"><label for="fDesc">설명</label><input id="fDesc" class="input" type="text" placeholder="설명(선택)"/></div>
          <div class="field"><button id="fCreate" class="btn btn-primary">등록</button></div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-head"><div class="flex items-center gap-sm"><span class="lamp ok"></span><h3>공장 목록</h3></div></div>
        <div class="table-wrap" id="facList"><div class="empty">불러오는 중…</div></div>
      </div>`;
    const listNode = $('#facList', root);

    async function load() {
      setLoading(listNode);
      try {
        const items = await apiGet('/admin/factories');
        if (!items || items.length === 0) { setEmpty(listNode, '등록된 공장이 없습니다.'); return; }
        listNode.innerHTML = `<table class="table">
          <thead><tr><th>ID</th><th>이름</th><th>설명</th><th>액션</th></tr></thead>
          <tbody>${items.map(f => `<tr>
            <td class="num dim">${f.id}</td>
            <td>${escapeHtml(f.name)}</td>
            <td class="dim">${escapeHtml(f.description) || '—'}</td>
            <td><div class="actions-cell">
              <button class="btn btn-sm" data-act="edit" data-id="${f.id}"
                data-name="${escapeHtml(f.name)}" data-desc="${escapeHtml(f.description || '')}">수정</button>
              <button class="btn btn-sm btn-danger" data-act="del" data-id="${f.id}"
                data-name="${escapeHtml(f.name)}">삭제</button>
            </div></td>
          </tr>`).join('')}</tbody></table>`;
        bind();
      } catch (e) { setError(listNode, e.message); }
    }

    function bind() {
      listNode.querySelectorAll('button[data-act="edit"]').forEach(b => b.addEventListener('click', () => {
        const name = prompt('공장 이름', b.dataset.name);
        if (name == null) return;
        if (!name.trim()) { toast('이름은 필수입니다.', true); return; }
        const desc = prompt('설명', b.dataset.desc);
        if (desc == null) return;
        update(b.dataset.id, name.trim(), desc.trim());
      }));
      listNode.querySelectorAll('button[data-act="del"]').forEach(b => b.addEventListener('click', () => {
        if (!confirm(`공장 "${b.dataset.name}"을(를) 삭제할까요?`)) return;
        remove(b.dataset.id);
      }));
    }

    async function create() {
      const name = $('#fName', root).value.trim();
      const desc = $('#fDesc', root).value.trim();
      if (!name) { toast('이름은 필수입니다.', true); return; }
      try {
        const msg = await apiMutate('/admin/factories', 'POST', { name, description: desc }, '공장이 등록되었습니다.');
        toast(msg);
        $('#fName', root).value = ''; $('#fDesc', root).value = '';
        load();
      } catch (e) { toast(e.message, true); }
    }
    async function update(id, name, description) {
      try {
        const msg = await apiMutate(`/admin/factories/${id}`, 'PUT', { name, description }, '수정되었습니다.');
        toast(msg); load();
      } catch (e) { toast(e.message, true); }
    }
    async function remove(id) {
      try {
        const msg = await apiMutate(`/admin/factories/${id}`, 'DELETE', undefined, '삭제되었습니다.');
        toast(msg); load();
      } catch (e) { toast(e.message, true); }
    }

    $('#fCreate', root).addEventListener('click', create);
    load();
  }

  /* ==========================================================
     탭 3) 구역 (SYSTEM_ADMIN, FACTORY_ADMIN)
     ========================================================== */
  function renderZones(root) {
    root.innerHTML = `
      <div class="panel panel-pad">
        <div class="eyebrow" style="margin-bottom:.9rem;">CREATE · 구역 등록</div>
        <div class="form-grid">
          <div class="field"><label for="zFactory">공장</label>
            <select id="zFactory" class="input"></select>
          </div>
          <div class="field hidden" id="zFactoryIdWrap"><label for="zFactoryId">공장 ID</label>
            <input id="zFactoryId" class="input" type="number" placeholder="공장 ID"/>
          </div>
          <div class="field"><label for="zName">이름</label><input id="zName" class="input" type="text" placeholder="예: A라인"/></div>
          <div class="field"><label for="zDesc">설명</label><input id="zDesc" class="input" type="text" placeholder="설명(선택)"/></div>
          <div class="field"><button id="zCreate" class="btn btn-primary">등록</button></div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-head"><div class="flex items-center gap-sm"><span class="lamp ok"></span><h3>구역 목록</h3></div></div>
        <div class="table-wrap" id="zoneList"><div class="empty">불러오는 중…</div></div>
      </div>`;
    const listNode = $('#zoneList', root);
    const facSelect = $('#zFactory', root);
    const facIdWrap = $('#zFactoryIdWrap', root);
    const facIdInput = $('#zFactoryId', root);
    const isSysAdmin = Auth.hasRole('SYSTEM_ADMIN');

    /* 공장 옵션: SYSTEM_ADMIN은 /admin/factories, FACTORY_ADMIN은 구역 결과에서 distinct 유도 */
    function fillFactoryOptions(list) {
      if (list && list.length) {
        facSelect.innerHTML = list.map(f => `<option value="${f.id}">${escapeHtml(f.name)}</option>`).join('');
        facSelect.parentElement.classList.remove('hidden');
        facIdWrap.classList.add('hidden');
      } else {
        // 옵션 없음 → 숫자 입력 폴백
        facSelect.parentElement.classList.add('hidden');
        facIdWrap.classList.remove('hidden');
      }
    }

    async function loadFactoryOptions(zones) {
      if (isSysAdmin) {
        try {
          const facs = await apiGet('/admin/factories');
          fillFactoryOptions(facs);
          return;
        } catch { /* 실패 시 구역에서 유도로 폴백 */ }
      }
      // FACTORY_ADMIN(또는 실패): 구역 결과에서 distinct factory 유도
      const seen = new Map();
      (zones || []).forEach(z => { if (z.factoryId != null && !seen.has(z.factoryId)) seen.set(z.factoryId, z.factoryName); });
      fillFactoryOptions(Array.from(seen, ([id, name]) => ({ id, name: name || ('공장 #' + id) })));
    }

    async function load() {
      setLoading(listNode);
      try {
        const zones = await apiGet('/admin/zones');
        await loadFactoryOptions(zones);
        if (!zones || zones.length === 0) { setEmpty(listNode, '등록된 구역이 없습니다.'); return; }
        listNode.innerHTML = `<table class="table">
          <thead><tr><th>공장</th><th>이름</th><th>설명</th><th>액션</th><th>구역 사용자</th></tr></thead>
          <tbody>${zones.map(z => `<tr>
            <td class="dim">${escapeHtml(z.factoryName) || '#' + z.factoryId}</td>
            <td>${escapeHtml(z.name)}</td>
            <td class="dim">${escapeHtml(z.description) || '—'}</td>
            <td><div class="actions-cell">
              <button class="btn btn-sm" data-act="edit" data-id="${z.id}" data-fid="${z.factoryId}"
                data-name="${escapeHtml(z.name)}" data-desc="${escapeHtml(z.description || '')}">수정</button>
              <button class="btn btn-sm btn-danger" data-act="del" data-id="${z.id}" data-name="${escapeHtml(z.name)}">삭제</button>
            </div></td>
            <td><div class="actions-cell">
              <button class="btn btn-sm btn-ghost" data-act="uadd" data-id="${z.id}">사용자 추가</button>
              <button class="btn btn-sm btn-ghost" data-act="udel" data-id="${z.id}">사용자 제거</button>
              <div class="hint" style="flex-basis:100%;">user id 입력</div>
            </div></td>
          </tr>`).join('')}</tbody></table>`;
        bind();
      } catch (e) { setError(listNode, e.message); }
    }

    function bind() {
      listNode.querySelectorAll('button[data-act="edit"]').forEach(b => b.addEventListener('click', () => {
        const name = prompt('구역 이름', b.dataset.name);
        if (name == null) return;
        if (!name.trim()) { toast('이름은 필수입니다.', true); return; }
        const desc = prompt('설명', b.dataset.desc);
        if (desc == null) return;
        update(b.dataset.id, Number(b.dataset.fid), name.trim(), desc.trim());
      }));
      listNode.querySelectorAll('button[data-act="del"]').forEach(b => b.addEventListener('click', () => {
        if (!confirm(`구역 "${b.dataset.name}"을(를) 삭제할까요?`)) return;
        remove(b.dataset.id);
      }));
      listNode.querySelectorAll('button[data-act="uadd"]').forEach(b => b.addEventListener('click', () => {
        const uid = prompt('추가할 사용자 ID (숫자)');
        if (uid == null) return;
        if (!uid.trim() || isNaN(Number(uid))) { toast('유효한 사용자 ID가 필요합니다.', true); return; }
        userAdd(b.dataset.id, Number(uid));
      }));
      listNode.querySelectorAll('button[data-act="udel"]').forEach(b => b.addEventListener('click', () => {
        const uid = prompt('제거할 사용자 ID (숫자)');
        if (uid == null) return;
        if (!uid.trim() || isNaN(Number(uid))) { toast('유효한 사용자 ID가 필요합니다.', true); return; }
        userDel(b.dataset.id, Number(uid));
      }));
    }

    function currentFactoryId() {
      if (!facIdWrap.classList.contains('hidden')) {
        const v = facIdInput.value.trim();
        return v && !isNaN(Number(v)) ? Number(v) : null;
      }
      const v = facSelect.value;
      return v ? Number(v) : null;
    }

    async function create() {
      const factoryId = currentFactoryId();
      const name = $('#zName', root).value.trim();
      const desc = $('#zDesc', root).value.trim();
      if (factoryId == null) { toast('공장을 선택하거나 공장 ID를 입력하세요.', true); return; }
      if (!name) { toast('이름은 필수입니다.', true); return; }
      try {
        const msg = await apiMutate('/admin/zones', 'POST', { factoryId, name, description: desc }, '구역이 등록되었습니다.');
        toast(msg);
        $('#zName', root).value = ''; $('#zDesc', root).value = '';
        load();
      } catch (e) { toast(e.message, true); }
    }
    async function update(id, factoryId, name, description) {
      try {
        const msg = await apiMutate(`/admin/zones/${id}`, 'PUT', { factoryId, name, description }, '수정되었습니다.');
        toast(msg); load();
      } catch (e) { toast(e.message, true); }
    }
    async function remove(id) {
      try {
        const msg = await apiMutate(`/admin/zones/${id}`, 'DELETE', undefined, '삭제되었습니다.');
        toast(msg); load();
      } catch (e) { toast(e.message, true); }
    }
    async function userAdd(zoneId, userId) {
      try {
        const msg = await apiMutate(`/admin/zones/${zoneId}/users`, 'POST', { userId }, '사용자가 추가되었습니다.');
        toast(msg);
      } catch (e) { toast(e.message, true); }
    }
    async function userDel(zoneId, userId) {
      try {
        const msg = await apiMutate(`/admin/zones/${zoneId}/users/${userId}`, 'DELETE', undefined, '사용자가 제거되었습니다.');
        toast(msg);
      } catch (e) { toast(e.message, true); }
    }

    $('#zCreate', root).addEventListener('click', create);
    load();
  }

  /* ==========================================================
     탭 4) 장치 (SYSTEM_ADMIN, MEMBER)
     ========================================================== */
  const DEVICE_TYPES = ['TEMPERATURE', 'PRESSURE', 'CURRENT', 'POWER', 'ACCELERATION'];

  function renderDevices(root) {
    root.innerHTML = `
      <div class="panel panel-pad">
        <div class="eyebrow" style="margin-bottom:.9rem;">CREATE · 장치 등록</div>
        <div class="form-grid">
          <div class="field"><label for="dName">이름</label><input id="dName" class="input" type="text" placeholder="예: TEMP-01"/></div>
          <div class="field"><label for="dType">타입</label>
            <select id="dType" class="input">${DEVICE_TYPES.map(t => `<option value="${t}">${t}</option>`).join('')}</select>
          </div>
          <div class="field"><label for="dLoc">위치</label><input id="dLoc" class="input" type="text" placeholder="예: A라인 3번"/></div>
          <div class="field"><label for="dThr">임계값</label><input id="dThr" class="input" type="number" step="any" placeholder="예: 80"/></div>
          <div class="field"><label for="dZone">구역</label>
            <select id="dZone" class="input"><option value="">— 직접 입력 —</option></select>
          </div>
          <div class="field"><label for="dZoneId">구역 ID (직접)</label><input id="dZoneId" class="input" type="number" placeholder="zoneId"/></div>
          <div class="field"><button id="dCreate" class="btn btn-primary">등록</button></div>
        </div>
        <div class="hint">구역 목록은 기존 장치들에서 유도됩니다. 없으면 구역 ID를 직접 입력하세요.</div>
      </div>
      <div class="panel">
        <div class="panel-head"><div class="flex items-center gap-sm"><span class="lamp ok"></span><h3>장치 목록</h3></div></div>
        <div class="table-wrap" id="devList"><div class="empty">불러오는 중…</div></div>
      </div>`;
    const listNode = $('#devList', root);
    const zoneSelect = $('#dZone', root);

    function fillZoneOptions(devices) {
      const seen = new Map();
      (devices || []).forEach(d => { if (d.zoneId != null && !seen.has(d.zoneId)) seen.set(d.zoneId, d.zoneName); });
      const opts = ['<option value="">— 직접 입력 —</option>']
        .concat(Array.from(seen, ([id, name]) => `<option value="${id}">${escapeHtml(name || '구역 #' + id)}</option>`));
      zoneSelect.innerHTML = opts.join('');
    }

    async function load() {
      setLoading(listNode);
      try {
        const devices = await apiGet('/devices');
        fillZoneOptions(devices);
        if (!devices || devices.length === 0) { setEmpty(listNode, '등록된 장치가 없습니다.'); return; }
        listNode.innerHTML = `<table class="table">
          <thead><tr><th>이름</th><th>타입</th><th>위치</th><th>임계값</th><th>구역</th><th>액션</th></tr></thead>
          <tbody>${devices.map(d => `<tr>
            <td>${escapeHtml(d.name)}</td>
            <td><span class="tag tag-signal">${escapeHtml(d.deviceType)}</span></td>
            <td class="dim">${escapeHtml(d.location) || '—'}</td>
            <td class="num">${d.thresholdValue != null ? escapeHtml(d.thresholdValue) : '—'}</td>
            <td class="dim">${escapeHtml(d.zoneName) || '#' + d.zoneId}</td>
            <td><div class="actions-cell">
              <button class="btn btn-sm" data-act="edit" data-id="${d.id}"
                data-name="${escapeHtml(d.name)}" data-type="${escapeHtml(d.deviceType)}"
                data-loc="${escapeHtml(d.location || '')}" data-thr="${d.thresholdValue != null ? escapeHtml(d.thresholdValue) : ''}">수정</button>
              <button class="btn btn-sm btn-danger" data-act="del" data-id="${d.id}" data-name="${escapeHtml(d.name)}">삭제</button>
            </div></td>
          </tr>`).join('')}</tbody></table>`;
        bind();
      } catch (e) { setError(listNode, e.message); }
    }

    function bind() {
      listNode.querySelectorAll('button[data-act="edit"]').forEach(b => b.addEventListener('click', () => {
        const name = prompt('장치 이름', b.dataset.name);
        if (name == null) return;
        if (!name.trim()) { toast('이름은 필수입니다.', true); return; }
        let type = prompt(`타입 (${DEVICE_TYPES.join('/')})`, b.dataset.type);
        if (type == null) return;
        type = type.trim().toUpperCase();
        if (!DEVICE_TYPES.includes(type)) { toast('유효한 타입이 아닙니다.', true); return; }
        const loc = prompt('위치', b.dataset.loc);
        if (loc == null) return;
        const thrStr = prompt('임계값 (숫자)', b.dataset.thr);
        if (thrStr == null) return;
        if (thrStr.trim() === '' || isNaN(Number(thrStr))) { toast('임계값은 숫자여야 합니다.', true); return; }
        update(b.dataset.id, { name: name.trim(), type, location: loc.trim(), thresholdValue: Number(thrStr) });
      }));
      listNode.querySelectorAll('button[data-act="del"]').forEach(b => b.addEventListener('click', () => {
        if (!confirm(`장치 "${b.dataset.name}"을(를) 삭제할까요?`)) return;
        remove(b.dataset.id);
      }));
    }

    function currentZoneId() {
      const sel = zoneSelect.value;
      if (sel) return Number(sel);
      const raw = $('#dZoneId', root).value.trim();
      return raw && !isNaN(Number(raw)) ? Number(raw) : null;
    }

    async function create() {
      const name = $('#dName', root).value.trim();
      const type = $('#dType', root).value;
      const location = $('#dLoc', root).value.trim();
      const thrRaw = $('#dThr', root).value.trim();
      const zoneId = currentZoneId();
      if (!name) { toast('이름은 필수입니다.', true); return; }
      if (thrRaw === '' || isNaN(Number(thrRaw))) { toast('임계값은 숫자여야 합니다.', true); return; }
      if (zoneId == null) { toast('구역을 선택하거나 구역 ID를 입력하세요.', true); return; }
      try {
        const msg = await apiMutate('/devices', 'POST',
          { name, type, location, thresholdValue: Number(thrRaw), zoneId }, '장치가 등록되었습니다.');
        toast(msg);
        $('#dName', root).value = ''; $('#dLoc', root).value = ''; $('#dThr', root).value = '';
        $('#dZoneId', root).value = '';
        load();
      } catch (e) { toast(e.message, true); }
    }
    async function update(id, body) {
      try {
        const msg = await apiMutate(`/devices/${id}`, 'PUT', body, '수정되었습니다.');
        toast(msg); load();
      } catch (e) { toast(e.message, true); }
    }
    async function remove(id) {
      try {
        const msg = await apiMutate(`/devices/${id}`, 'DELETE', undefined, '삭제되었습니다.');
        toast(msg); load();
      } catch (e) { toast(e.message, true); }
    }

    $('#dCreate', root).addEventListener('click', create);
    load();
  }

})();

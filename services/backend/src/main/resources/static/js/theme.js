// 라이트/다크 테마 토글. 초기값은 각 페이지 <head> 인라인 스크립트가 이미 설정(FOUC 방지).
(function () {
  const KEY = 'sm-theme';
  const root = document.documentElement;
  const SUN = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>';
  const MOON = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>';

  function paint() {
    const dark = root.dataset.theme !== 'light';
    document.querySelectorAll('[data-theme-toggle]').forEach((b) => {
      b.innerHTML = dark ? SUN : MOON; // 다크면 해(→라이트로 전환), 라이트면 달
      b.setAttribute('aria-label', dark ? '라이트 모드로 전환' : '다크 모드로 전환');
    });
  }
  function toggle() {
    const next = root.dataset.theme === 'light' ? 'dark' : 'light';
    root.dataset.theme = next;
    try { localStorage.setItem(KEY, next); } catch (e) {}
    paint();
    window.dispatchEvent(new Event('sm-theme-change')); // 차트 등 캔버스 재색칠 트리거
  }
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-theme-toggle]').forEach((b) => b.addEventListener('click', toggle));
    paint();
  });
})();

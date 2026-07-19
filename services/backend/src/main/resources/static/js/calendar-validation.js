(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.CalendarValidation = api;
})(typeof window !== 'undefined' ? window : globalThis, function () {
  'use strict';

  const TIME = /^(\d{2}):(\d{2})$/;

  function minute(value, allow24) {
    const match = TIME.exec(value || '');
    if (!match) return null;
    const hour = Number(match[1]);
    const min = Number(match[2]);
    if (allow24 && hour === 24 && min === 0) return 1440;
    if (hour > 23 || min > 59) return null;
    return hour * 60 + min;
  }

  function intervalErrors(intervals, label) {
    const errors = [];
    if (intervals.length > 12) errors.push(`${label}: 하루 구간은 최대 12개입니다.`);
    const parsed = intervals.map((interval) => ({
      start: minute(interval.start, false), end: minute(interval.end, true), raw: interval,
    }));
    parsed.forEach((interval) => {
      if (interval.start == null || interval.end == null) errors.push(`${label}: 시간은 HH:mm 형식이어야 합니다.`);
      else if (interval.start >= interval.end) errors.push(`${label}: 시작은 종료보다 빨라야 하며 자정을 넘길 수 없습니다.`);
    });
    parsed.filter((interval) => interval.start != null && interval.end != null)
      .sort((a, b) => a.start - b.start)
      .forEach((interval, index, sorted) => {
        if (index && interval.start <= sorted[index - 1].end) {
          errors.push(`${label}: 겹치거나 맞닿은 구간은 하나로 합쳐 주세요.`);
        }
      });
    return errors;
  }

  function validate(payload) {
    const errors = [];
    if (!payload || typeof payload !== 'object') return { valid: false, errors: ['캘린더 값이 없습니다.'] };
    if (!payload.timezone || typeof payload.timezone !== 'string') errors.push('timezone을 입력해 주세요.');
    if (!Number.isInteger(payload.resumeGraceSeconds) || payload.resumeGraceSeconds < 0 || payload.resumeGraceSeconds > 86400) {
      errors.push('재개 유예는 0~86400초여야 합니다.');
    }
    if (!Number.isInteger(payload.revision) || payload.revision < 0) errors.push('revision이 올바르지 않습니다.');
    if (!Array.isArray(payload.weeklyIntervals) || !Array.isArray(payload.dateOverrides)) {
      errors.push('주간 일정과 날짜 예외 목록이 필요합니다.');
      return { valid: false, errors };
    }
    const days = new Map();
    payload.weeklyIntervals.forEach((interval) => {
      if (!interval || !interval.dayOfWeek) errors.push('주간 일정의 요일이 비어 있습니다.');
      else {
        if (!days.has(interval.dayOfWeek)) days.set(interval.dayOfWeek, []);
        days.get(interval.dayOfWeek).push(interval);
      }
    });
    days.forEach((intervals, day) => errors.push(...intervalErrors(intervals, day)));
    if (payload.dateOverrides.length > 1000) errors.push('날짜 예외는 최대 1000개입니다.');
    const dates = new Set();
    payload.dateOverrides.forEach((override) => {
      if (!override || !override.date || !override.kind || !Array.isArray(override.intervals)) {
        errors.push('날짜 예외 형식이 올바르지 않습니다.'); return;
      }
      if (dates.has(override.date)) errors.push(`${override.date}: 날짜 예외가 중복됐습니다.`);
      dates.add(override.date);
      if (override.kind === 'CLOSED' && override.intervals.length) errors.push(`${override.date}: 휴무일은 구간을 가질 수 없습니다.`);
      if (override.kind === 'OPEN' && !override.intervals.length) errors.push(`${override.date}: 특근일은 구간이 하나 이상 필요합니다.`);
      errors.push(...intervalErrors(override.intervals, override.date));
    });
    return { valid: errors.length === 0, errors: [...new Set(errors)] };
  }

  return { minute, validate };
});

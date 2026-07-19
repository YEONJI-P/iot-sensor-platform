'use strict';

const assert = require('node:assert/strict');
const { minute, validate } = require('../../main/resources/static/js/calendar-validation.js');

assert.equal(minute('00:00', false), 0);
assert.equal(minute('24:00', true), 1440);
assert.equal(minute('24:00', false), null);

const base = {
  timezone: 'Asia/Seoul', resumeGraceSeconds: 300, revision: 0,
  weeklyIntervals: [{ dayOfWeek: 'MONDAY', start: '08:00', end: '18:00' }],
  dateOverrides: [],
};
assert.equal(validate(base).valid, true);
assert.equal(validate({ ...base, weeklyIntervals: [
  { dayOfWeek: 'MONDAY', start: '08:00', end: '12:00' },
  { dayOfWeek: 'MONDAY', start: '12:00', end: '18:00' },
] }).valid, false);
assert.equal(validate({ ...base, dateOverrides: [
  { date: '2026-07-20', kind: 'CLOSED', intervals: [{ start: '08:00', end: '09:00' }] },
] }).valid, false);
assert.equal(validate({ ...base, dateOverrides: [
  { date: '2026-07-20', kind: 'OPEN', intervals: [] },
] }).valid, false);

console.log('calendar-validation: ok');

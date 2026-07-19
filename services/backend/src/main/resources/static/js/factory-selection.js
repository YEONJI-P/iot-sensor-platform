/* 공장 선택 UI에서 공유하는 순수 데이터 변환 함수. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.FactorySelection = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function parsePositiveId(value) {
    const text = String(value == null ? '' : value).trim();
    if (!/^[1-9]\d*$/.test(text)) return null;
    const id = Number(text);
    return Number.isSafeInteger(id) ? id : null;
  }

  function isAllowedId(value, allowedValues) {
    const id = parsePositiveId(value);
    if (id == null) return false;
    return Array.from(allowedValues || []).some(candidate => parsePositiveId(candidate) === id);
  }

  function filterZones(zones, factoryId) {
    const id = parsePositiveId(factoryId);
    if (id == null) return [];
    return (zones || []).filter(zone => parsePositiveId(zone && zone.factoryId) === id);
  }

  function buildApprovalPayload(role, factoryId, zoneValues) {
    const parsedFactoryId = parsePositiveId(factoryId);
    if (!role || parsedFactoryId == null) return null;

    const zoneIds = [];
    for (const value of zoneValues || []) {
      const zoneId = parsePositiveId(value);
      if (zoneId == null) return null;
      zoneIds.push(zoneId);
    }
    return { role, factoryId: parsedFactoryId, zoneIds };
  }

  return { parsePositiveId, isAllowedId, filterZones, buildApprovalPayload };
});

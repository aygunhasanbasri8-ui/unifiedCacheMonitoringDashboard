import client from "./client";

// Config'de tanımlı tabloları ve filtre alanlarını getirir
export function getTables() {
  return client.get("/v1/cacheloader/tables").then((r) => r.data);
}

// Seçili tabloları cache'e seed eder ve key listesini döner
// tables: string[] (boşsa tüm tablolar), filters: { id?, name?, status?, merchantId?, tckn? }
export function getKeys({ tables = [], filters = {} } = {}) {
  const params = {};
  if (tables.length) params.tables = tables.join(",");
  Object.entries(filters).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== "") params[k] = v;
  });
  return client.get("/v1/cacheloader/keys", { params }).then((r) => r.data);
}

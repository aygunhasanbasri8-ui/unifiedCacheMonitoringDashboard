import { useEffect, useState } from "react";
import { getTables, getKeys } from "../api/cacheLoader";
import "./CacheLoader.css";

const PREVIEW_LIMIT = 500;

const FIELD_LABELS = {
  id: "ID",
  name: "İsim",
  status: "Durum",
  merchantId: "Merchant ID",
  tckn: "TCKN",
};

function TableKeysBlock({ tableResult }) {
  const [showAll, setShowAll] = useState(false);
  const [copiedKey, setCopiedKey] = useState(null);

  const keys = tableResult.keys ?? [];
  const visibleKeys = showAll ? keys : keys.slice(0, PREVIEW_LIMIT);
  const truncated = keys.length > PREVIEW_LIMIT && !showAll;

  function handleCopy(key) {
    navigator.clipboard?.writeText(key).then(() => {
      setCopiedKey(key);
      setTimeout(() => setCopiedKey((current) => (current === key ? null : current)), 1200);
    });
  }

  function handleCopyAll() {
    navigator.clipboard?.writeText(keys.join("\n"));
  }

  return (
    <div className="cacheloader-table-block">
      <div className="cacheloader-table-header">
        <span className="cacheloader-table-name">{tableResult.table}</span>
        <span className="cacheloader-table-count">{tableResult.count} key</span>
        <button type="button" className="cacheloader-copy-all" onClick={handleCopyAll}>
          Tümünü Kopyala
        </button>
      </div>

      <ul className="cacheloader-key-list">
        {visibleKeys.map((key) => (
          <li key={key} className="cacheloader-key-row">
            <span className="cacheloader-key-text">{key}</span>
            <button
              type="button"
              className="cacheloader-key-copy"
              onClick={() => handleCopy(key)}
              title="Kopyala"
            >
              {copiedKey === key ? "✓" : "⧉"}
            </button>
          </li>
        ))}
        {keys.length === 0 && <li className="cacheloader-key-empty">Key bulunamadı.</li>}
      </ul>

      {truncated && (
        <button type="button" className="cacheloader-show-all" onClick={() => setShowAll(true)}>
          Tümünü göster ({keys.length})
        </button>
      )}
    </div>
  );
}

export default function CacheLoader() {
  const [tables, setTables] = useState([]);
  const [tablesLoading, setTablesLoading] = useState(true);
  const [tablesError, setTablesError] = useState("");

  const [selectedTables, setSelectedTables] = useState(() => new Set());
  const [filters, setFilters] = useState({ id: "", name: "", status: "", merchantId: "", tckn: "" });

  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [errorBanner, setErrorBanner] = useState("");

  useEffect(() => {
    getTables()
      .then((data) => setTables(data))
      .catch(() => setTablesError("Tablo listesi alınamadı."))
      .finally(() => setTablesLoading(false));
  }, []);

  function toggleTable(name) {
    setSelectedTables((prev) => {
      const next = new Set(prev);
      if (next.has(name)) {
        next.delete(name);
      } else {
        next.add(name);
      }
      return next;
    });
  }

  const relevantTables =
    selectedTables.size > 0 ? tables.filter((t) => selectedTables.has(t.table)) : tables;
  const extraFields = Array.from(
    new Set(relevantTables.flatMap((t) => t.filterFields ?? []).filter((f) => f !== "id" && f !== "name"))
  );

  async function handleFetch() {
    setLoading(true);
    setErrorBanner("");
    try {
      const data = await getKeys({ tables: Array.from(selectedTables), filters });
      setResult(data);
    } catch (err) {
      const status = err.response?.status;
      if (status === 404) {
        const table = err.response?.data?.table ?? Array.from(selectedTables).join(", ");
        setErrorBanner(`Tablo bulunamadı: ${table}`);
      } else if (status === 400) {
        setErrorBanner("Geçersiz filtre değeri.");
      } else {
        setErrorBanner("İstek başarısız oldu.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="cacheloader-page">
      <h1 className="cacheloader-title">Cache Loader</h1>

      <div className="cacheloader-tables">
        <h2 className="cacheloader-section-title">Tablolar</h2>

        {tablesLoading && <div className="cacheloader-status">Yükleniyor...</div>}
        {tablesError && <div className="cacheloader-banner">{tablesError}</div>}

        {!tablesLoading && !tablesError && (
          <>
            <div className="cacheloader-table-checkboxes">
              {tables.map((t) => (
                <label key={t.table} className="cacheloader-checkbox">
                  <input
                    type="checkbox"
                    checked={selectedTables.has(t.table)}
                    onChange={() => toggleTable(t.table)}
                  />
                  <span>{t.table}</span>
                  <span className="cacheloader-table-meta">
                    {t.keyPrefix} · TTL {t.ttlSeconds}s
                  </span>
                </label>
              ))}
              {tables.length === 0 && (
                <div className="cacheloader-empty">Tanımlı tablo bulunamadı.</div>
              )}
            </div>
            <div className="cacheloader-hint">
              Hiçbir tablo seçilmezse tüm tablolar için sorgu yapılır.
            </div>
          </>
        )}
      </div>

      <div className="cacheloader-filters">
        <h2 className="cacheloader-section-title">Filtreler</h2>
        <div className="cacheloader-filter-grid">
          <label className="cacheloader-filter-field">
            <span>ID</span>
            <input
              type="text"
              inputMode="numeric"
              value={filters.id}
              placeholder="örn: 42"
              onChange={(e) => setFilters((f) => ({ ...f, id: e.target.value }))}
            />
          </label>
          <label className="cacheloader-filter-field">
            <span>İsim</span>
            <input
              type="text"
              value={filters.name}
              placeholder="içerir..."
              onChange={(e) => setFilters((f) => ({ ...f, name: e.target.value }))}
            />
          </label>
          {extraFields.map((field) => (
            <label key={field} className="cacheloader-filter-field">
              <span>{FIELD_LABELS[field] ?? field}</span>
              <input
                type="text"
                value={filters[field] ?? ""}
                onChange={(e) => setFilters((f) => ({ ...f, [field]: e.target.value }))}
              />
            </label>
          ))}
        </div>

        <button type="button" className="cacheloader-fetch-button" onClick={handleFetch} disabled={loading}>
          {loading ? "Getiriliyor..." : "Getir"}
        </button>
      </div>

      {errorBanner && <div className="cacheloader-banner">{errorBanner}</div>}

      {result && (
        <div className="cacheloader-results">
          <div className="cacheloader-summary">
            Toplam: {result.tableCount} tablo, {result.totalKeys} key
          </div>

          {result.totalKeys === 0 && <div className="cacheloader-empty">Kayıt bulunamadı.</div>}

          {result.results?.map((r) => (
            <TableKeysBlock key={r.table} tableResult={r} />
          ))}
        </div>
      )}
    </div>
  );
}

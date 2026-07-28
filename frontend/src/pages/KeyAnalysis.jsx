import { useEffect, useState, useCallback, Fragment } from "react";
import { useParams, useNavigate } from "react-router-dom";
import client from "../api/client";
import { formatBytes, formatTtl } from "../utils/format";
import "./KeyAnalysis.css";

const PAGE_SIZE = 20;

export default function KeyAnalysis() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [instance, setInstance] = useState(null);

  const [prefixInput, setPrefixInput] = useState("");
  const [activePrefix, setActivePrefix] = useState("");
  const [keys, setKeys] = useState([]);
  const [cursor, setCursor] = useState("0");
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");

  const [topKeys, setTopKeys] = useState([]);
  const [topLoading, setTopLoading] = useState(true);
  const [topError, setTopError] = useState("");

  const [patterns, setPatterns] = useState([]);
  const [patternsLoading, setPatternsLoading] = useState(true);
  const [patternsError, setPatternsError] = useState("");

  const [expandedMaps, setExpandedMaps] = useState(() => new Set());

  const instanceName = instance?.name ?? "";
  const isHazelcast = instance?.type === "HAZELCAST";
  const unitWord = isHazelcast ? "IMap" : "key";

  useEffect(() => {
    client
      .get(`/v1/instances/${id}`)
      .then((res) => setInstance(res.data))
      .catch(() => setInstance(null));
  }, [id]);

  const fetchPatterns = useCallback(async () => {
    setPatternsLoading(true);
    setPatternsError("");
    try {
      const res = await client.get(`/v1/instances/${id}/key-patterns`);
      setPatterns(res.data);
    } catch {
      setPatternsError("Pattern dağılımı alınamadı.");
    } finally {
      setPatternsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı, sonrası manuel
    fetchPatterns();
  }, [fetchPatterns]);

  const fetchPage = useCallback(
    async (prefixValue, cursorValue, append) => {
      if (append) {
        setLoadingMore(true);
      } else {
        setLoading(true);
      }
      setError("");

      try {
        const res = await client.get(`/v1/instances/${id}/keys`, {
          params: { prefix: prefixValue, cursor: cursorValue, count: PAGE_SIZE },
        });
        const page = res.data;

        setKeys((prev) => (append ? [...prev, ...page.content] : page.content));
        setCursor(page.cursor);
        setHasMore(page.hasMore);
      } catch {
        setError("Key listesi alınamadı.");
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [id]
  );

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchPage("", "0", false);
  }, [fetchPage]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    setTopLoading(true);
    client
      .get(`/v1/instances/${id}/keys/top`, { params: { n: 10 } })
      .then((res) => setTopKeys(res.data))
      .catch(() => setTopError("En büyük key'ler alınamadı."))
      .finally(() => setTopLoading(false));
  }, [id]);

  function handleSearch(e) {
    e.preventDefault();
    setActivePrefix(prefixInput);
    setCursor("0");
    fetchPage(prefixInput, "0", false);
  }

  function handleLoadMore() {
    fetchPage(activePrefix, cursor, true);
  }

  function handlePatternClick(pattern) {
    const prefix = pattern.endsWith("*") ? pattern.slice(0, -1) : pattern;
    setPrefixInput(prefix);
    setActivePrefix(prefix);
    setCursor("0");
    fetchPage(prefix, "0", false);
  }

  function toggleExpand(name) {
    setExpandedMaps((prev) => {
      const next = new Set(prev);
      if (next.has(name)) {
        next.delete(name);
      } else {
        next.add(name);
      }
      return next;
    });
  }

  function renderSize(key) {
    const text = isHazelcast ? key.sizeBytes : formatBytes(key.sizeBytes);
    const estimated = key.sizeStatus === "ESTIMATED";
    return (
      <>
        {text}
        {estimated && (
          <span
            title="yaklaşık değer"
            style={{ color: "var(--text-dim)", marginLeft: 4 }}
          >
            ~
          </span>
        )}
      </>
    );
  }

  const sortedPatterns = [...patterns].sort((a, b) => b.totalSizeBytes - a.totalSizeBytes);
  const totalPatternKeyCount = patterns.reduce((sum, p) => sum + p.keyCount, 0);
  const totalPatternSize = patterns.reduce((sum, p) => sum + p.totalSizeBytes, 0);

  return (
    <div className="key-analysis-page">
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, flexWrap: "wrap" }}>
        <h1 className="key-analysis-title">
          {isHazelcast ? "IMap Listesi" : "Key Analizi"} - {instanceName || `#${id}`}
        </h1>
        {!isHazelcast && (
          <button type="button" className="btn-secondary" onClick={() => navigate("/cacheloader")}>
            Cache Loader
          </button>
        )}
      </div>

      <div className="key-analysis-patterns">
        <div className="key-analysis-patterns-header">
          <h2 className="key-analysis-section-title">
            {isHazelcast ? "IMap Dağılımı" : "Key Pattern Dağılımı"}
          </h2>
          <button
            type="button"
            className="key-analysis-patterns-refresh"
            onClick={fetchPatterns}
            disabled={patternsLoading}
          >
            {patternsLoading ? "Yükleniyor..." : "⟳ Yenile"}
          </button>
        </div>

        {patternsLoading && <div className="key-analysis-status">Pattern dağılımı hesaplanıyor...</div>}
        {!patternsLoading && patternsError && (
          <div className="key-analysis-error">{patternsError}</div>
        )}
        {!patternsLoading && !patternsError && patterns.length === 0 && (
          <div className="key-analysis-empty-patterns">Bu instance'ta henüz {unitWord} bulunmuyor.</div>
        )}

        {!patternsLoading && !patternsError && patterns.length > 0 && (
          <>
            <ul className="key-analysis-pattern-list">
              {sortedPatterns.map((p) => {
                const sizePercent = totalPatternSize > 0 ? (p.totalSizeBytes / totalPatternSize) * 100 : 0;
                const countPercent =
                  totalPatternKeyCount > 0 ? (p.keyCount / totalPatternKeyCount) * 100 : 0;

                return (
                  <li
                    key={p.pattern}
                    className={`key-analysis-pattern-row ${isHazelcast ? "key-analysis-pattern-row-static" : ""}`}
                    onClick={isHazelcast ? undefined : () => handlePatternClick(p.pattern)}
                    title={isHazelcast ? undefined : `"${p.pattern}" ile ara`}
                  >
                    <div className="key-analysis-pattern-info">
                      <span className="key-analysis-pattern-label">{p.label}</span>
                      <span className="key-analysis-pattern-technical">{p.pattern}</span>
                    </div>
                    <div className="key-analysis-pattern-bar-track">
                      <div
                        className="key-analysis-pattern-bar-fill"
                        style={{ width: `${sizePercent.toFixed(2)}%` }}
                      />
                    </div>
                    <div className="key-analysis-pattern-stats">
                      <span>{p.keyCount} {unitWord} ({countPercent.toFixed(1)}%)</span>
                      <span>
                        {isHazelcast ? `Entry: ${p.totalSizeBytes}` : formatBytes(p.totalSizeBytes)}
                        {" "}({sizePercent.toFixed(1)}%)
                      </span>
                    </div>
                  </li>
                );
              })}
            </ul>

            <div className="key-analysis-pattern-total">
              Toplam: {totalPatternKeyCount} {unitWord},{" "}
              {isHazelcast ? `${totalPatternSize} entry` : formatBytes(totalPatternSize)}
            </div>
          </>
        )}
      </div>

      {!isHazelcast && (
        <form className="key-analysis-search" onSubmit={handleSearch}>
          <input
            className="key-analysis-search-input"
            type="text"
            placeholder="Key prefix'i ile ara (örn: user:)"
            value={prefixInput}
            onChange={(e) => setPrefixInput(e.target.value)}
          />
          <button type="submit" className="key-analysis-search-button">
            Ara
          </button>
        </form>
      )}

      {loading && <div className="key-analysis-status">Yükleniyor...</div>}
      {error && <div className="key-analysis-error">{error}</div>}

      {!loading && !error && (
        <>
          <table className="key-analysis-table">
            <thead>
              <tr>
                <th>{isHazelcast ? "IMap Adı" : "Key Adı"}</th>
                {!isHazelcast && <th>Tip</th>}
                <th>{isHazelcast ? "Entry Sayısı" : "Boyut"}</th>
                {!isHazelcast && <th>TTL</th>}
              </tr>
            </thead>
            <tbody>
              {keys.map((key) => {
                const isExpanded = expandedMaps.has(key.keyName);
                return (
                  <Fragment key={key.keyName}>
                    <tr
                      className={isHazelcast ? "key-analysis-row-clickable" : ""}
                      onClick={isHazelcast ? () => toggleExpand(key.keyName) : undefined}
                    >
                      <td>{key.keyName}</td>
                      {!isHazelcast && <td>{key.keyType}</td>}
                      <td>{renderSize(key)}</td>
                      {!isHazelcast && <td>{formatTtl(key.ttlMillis)}</td>}
                    </tr>
                    {isHazelcast && isExpanded && (
                      <tr className="key-analysis-expand-row">
                        <td colSpan={2}>
                          <div className="key-analysis-expand-detail">
                            <span>Map Adı: {key.keyName}</span>
                            <span>Entry Sayısı: {key.sizeBytes}</span>
                            <span>Tip: {key.keyType ?? "-"}</span>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
              {keys.length === 0 && (
                <tr>
                  <td colSpan={isHazelcast ? 2 : 4} className="key-analysis-empty">
                    Sonuç bulunamadı.
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          {hasMore && (
            <button
              type="button"
              className="key-analysis-load-more"
              onClick={handleLoadMore}
              disabled={loadingMore}
            >
              {loadingMore ? "Yükleniyor..." : "Daha Fazla"}
            </button>
          )}
        </>
      )}

      <div className="key-analysis-top">
        <h2 className="key-analysis-section-title">
          {isHazelcast ? "En Büyük IMap'ler" : "En Büyük Key'ler"}
        </h2>

        {topLoading && <div className="key-analysis-status">Yükleniyor...</div>}
        {topError && <div className="key-analysis-error">{topError}</div>}

        {!topLoading && !topError && (
          <table className="key-analysis-table">
            <thead>
              <tr>
                <th>{isHazelcast ? "IMap Adı" : "Key Adı"}</th>
                {!isHazelcast && <th>Tip</th>}
                <th>{isHazelcast ? "Entry Sayısı" : "Boyut"}</th>
                {!isHazelcast && <th>TTL</th>}
              </tr>
            </thead>
            <tbody>
              {topKeys.map((key) => (
                <tr key={key.keyName}>
                  <td>{key.keyName}</td>
                  {!isHazelcast && <td>{key.keyType}</td>}
                  <td>{renderSize(key)}</td>
                  {!isHazelcast && <td>{formatTtl(key.ttlMillis)}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

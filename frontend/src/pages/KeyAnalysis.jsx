import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import ProblemError from "../components/ProblemError";
import { formatBytes, formatTtlByStatus } from "../utils/format";
import "./KeyAnalysis.css";

const PAGE_SIZE = 20;
const MIN_REDIS_PREFIX_LENGTH = 3;
const WILDCARD_PATTERN = /[*?]/;

export default function KeyAnalysis() {
  const { id } = useParams();

  const [instance, setInstance] = useState(null);

  const [selectedMap, setSelectedMap] = useState(null);

  const [prefixInput, setPrefixInput] = useState("");
  const [activePrefix, setActivePrefix] = useState("");
  const [matchMode, setMatchMode] = useState("STARTS_WITH");
  const [keys, setKeys] = useState([]);
  const [keysStatus, setKeysStatus] = useState(null);
  const [cursor, setCursor] = useState("0");
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [searched, setSearched] = useState(false);

  const [topKeys, setTopKeys] = useState([]);
  const [topMeta, setTopMeta] = useState(null);
  const [topLoading, setTopLoading] = useState(true);
  const [topError, setTopError] = useState(null);

  const [patterns, setPatterns] = useState([]);
  const [patternsLoading, setPatternsLoading] = useState(true);
  const [patternsError, setPatternsError] = useState(null);

  const instanceName = instance?.name ?? "";
  const isHazelcast = instance?.type === "HAZELCAST";
  // Hazelcast iki aşamalı çalışır: önce map seçimi (mapListMode), sonra seçilen map içinde entry araması (hazelcastEntryMode).
  const hazelcastEntryMode = isHazelcast && selectedMap != null;
  const mapListMode = isHazelcast && !hazelcastEntryMode;
  const unitWord = isHazelcast ? "IMap" : "key";

  const trimmedPrefix = prefixInput.trim();
  const hasWildcard = WILDCARD_PATTERN.test(prefixInput);
  // Redis'te prefix zorunlu (min 3 karakter); Hazelcast entry aramasında prefix opsiyoneldir.
  const prefixTooShort = !isHazelcast && trimmedPrefix.length < MIN_REDIS_PREFIX_LENGTH;
  const searchDisabled = instance != null && prefixTooShort;

  useEffect(() => {
    client
      .post(`/v1/instances/${id}/detail`, {})
      .then((res) => setInstance(res.data))
      .catch(() => setInstance(null));
  }, [id]);

  const fetchPatterns = useCallback(async () => {
    setPatternsLoading(true);
    setPatternsError(null);
    try {
      const res = await client.post(`/v1/instances/${id}/key-patterns`, {});
      setPatterns(res.data);
    } catch (err) {
      setPatternsError(toProblem(err, "Pattern dağılımı alınamadı."));
    } finally {
      setPatternsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı, sonrası manuel
    fetchPatterns();
  }, [fetchPatterns]);

  const fetchPage = useCallback(
    async (prefixValue, cursorValue, append, mode, mapNameValue) => {
      if (append) {
        setLoadingMore(true);
      } else {
        setLoading(true);
      }
      setError(null);

      try {
        const res = await client.post(`/v1/instances/${id}/keys`, {
          prefix: prefixValue,
          cursor: cursorValue,
          count: PAGE_SIZE,
          matchMode: mode,
          ...(mapNameValue ? { mapName: mapNameValue } : {}),
        });
        const page = res.data;

        setKeys((prev) => (append ? [...prev, ...page.content] : page.content));
        setCursor(page.cursor);
        setHasMore(page.hasMore);
        setKeysStatus(page.status);
      } catch (err) {
        setError(toProblem(err, "Key listesi alınamadı."));
      } finally {
        setLoading(false);
        setLoadingMore(false);
        setSearched(true);
      }
    },
    [id]
  );

  // Hazelcast'te önce map listesi (mapName boş) otomatik yüklenir; Redis'te kullanıcı arama yapmadan
  // (min 3 karakter) istek atılmaz, çünkü backend boş/kısa prefix'i 400 ile reddeder.
  useEffect(() => {
    if (!instance) return;
    if (instance.type === "HAZELCAST") {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
      fetchPage("", "0", false, matchMode);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- yalnızca instance yüklendiğinde bir kez çalışsın
  }, [instance]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    setTopLoading(true);
    client
      .post(`/v1/instances/${id}/keys/top`, { n: 10 })
      .then((res) => {
        const data = res.data;
        setTopKeys(data.keys ?? []);
        setTopMeta({
          estimated: data.estimated,
          scannedCount: data.scannedCount,
          scanLimit: data.scanLimit,
          scanCompleted: data.scanCompleted,
          status: data.status,
        });
      })
      .catch((err) => setTopError(toProblem(err, "En büyük key'ler alınamadı.")))
      .finally(() => setTopLoading(false));
  }, [id]);

  function handleSearch(e) {
    e.preventDefault();
    if (searchDisabled) return;
    setActivePrefix(trimmedPrefix);
    setCursor("0");
    fetchPage(trimmedPrefix, "0", false, matchMode, selectedMap);
  }

  function handleLoadMore() {
    fetchPage(activePrefix, cursor, true, matchMode, selectedMap);
  }

  function handlePatternClick(pattern) {
    const prefix = pattern.endsWith("*") ? pattern.slice(0, -1) : pattern;
    setPrefixInput(prefix);
    setActivePrefix(prefix);
    setCursor("0");
    fetchPage(prefix, "0", false, matchMode, selectedMap);
  }

  function handleSelectMap(mapName) {
    setSelectedMap(mapName);
    setPrefixInput("");
    setActivePrefix("");
    setMatchMode("STARTS_WITH");
    setCursor("0");
    setKeys([]);
    setSearched(false);
    fetchPage("", "0", false, "STARTS_WITH", mapName);
  }

  function handleBackToMapList() {
    setSelectedMap(null);
    setPrefixInput("");
    setActivePrefix("");
    setCursor("0");
    setKeys([]);
    setSearched(false);
    fetchPage("", "0", false, matchMode);
  }

  function renderSize(key) {
    const estimated = key.sizeStatus === "ESTIMATED";
    return (
      <>
        {formatBytes(key.sizeBytes)}
        {estimated && (
          <span title="yaklaşık değer" style={{ color: "var(--text-dim)", marginLeft: 4 }}>
            ~
          </span>
        )}
      </>
    );
  }

  function renderTtl(key) {
    const text = formatTtlByStatus(key.ttlMillis, key.ttlStatus);
    const badgeClass =
      key.ttlStatus === "PERSISTENT"
        ? "key-analysis-ttl-persistent"
        : key.ttlStatus === "NOT_FOUND"
          ? "key-analysis-ttl-notfound"
          : key.ttlStatus === "UNAVAILABLE"
            ? "key-analysis-ttl-unavailable"
            : "";
    return <span className={badgeClass ? `key-analysis-ttl-badge ${badgeClass}` : undefined}>{text}</span>;
  }

  const sortedPatterns = [...patterns].sort((a, b) => b.totalSizeBytes - a.totalSizeBytes);
  const totalPatternKeyCount = patterns.reduce((sum, p) => sum + p.keyCount, 0);
  const totalPatternSize = patterns.reduce((sum, p) => sum + p.totalSizeBytes, 0);

  const showEstimatedBanner = topMeta?.estimated && !topMeta?.scanCompleted;
  const mapTooLarge = hazelcastEntryMode && keysStatus === "NOT_APPLICABLE";

  return (
    <div className="key-analysis-page">
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, flexWrap: "wrap" }}>
        <h1 className="key-analysis-title">
          {isHazelcast ? "IMap Listesi" : "Key Analizi"} - {instanceName || `#${id}`}
        </h1>
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
          <ProblemError error={patternsError} className="key-analysis-error" />
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

      {isHazelcast && (
        <div className="key-analysis-stage-header">
          {hazelcastEntryMode ? (
            <>
              <button type="button" className="key-analysis-back-link" onClick={handleBackToMapList}>
                ← Map listesine dön
              </button>
              <span className="key-analysis-stage-label">Map: {selectedMap}</span>
            </>
          ) : (
            <span className="key-analysis-stage-label">Entry aramak için bir map seçin</span>
          )}
        </div>
      )}

      {(!isHazelcast || hazelcastEntryMode) && (
        <form className="key-analysis-search" onSubmit={handleSearch}>
          <div className="key-analysis-search-field">
            <div className="key-analysis-search-row">
              <input
                className="key-analysis-search-input"
                type="text"
                placeholder={
                  isHazelcast
                    ? "Entry key'i ile ara (opsiyonel)"
                    : "Key prefix'i ile ara (en az 3 karakter, örn: user:)"
                }
                value={prefixInput}
                onChange={(e) => setPrefixInput(e.target.value)}
              />
              <div className="key-analysis-matchmode-toggle">
                <button
                  type="button"
                  className={`key-analysis-matchmode-button ${matchMode === "STARTS_WITH" ? "active" : ""}`}
                  onClick={() => setMatchMode("STARTS_WITH")}
                >
                  Başlayan
                </button>
                <button
                  type="button"
                  className={`key-analysis-matchmode-button ${matchMode === "CONTAINS" ? "active" : ""}`}
                  onClick={() => setMatchMode("CONTAINS")}
                >
                  İçeren
                </button>
              </div>
              <button type="submit" className="key-analysis-search-button" disabled={searchDisabled}>
                Ara
              </button>
            </div>
            {searchDisabled && <div className="key-analysis-search-hint">En az 3 karakter girin</div>}
            {hasWildcard && (
              <div className="key-analysis-search-warning">Joker karakter desteklenmiyor</div>
            )}
          </div>
        </form>
      )}

      {loading && <div className="key-analysis-status">Yükleniyor...</div>}
      {error && <ProblemError error={error} className="key-analysis-error" />}

      {!isHazelcast && !loading && !error && !searched && (
        <div className="key-analysis-status">Aramak için en az 3 karakter girin ve Ara'ya tıklayın.</div>
      )}

      {!loading && !error && mapTooLarge && (
        <div className="key-analysis-info-banner">
          ⓘ Bu map arama için çok büyük (tarama sınırı aşıldı). Daha küçük bir map seçin veya sistem
          yöneticisiyle görüşün.
        </div>
      )}

      {!loading && !error && !mapTooLarge && (isHazelcast || searched) && (
        <>
          <table className="key-analysis-table">
            <thead>
              <tr>
                <th>{mapListMode ? "IMap Adı" : "Key Adı"}</th>
                {!mapListMode && <th>Tip</th>}
                <th>{mapListMode ? "Entry Sayısı" : "Boyut"}</th>
                {!mapListMode && <th>TTL</th>}
              </tr>
            </thead>
            <tbody>
              {keys.map((key) => (
                <tr
                  key={key.keyName}
                  className={mapListMode ? "key-analysis-row-clickable" : ""}
                  onClick={mapListMode ? () => handleSelectMap(key.keyName) : undefined}
                >
                  <td>{key.keyName}</td>
                  {!mapListMode && <td>{key.keyType}</td>}
                  <td>{mapListMode ? key.entryCount : renderSize(key)}</td>
                  {!mapListMode && <td>{renderTtl(key)}</td>}
                </tr>
              ))}
              {keys.length === 0 && (
                <tr>
                  <td colSpan={mapListMode ? 2 : 4} className="key-analysis-empty">
                    {keysStatus === "UNAVAILABLE" ? "Veri alınamadı." : "Sonuç bulunamadı."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          {hasMore && hazelcastEntryMode && (
            <div className="key-analysis-pagination-warning">
              Sayfalama sırası garanti edilmez, sonuçlar tekrarlanabilir. Daha spesifik sonuç için prefix girin.
            </div>
          )}

          {hasMore && !hazelcastEntryMode && (
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
        {topError && <ProblemError error={topError} className="key-analysis-error" />}

        {!topLoading && !topError && (
          <>
            {showEstimatedBanner && (
              <div className="key-analysis-estimated-banner">
                ⚠ Bu sonuç örnekleme ile üretildi ({topMeta.scannedCount} key tarandı). Tam liste değildir.
              </div>
            )}
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
                    <td>{isHazelcast ? key.sizeBytes : renderSize(key)}</td>
                    {!isHazelcast && <td>{renderTtl(key)}</td>}
                  </tr>
                ))}
                {topKeys.length === 0 && (
                  <tr>
                    <td colSpan={isHazelcast ? 2 : 4} className="key-analysis-empty">
                      {topMeta?.status === "UNAVAILABLE" ? "Veri alınamadı." : "Sonuç bulunamadı."}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </>
        )}
      </div>
    </div>
  );
}

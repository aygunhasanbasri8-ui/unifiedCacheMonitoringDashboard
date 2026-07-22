import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import client from "../api/client";
import { formatBytes, formatTtl } from "../utils/format";
import "./KeyAnalysis.css";

const PAGE_SIZE = 20;

export default function KeyAnalysis() {
  const { id } = useParams();

  const [instanceName, setInstanceName] = useState("");

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

  useEffect(() => {
    client
      .get(`/v1/instances/${id}`)
      .then((res) => setInstanceName(res.data.name))
      .catch(() => setInstanceName(""));
  }, [id]);

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

  return (
    <div className="key-analysis-page">
      <h1 className="key-analysis-title">Key Analizi - {instanceName || `#${id}`}</h1>

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

      {loading && <div className="key-analysis-status">Yükleniyor...</div>}
      {error && <div className="key-analysis-error">{error}</div>}

      {!loading && !error && (
        <>
          <table className="key-analysis-table">
            <thead>
              <tr>
                <th>Key Adı</th>
                <th>Tip</th>
                <th>Boyut</th>
                <th>TTL</th>
              </tr>
            </thead>
            <tbody>
              {keys.map((key) => (
                <tr key={key.keyName}>
                  <td>{key.keyName}</td>
                  <td>{key.keyType}</td>
                  <td>{formatBytes(key.sizeBytes)}</td>
                  <td>{formatTtl(key.ttlMillis)}</td>
                </tr>
              ))}
              {keys.length === 0 && (
                <tr>
                  <td colSpan={4} className="key-analysis-empty">
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
        <h2 className="key-analysis-section-title">En Büyük Key'ler</h2>

        {topLoading && <div className="key-analysis-status">Yükleniyor...</div>}
        {topError && <div className="key-analysis-error">{topError}</div>}

        {!topLoading && !topError && (
          <table className="key-analysis-table">
            <thead>
              <tr>
                <th>Key Adı</th>
                <th>Tip</th>
                <th>Boyut</th>
                <th>TTL</th>
              </tr>
            </thead>
            <tbody>
              {topKeys.map((key) => (
                <tr key={key.keyName}>
                  <td>{key.keyName}</td>
                  <td>{key.keyType}</td>
                  <td>{formatBytes(key.sizeBytes)}</td>
                  <td>{formatTtl(key.ttlMillis)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

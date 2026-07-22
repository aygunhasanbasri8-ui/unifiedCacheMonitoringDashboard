import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import client from "../api/client";
import { useAuth } from "../context/AuthContext";
import "./Sidebar.css";

export default function Sidebar({ isOpen, onClose }) {
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const [instances, setInstances] = useState([]);
  const [statuses, setStatuses] = useState({});

  useEffect(() => {
    client
      .get("/v1/instances")
      .then((res) => setInstances(res.data))
      .catch(() => setInstances([]));
  }, []);

  useEffect(() => {
    if (instances.length === 0) return;
    let cancelled = false;

    Promise.all(
      instances.map((instance) =>
        client
          .get(`/v1/instances/${instance.id}/health`)
          .then((res) => [instance.id, res.data.status])
          .catch(() => [instance.id, "DOWN"])
      )
    ).then((entries) => {
      if (!cancelled) setStatuses(Object.fromEntries(entries));
    });

    return () => {
      cancelled = true;
    };
  }, [instances]);

  function handleSelect(id) {
    navigate(`/instances/${id}`);
    onClose();
  }

  function handleAuditClick() {
    navigate("/audit");
    onClose();
  }

  function handleUsersClick() {
    navigate("/users");
    onClose();
  }

  return (
    <>
      {isOpen && <div className="sidebar-overlay" onClick={onClose} />}
      <aside className={`sidebar ${isOpen ? "sidebar-open" : ""}`}>
        <div className="sidebar-header">Instance&apos;lar</div>
        <div className="sidebar-list">
          {instances.map((instance) => {
            const status = statuses[instance.id];
            const dotClass =
              status === "UP"
                ? "sidebar-item-dot-up"
                : status === "DOWN"
                  ? "sidebar-item-dot-down"
                  : "sidebar-item-dot-unknown";

            return (
              <button
                key={instance.id}
                type="button"
                className="sidebar-item"
                onClick={() => handleSelect(instance.id)}
              >
                <span className={`sidebar-item-dot ${dotClass}`} />
                <span className="sidebar-item-name">{instance.name}</span>
                <span className={`sidebar-item-type sidebar-item-type-${instance.type?.toLowerCase()}`}>
                  {instance.type}
                </span>
              </button>
            );
          })}
          {instances.length === 0 && (
            <div className="sidebar-empty">Instance bulunamadı.</div>
          )}
        </div>

        {isAdmin && (
          <div className="sidebar-footer">
            <button type="button" className="sidebar-item" onClick={handleAuditClick}>
              <span className="sidebar-item-name">Denetim Kayıtları</span>
            </button>
            <button type="button" className="sidebar-item" onClick={handleUsersClick}>
              <span className="sidebar-item-name">Kullanıcı Yönetimi</span>
            </button>
          </div>
        )}
      </aside>
    </>
  );
}

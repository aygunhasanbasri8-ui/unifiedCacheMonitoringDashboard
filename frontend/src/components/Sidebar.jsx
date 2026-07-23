import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import client from "../api/client";
import { useAuth } from "../context/AuthContext";
import "./Sidebar.css";

const TYPE_ORDER = ["REDIS", "HAZELCAST"];

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

  function handleOverviewClick() {
    navigate("/overview");
    onClose();
  }

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

  function renderInstanceItem(instance) {
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
  }

  const groupedInstances = TYPE_ORDER.map((type) => ({
    type,
    items: instances.filter((i) => i.type === type),
  })).filter((group) => group.items.length > 0);

  const otherInstances = instances.filter((i) => !TYPE_ORDER.includes(i.type));

  return (
    <>
      {isOpen && <div className="sidebar-overlay" onClick={onClose} />}
      <aside className={`sidebar ${isOpen ? "sidebar-open" : ""}`}>
        <div className="sidebar-list">
          <button
            type="button"
            className="sidebar-item sidebar-item-overview"
            onClick={handleOverviewClick}
          >
            <span className="sidebar-item-name">Overview</span>
          </button>
        </div>

        <div className="sidebar-divider" />

        <div className="sidebar-header">Instance&apos;lar</div>
        <div className="sidebar-list">
          {instances.length === 0 && (
            <div className="sidebar-empty">Instance bulunamadı.</div>
          )}

          {groupedInstances.map((group) => (
            <div key={group.type} className="sidebar-group">
              <div className="sidebar-group-title">{group.type}</div>
              {group.items.map(renderInstanceItem)}
            </div>
          ))}

          {otherInstances.length > 0 && (
            <div className="sidebar-group">{otherInstances.map(renderInstanceItem)}</div>
          )}
        </div>

        {isAdmin && (
          <>
            <div className="sidebar-divider" />
            <div className="sidebar-footer">
              <button type="button" className="sidebar-item" onClick={handleAuditClick}>
                <span className="sidebar-item-name">Denetim Kayıtları</span>
              </button>
              <button type="button" className="sidebar-item" onClick={handleUsersClick}>
                <span className="sidebar-item-name">Kullanıcı Yönetimi</span>
              </button>
            </div>
          </>
        )}
      </aside>
    </>
  );
}

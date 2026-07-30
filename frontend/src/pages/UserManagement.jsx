import { useEffect, useState, useCallback } from "react";
import client from "../api/client";
import { useAuth } from "../context/AuthContext";
import { formatDateTime } from "../utils/format";
import "./UserManagement.css";

const PROTECTED_USERNAME = "monitor";

export default function UserManagement() {
  const { username: currentUsername } = useAuth();

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newRole, setNewRole] = useState("MONITOR");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createSuccess, setCreateSuccess] = useState("");

  const [deleteError, setDeleteError] = useState("");

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await client.get("/v1/users");
      setUsers(res.data);
    } catch {
      setError("Kullanıcı listesi alınamadı.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchUsers();
  }, [fetchUsers]);

  async function handleCreate(e) {
    e.preventDefault();
    if (!newUsername || !newPassword) return;

    setCreating(true);
    setCreateError("");
    setCreateSuccess("");

    try {
      await client.post("/v1/users", {
        username: newUsername,
        password: newPassword,
        role: newRole,
      });
      setCreateSuccess("Kullanıcı oluşturuldu");
      setNewUsername("");
      setNewPassword("");
      setNewRole("MONITOR");
      fetchUsers();
    } catch (err) {
      setCreateError(
        err.response?.data?.detail ?? err.response?.data?.error ?? "Kullanıcı oluşturulamadı."
      );
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(username) {
    if (!window.confirm(`${username} kullanıcısını silmek istediğinize emin misiniz?`)) {
      return;
    }

    setDeleteError("");
    try {
      await client.delete(`/v1/users/${username}`);
      fetchUsers();
    } catch (err) {
      setDeleteError(
        err.response?.data?.detail ?? err.response?.data?.error ?? "Kullanıcı silinemedi."
      );
    }
  }

  return (
    <div className="user-management-page">
      <h1 className="user-management-title">Kullanıcı Yönetimi</h1>

      <div className="user-management-form-card">
        <h2 className="user-management-section-title">Yeni Kullanıcı Oluştur</h2>

        {createSuccess && <div className="user-management-success">{createSuccess}</div>}
        {createError && <div className="user-management-error">{createError}</div>}

        <form className="user-management-form" onSubmit={handleCreate}>
          <div className="user-management-field">
            <label htmlFor="new-username">Kullanıcı Adı</label>
            <input
              id="new-username"
              type="text"
              value={newUsername}
              onChange={(e) => setNewUsername(e.target.value)}
              required
            />
          </div>
          <div className="user-management-field">
            <label htmlFor="new-password">Şifre</label>
            <input
              id="new-password"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
          </div>
          <div className="user-management-field">
            <label htmlFor="new-role">Rol</label>
            <select
              id="new-role"
              value={newRole}
              onChange={(e) => setNewRole(e.target.value)}
            >
              <option value="MONITOR">MONITOR</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>
          <button
            type="submit"
            className="user-management-create-button"
            disabled={creating || !newUsername || !newPassword}
          >
            {creating ? "Oluşturuluyor..." : "Oluştur"}
          </button>
        </form>
      </div>

      {deleteError && <div className="user-management-error">{deleteError}</div>}

      {loading && <div className="user-management-status">Yükleniyor...</div>}
      {error && <div className="user-management-error">{error}</div>}

      {!loading && !error && (
        <table className="user-management-table">
          <thead>
            <tr>
              <th>Kullanıcı Adı</th>
              <th>Rol</th>
              <th>Durum</th>
              <th>Oluşturulma</th>
              <th>Son Giriş</th>
              <th>İşlem</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => {
              const canDelete =
                user.username !== PROTECTED_USERNAME && user.username !== currentUsername;

              return (
                <tr key={user.id}>
                  <td>{user.username}</td>
                  <td>
                    <span
                      className={`user-management-role user-management-role-${user.role?.toLowerCase()}`}
                    >
                      {user.role}
                    </span>
                  </td>
                  <td className={user.active ? "user-management-active" : "user-management-inactive"}>
                    {user.active ? "Aktif" : "Pasif"}
                  </td>
                  <td>{formatDateTime(user.createdAt)}</td>
                  <td>{user.lastLoginAt ? formatDateTime(user.lastLoginAt) : "Hiç"}</td>
                  <td>
                    {canDelete ? (
                      <button
                        type="button"
                        className="user-management-delete-button"
                        onClick={() => handleDelete(user.username)}
                      >
                        Sil
                      </button>
                    ) : (
                      <span className="user-management-delete-disabled">-</span>
                    )}
                  </td>
                </tr>
              );
            })}
            {users.length === 0 && (
              <tr>
                <td colSpan={6} className="user-management-empty">
                  Kullanıcı bulunamadı.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}

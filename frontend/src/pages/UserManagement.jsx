import { useEffect, useState, useCallback } from "react";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import ProblemError from "../components/ProblemError";
import { useAuth } from "../context/AuthContext";
import { formatDateTime } from "../utils/format";
import "./UserManagement.css";

const PROTECTED_USERNAME = "monitor";

export default function UserManagement() {
  const { username: currentUsername } = useAuth();

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newRole, setNewRole] = useState("MONITOR");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [createSuccess, setCreateSuccess] = useState("");

  const [deleteError, setDeleteError] = useState(null);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await client.get("/v1/admin/users");
      setUsers(res.data);
    } catch (err) {
      setError(toProblem(err, "Kullanıcı listesi alınamadı."));
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
    setCreateError(null);
    setCreateSuccess("");

    try {
      await client.post("/v1/admin/users", {
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
      setCreateError(toProblem(err, "Kullanıcı oluşturulamadı."));
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(username) {
    if (!window.confirm(`${username} kullanıcısını silmek istediğinize emin misiniz?`)) {
      return;
    }

    setDeleteError(null);
    try {
      await client.delete(`/v1/admin/users/${username}`);
      fetchUsers();
    } catch (err) {
      setDeleteError(toProblem(err, "Kullanıcı silinemedi."));
    }
  }

  return (
    <div className="user-management-page">
      <h1 className="user-management-title">Kullanıcı Yönetimi</h1>

      <div className="user-management-form-card">
        <h2 className="user-management-section-title">Yeni Kullanıcı Oluştur</h2>

        {createSuccess && <div className="user-management-success">{createSuccess}</div>}
        {createError && <ProblemError error={createError} className="user-management-error" />}

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

      {deleteError && <ProblemError error={deleteError} className="user-management-error" />}

      {loading && <div className="user-management-status">Yükleniyor...</div>}
      {error && <ProblemError error={error} className="user-management-error" />}

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

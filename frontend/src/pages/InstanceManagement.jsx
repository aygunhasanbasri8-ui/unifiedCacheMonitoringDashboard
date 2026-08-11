import { useEffect, useState, useCallback } from "react";
import client from "../api/client";
import { toProblem } from "../api/problemDetails";
import ProblemError from "../components/ProblemError";
import "./InstanceManagement.css";

const EMPTY_FORM = {
  name: "",
  type: "REDIS",
  host: "",
  port: "",
  enabled: true,
  clusterName: "",
  members: "",
  metricsUrl: "",
  username: "",
  passwordEnvVar: "",
  tlsEnabled: false,
};

export default function InstanceManagement() {
  const [instances, setInstances] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);
  const [saveSuccess, setSaveSuccess] = useState("");

  const [deleteError, setDeleteError] = useState(null);

  const fetchInstances = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await client.post("/v1/instances/list", {});
      setInstances(res.data);
    } catch (err) {
      setError(toProblem(err, "Instance listesi alınamadı."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- ilk veri çekimi için kasıtlı
    fetchInstances();
  }, [fetchInstances]);

  function updateField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function resetForm() {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setFormError(null);
  }

  function handleEdit(instance) {
    setEditingId(instance.id);
    setForm({
      name: instance.name ?? "",
      type: instance.type ?? "REDIS",
      host: instance.host ?? "",
      port: instance.port != null ? String(instance.port) : "",
      enabled: instance.enabled ?? true,
      clusterName: instance.clusterName ?? "",
      members: instance.members ?? "",
      metricsUrl: instance.metricsUrl ?? "",
      username: instance.username ?? "",
      passwordEnvVar: instance.passwordEnvVar ?? "",
      tlsEnabled: instance.tlsEnabled ?? false,
    });
    setFormError(null);
    setSaveSuccess("");
  }

  function validateForm() {
    if (!form.name.trim()) return "Ad alanı zorunlu.";
    if (!form.host.trim()) return "Host alanı zorunlu.";

    const port = Number(form.port);
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      return "Port 1-65535 aralığında bir tam sayı olmalı.";
    }

    if (form.type === "HAZELCAST" && !form.clusterName.trim()) {
      return "Hazelcast instance'ları için Cluster Adı zorunlu.";
    }

    return null;
  }

  function buildPayload() {
    const isHazelcast = form.type === "HAZELCAST";
    const isRedis = form.type === "REDIS";

    return {
      id: editingId,
      name: form.name.trim(),
      type: form.type,
      host: form.host.trim(),
      port: Number(form.port),
      enabled: form.enabled,
      clusterName: isHazelcast ? form.clusterName.trim() : null,
      members: isHazelcast ? form.members.trim() || null : null,
      metricsUrl: isHazelcast ? form.metricsUrl.trim() || null : null,
      username: isRedis ? form.username.trim() || null : null,
      passwordEnvVar: isRedis ? form.passwordEnvVar.trim() || null : null,
      tlsEnabled: isRedis ? form.tlsEnabled : false,
    };
  }

  async function handleSubmit(e) {
    e.preventDefault();

    const validationMessage = validateForm();
    if (validationMessage) {
      setFormError(validationMessage);
      return;
    }

    setSaving(true);
    setFormError(null);
    setSaveSuccess("");

    const payload = buildPayload();

    try {
      if (editingId) {
        await client.put(`/v1/admin/instances/${editingId}`, payload);
        setSaveSuccess("Instance güncellendi");
      } else {
        await client.post("/v1/admin/instances", payload);
        setSaveSuccess("Instance oluşturuldu");
      }
      resetForm();
      fetchInstances();
    } catch (err) {
      setFormError(toProblem(err, editingId ? "Instance güncellenemedi." : "Instance oluşturulamadı."));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(instance) {
    if (
      !window.confirm(
        `${instance.name} instance'ını silmek istediğinize emin misiniz?\n\nYalnızca UCMD'deki izleme kaydı silinir, izlenen sisteme dokunulmaz.`
      )
    ) {
      return;
    }

    setDeleteError(null);
    try {
      await client.delete(`/v1/admin/instances/${instance.id}`);
      if (editingId === instance.id) resetForm();
      fetchInstances();
    } catch (err) {
      setDeleteError(toProblem(err, "Instance silinemedi."));
    }
  }

  return (
    <div className="instance-management-page">
      <h1 className="instance-management-title">Instance Yönetimi</h1>

      <div className="instance-management-form-card">
        <h2 className="instance-management-section-title">
          {editingId ? "Instance Düzenle" : "Yeni Instance Oluştur"}
        </h2>

        {saveSuccess && <div className="instance-management-success">{saveSuccess}</div>}
        {formError && <ProblemError error={formError} className="instance-management-error" />}

        <form className="instance-management-form" onSubmit={handleSubmit}>
          <div className="instance-management-field">
            <label htmlFor="inst-name">Ad</label>
            <input
              id="inst-name"
              type="text"
              value={form.name}
              onChange={(e) => updateField("name", e.target.value)}
              required
            />
          </div>

          <div className="instance-management-field">
            <label htmlFor="inst-type">Tip</label>
            <select
              id="inst-type"
              value={form.type}
              onChange={(e) => updateField("type", e.target.value)}
            >
              <option value="REDIS">REDIS</option>
              <option value="HAZELCAST">HAZELCAST</option>
            </select>
          </div>

          <div className="instance-management-field">
            <label htmlFor="inst-host">Host</label>
            <input
              id="inst-host"
              type="text"
              value={form.host}
              onChange={(e) => updateField("host", e.target.value)}
              required
            />
          </div>

          <div className="instance-management-field">
            <label htmlFor="inst-port">Port</label>
            <input
              id="inst-port"
              type="number"
              min={1}
              max={65535}
              value={form.port}
              onChange={(e) => updateField("port", e.target.value)}
              required
            />
          </div>

          <div className="instance-management-field instance-management-field-checkbox">
            <label htmlFor="inst-enabled">Aktif</label>
            <input
              id="inst-enabled"
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => updateField("enabled", e.target.checked)}
            />
          </div>

          {form.type === "HAZELCAST" && (
            <>
              <div className="instance-management-field">
                <label htmlFor="inst-cluster-name">Cluster Adı</label>
                <input
                  id="inst-cluster-name"
                  type="text"
                  value={form.clusterName}
                  onChange={(e) => updateField("clusterName", e.target.value)}
                  required
                />
              </div>

              <div className="instance-management-field">
                <label htmlFor="inst-members">Members</label>
                <input
                  id="inst-members"
                  type="text"
                  placeholder="host1:5701,host2:5701"
                  value={form.members}
                  onChange={(e) => updateField("members", e.target.value)}
                />
              </div>

              <div className="instance-management-field">
                <label htmlFor="inst-metrics-url">Metrics URL</label>
                <input
                  id="inst-metrics-url"
                  type="text"
                  placeholder="http://host1:9999/metrics,http://host2:9999/metrics"
                  value={form.metricsUrl}
                  onChange={(e) => updateField("metricsUrl", e.target.value)}
                />
              </div>
            </>
          )}

          {form.type === "REDIS" && (
            <>
              <div className="instance-management-field">
                <label htmlFor="inst-username">Kullanıcı Adı (ACL)</label>
                <input
                  id="inst-username"
                  type="text"
                  value={form.username}
                  onChange={(e) => updateField("username", e.target.value)}
                />
              </div>

              <div className="instance-management-field">
                <label htmlFor="inst-password-env-var">Şifre Env Değişkeni</label>
                <input
                  id="inst-password-env-var"
                  type="text"
                  placeholder="ör. REDIS_PROD_PASSWORD"
                  value={form.passwordEnvVar}
                  onChange={(e) => updateField("passwordEnvVar", e.target.value)}
                />
                <span className="instance-management-warning">
                  Buraya parolanın kendisini değil, parolanın okunacağı environment
                  değişkeninin adını yazın. Gerçek parola girmek güvenlik ihlalidir.
                </span>
              </div>

              <div className="instance-management-checkbox">
                <label htmlFor="inst-tls-enabled">
                  <input
                    id="inst-tls-enabled"
                    type="checkbox"
                    checked={form.tlsEnabled}
                    onChange={(e) => updateField("tlsEnabled", e.target.checked)}
                  />
                  TLS ile bağlan
                </label>
                <span className="instance-management-help">
                  Redis sunucusunda tls-port açık olmalı ve sertifikası JVM
                  truststore&apos;una tanıtılmış olmalıdır.
                </span>
              </div>
            </>
          )}

          <div className="instance-management-form-actions">
            <button type="submit" className="instance-management-create-button" disabled={saving}>
              {saving ? "Kaydediliyor..." : editingId ? "Güncelle" : "Oluştur"}
            </button>
            {editingId && (
              <button
                type="button"
                className="instance-management-cancel-button"
                onClick={resetForm}
                disabled={saving}
              >
                İptal
              </button>
            )}
          </div>
        </form>
      </div>

      {deleteError && <ProblemError error={deleteError} className="instance-management-error" />}

      {loading && <div className="instance-management-status">Yükleniyor...</div>}
      {error && <ProblemError error={error} className="instance-management-error" />}

      {!loading && !error && (
        <table className="instance-management-table">
          <thead>
            <tr>
              <th>Ad</th>
              <th>Tip</th>
              <th>Host:Port</th>
              <th>Cluster</th>
              <th>Durum</th>
              <th>İşlem</th>
            </tr>
          </thead>
          <tbody>
            {instances.map((instance) => (
              <tr key={instance.id}>
                <td>{instance.name}</td>
                <td>
                  <span
                    className={`instance-management-type instance-management-type-${instance.type?.toLowerCase()}`}
                  >
                    {instance.type}
                  </span>
                </td>
                <td>
                  {instance.host}:{instance.port}
                </td>
                <td>{instance.clusterName || "-"}</td>
                <td
                  className={
                    instance.enabled ? "instance-management-active" : "instance-management-inactive"
                  }
                >
                  {instance.enabled ? "Aktif" : "Pasif"}
                </td>
                <td className="instance-management-actions">
                  <button
                    type="button"
                    className="instance-management-edit-button"
                    onClick={() => handleEdit(instance)}
                  >
                    Düzenle
                  </button>
                  <button
                    type="button"
                    className="instance-management-delete-button"
                    onClick={() => handleDelete(instance)}
                  >
                    Sil
                  </button>
                </td>
              </tr>
            ))}
            {instances.length === 0 && (
              <tr>
                <td colSpan={6} className="instance-management-empty">
                  Instance bulunamadı.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import ProblemError from "../components/ProblemError";
import "./Login.css";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const result = await login(username, password);

    setLoading(false);

    if (result.success) {
      navigate("/overview");
    } else {
      setError({
        message: result.problem?.message ?? "Kullanıcı adı veya şifre hatalı",
        traceId: result.problem?.traceId ?? null,
      });
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1 className="login-title">UCMD</h1>
        <p className="login-subtitle">Yönetim paneline giriş yapın</p>

        {error && <ProblemError error={error} className="login-error" />}

        <label className="login-label" htmlFor="username">
          Kullanıcı adı
        </label>
        <input
          id="username"
          className="login-input"
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />

        <label className="login-label" htmlFor="password">
          Şifre
        </label>
        <input
          id="password"
          className="login-input"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />

        <button type="submit" className="login-button" disabled={loading}>
          {loading ? "Giriş yapılıyor..." : "Giriş Yap"}
        </button>
      </form>
    </div>
  );
}

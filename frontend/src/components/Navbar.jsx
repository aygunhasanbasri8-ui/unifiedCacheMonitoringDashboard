import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./Navbar.css";

export default function Navbar({ onToggleDrawer }) {
  const { username, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const showBack = location.pathname !== "/overview";

  async function handleLogout() {
    await logout();
    navigate("/login");
  }

  return (
    <header className="navbar">
      <div className="navbar-left">
        <button
          type="button"
          className="navbar-hamburger"
          onClick={onToggleDrawer}
          aria-label="Menüyü aç/kapa"
        >
          ☰
        </button>
        {showBack && (
          <button type="button" className="navbar-back" onClick={() => navigate(-1)}>
            ← Geri
          </button>
        )}
        <Link to="/overview" className="navbar-brand">
          UCMD
        </Link>
      </div>
      <nav className="navbar-links">
        <Link to="/overview">Overview</Link>
        {isAdmin && <Link to="/audit">Denetim Kayıtları</Link>}
        {isAdmin && <Link to="/users">Kullanıcı Yönetimi</Link>}
      </nav>
      <div className="navbar-user">
        {username && <span className="navbar-username">{username}</span>}
        <button type="button" className="navbar-logout" onClick={handleLogout}>
          Çıkış Yap
        </button>
      </div>
    </header>
  );
}

import { createContext, useContext, useState, useCallback } from "react";
import client from "../api/client";

const AuthContext = createContext(null);

const TOKEN_KEY = "ucmd_token";
const USERNAME_KEY = "ucmd_username";
const ROLE_KEY = "ucmd_role";

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [username, setUsername] = useState(() => localStorage.getItem(USERNAME_KEY));
  const [role, setRole] = useState(() => localStorage.getItem(ROLE_KEY));

  const login = useCallback(async (username, password) => {
    try {
      const res = await client.post("/v1/auth/login", { username, password });
      const data = res.data;
      localStorage.setItem(TOKEN_KEY, data.token);
      localStorage.setItem(USERNAME_KEY, data.username);
      localStorage.setItem(ROLE_KEY, data.role);
      setToken(data.token);
      setUsername(data.username);
      setRole(data.role);
      return { success: true };
    } catch (err) {
      return { success: false, problem: err.problem };
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await client.post("/v1/auth/logout");
    } catch {
      // logout isteği başarısız olsa bile oturumu yerelde temizlemeye devam et
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(ROLE_KEY);
    setToken(null);
    setUsername(null);
    setRole(null);
  }, []);

  const value = {
    token,
    username,
    role,
    isAuthenticated: Boolean(token),
    isAdmin: role === "ADMIN",
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}

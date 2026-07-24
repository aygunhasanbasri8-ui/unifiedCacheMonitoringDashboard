import { useState } from "react";
import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import Navbar from "./components/Navbar";
import Sidebar from "./components/Sidebar";
import Login from "./pages/Login";
import Overview from "./pages/Overview";
import InstanceDetail from "./pages/InstanceDetail";
import KeyAnalysis from "./pages/KeyAnalysis";
import CacheLoader from "./pages/CacheLoader";
import AuditLog from "./pages/AuditLog";
import UserManagement from "./pages/UserManagement";

function AppLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <>
      <Navbar onToggleDrawer={() => setDrawerOpen((open) => !open)} />
      <Sidebar isOpen={drawerOpen} onClose={() => setDrawerOpen(false)} />
      <Outlet />
    </>
  );
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />

          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/overview" element={<Overview />} />
            <Route path="/instances/:id" element={<InstanceDetail />} />
            <Route path="/instances/:id/keys" element={<KeyAnalysis />} />
            <Route path="/cacheloader" element={<CacheLoader />} />
            <Route
              path="/audit"
              element={
                <ProtectedRoute requireAdmin>
                  <AuditLog />
                </ProtectedRoute>
              }
            />
            <Route
              path="/users"
              element={
                <ProtectedRoute requireAdmin>
                  <UserManagement />
                </ProtectedRoute>
              }
            />
          </Route>

          <Route path="/" element={<Navigate to="/overview" replace />} />
          <Route path="*" element={<Navigate to="/overview" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;

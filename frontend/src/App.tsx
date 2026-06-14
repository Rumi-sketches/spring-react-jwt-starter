import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LoginPage } from './features/auth/LoginPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { UnauthorizedPage } from './features/auth/UnauthorizedPage';

/**
 * Application root — defines the client-side route tree.
 *
 * Route structure:
 * - /login          → public, always accessible
 * - /unauthorized   → shown when a user lacks the required role
 * - /dashboard      → requires any authenticated user (wrapped in ProtectedRoute)
 * - *               → catch-all redirects to /dashboard
 *
 * `ProtectedRoute` acts as a layout route: it checks auth/role before rendering
 * its children via `<Outlet />`. Nesting protected pages inside it keeps the
 * guard logic in one place rather than duplicated in every page component.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/unauthorized" element={<UnauthorizedPage />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<DashboardPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

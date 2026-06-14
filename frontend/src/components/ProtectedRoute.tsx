import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';

interface ProtectedRouteProps {
  /** When provided, only users whose role is in this list are allowed through. */
  allowedRoles?: string[];
}

/**
 * Route guard that enforces authentication and optional role-based access control.
 *
 * Usage in the router:
 * ```tsx
 * // Any authenticated user
 * <Route element={<ProtectedRoute />}>
 *   <Route path="/dashboard" element={<DashboardPage />} />
 * </Route>
 *
 * // ADMIN only
 * <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
 *   <Route path="/admin" element={<AdminPage />} />
 * </Route>
 * ```
 *
 * Decision tree:
 * 1. Still loading (silent refresh in progress) → show a spinner.
 *    Without this, a logged-in user who refreshes the page would see a flash of
 *    the login page before the token is restored.
 * 2. Not authenticated → redirect to /login.
 * 3. Authenticated but wrong role → redirect to /unauthorized.
 * 4. Authenticated and authorized → render the child route via `<Outlet />`.
 */
export function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-gray-500 text-sm">Loading...</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />;
  }

  // `Outlet` renders whichever child route matched — the actual page component
  return <Outlet />;
}

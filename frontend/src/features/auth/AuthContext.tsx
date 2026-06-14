import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
} from 'react';
import type { ReactNode } from 'react';
import { jwtDecode } from 'jwt-decode';
import { login as apiLogin, logout as apiLogout, refreshToken } from './auth.api';
import { setTokenGetter, setAuthCallbacks } from '../../lib/axios';
import type { LoginRequest } from './auth.api';

interface AuthUser {
  email: string;
  role: string;
}

/** Shape of the decoded JWT payload we care about. */
interface JwtPayload {
  sub: string;   // email
  role: string;
  iat: number;   // issued-at (Unix seconds)
  exp: number;   // expires-at (Unix seconds)
}

interface AuthContextType {
  user: AuthUser | null;
  isLoading: boolean;
  tokenExpiresAt: Date | null;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

/**
 * Global authentication state provider.
 *
 * Wrap your entire application (or at least the authenticated portion) with this
 * provider so any component can call `useAuth()` to read or update auth state.
 *
 * Responsibilities:
 * - Hold the access token in a React state variable (memory only — never localStorage).
 * - Expose `login` and `logout` actions to child components.
 * - Wire up the Axios instance so it can read the current token and trigger logout.
 * - Perform a silent token refresh on page load so the user stays logged in across
 *   browser refreshes (the refresh-token cookie survives a page reload; the in-memory
 *   access token does not).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [tokenExpiresAt, setTokenExpiresAt] = useState<Date | null>(null);

  /**
   * Decodes the JWT and updates all auth-related state in one shot.
   * Called after every successful login or token refresh.
   *
   * Note: `jwtDecode` does NOT verify the signature — it simply Base64-decodes the
   * payload. Signature verification happens on the server for every API request.
   * We use the decoded payload here only to read user info and the expiry timestamp.
   */
  function applyToken(token: string) {
    const payload = jwtDecode<JwtPayload>(token);
    setAccessToken(token);
    setUser({ email: payload.sub, role: payload.role });
    setTokenExpiresAt(new Date(payload.exp * 1000)); // exp is in seconds, Date expects ms
  }

  /** Clears all auth state — called on logout or when a refresh attempt fails. */
  function clearAuth() {
    setAccessToken(null);
    setUser(null);
    setTokenExpiresAt(null);
  }

  const handleLogout = useCallback(async () => {
    try {
      await apiLogout(); // tell the server to revoke the refresh token
    } catch {
      // Ignore errors (e.g. token already expired) — always clear local state
    } finally {
      clearAuth();
    }
  }, []);

  /**
   * Register callbacks on the Axios instance so the 401 interceptor can call back
   * into React state without creating a circular dependency.
   * Re-registers whenever `handleLogout` identity changes (i.e. on mount only).
   */
  useEffect(() => {
    setAuthCallbacks({
      onTokenRefreshed: applyToken,
      onLogout: handleLogout,
    });
  }, [handleLogout]);

  /**
   * Keep the Axios request interceptor in sync with the current access token.
   * Every time the token changes (login, refresh, logout), the interceptor getter
   * is updated so subsequent requests use the latest value.
   */
  useEffect(() => {
    setTokenGetter(() => accessToken);
  }, [accessToken]);

  /**
   * Silent refresh on page load.
   *
   * When the user refreshes the browser, the in-memory access token is gone but the
   * httpOnly cookie is still present. This effect runs once on mount and attempts a
   * token refresh to restore the session transparently.
   *
   * `isLoading` stays `true` until this resolves so that `ProtectedRoute` does not
   * redirect to /login before we know whether the user is actually authenticated.
   */
  useEffect(() => {
    refreshToken()
      .then((data) => applyToken(data.accessToken))
      .catch(() => clearAuth())
      .finally(() => setIsLoading(false));
  }, []);

  /** Calls the login API and stores the returned access token in state. */
  const handleLogin = async (credentials: LoginRequest): Promise<void> => {
    const data = await apiLogin(credentials);
    applyToken(data.accessToken);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        tokenExpiresAt,
        login: handleLogin,
        logout: handleLogout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Convenience hook for consuming auth state in any component.
 *
 * Throws if used outside of an `AuthProvider` — this is intentional so the error
 * surfaces early during development rather than silently returning null.
 */
export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}

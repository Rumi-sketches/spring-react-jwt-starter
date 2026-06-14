import axios from 'axios';

/**
 * Shared Axios instance used for all API calls in the application.
 *
 * Using a single instance instead of calling `axios` directly everywhere means:
 * - The base URL and credentials flag are configured in one place.
 * - Request and response interceptors apply automatically to every call.
 *
 * `withCredentials: true` instructs the browser to include httpOnly cookies
 * (the refresh token) on every request, even though the frontend runs on a
 * different port than the backend in development.
 */
export const api = axios.create({
  baseURL: '/api/v1',       // Vite proxies this prefix to localhost:8080
  withCredentials: true,    // required to send the httpOnly refresh-token cookie
});

// ---------------------------------------------------------------------------
// Request interceptor — attach the access token to every outgoing request
// ---------------------------------------------------------------------------

/**
 * The current access token is stored here by AuthContext via `setTokenGetter`.
 *
 * Why a getter function instead of a plain variable?
 * The interceptor closure captures `getAccessToken` once at registration time.
 * If we stored the token directly, we would need to re-register the interceptor
 * on every token change. A getter function always reads the latest value.
 */
let getAccessToken: () => string | null = () => null;

/**
 * Called by AuthContext to wire up the token source.
 * The provided function is invoked on every request to read the current token.
 */
export function setTokenGetter(fn: () => string | null) {
  getAccessToken = fn;
}

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ---------------------------------------------------------------------------
// Response interceptor — transparent token refresh on 401
// ---------------------------------------------------------------------------

/**
 * Callbacks registered by AuthContext so the interceptor can update app state
 * (store the new token, or trigger a logout) without importing React context directly.
 * This keeps the Axios module free of React dependencies.
 */
let onLogout: () => void = () => {};
let onTokenRefreshed: (token: string) => void = () => {};

/** Called by AuthContext once on mount to wire up the logout and refresh callbacks. */
export function setAuthCallbacks(callbacks: {
  onLogout: () => void;
  onTokenRefreshed: (token: string) => void;
}) {
  onLogout = callbacks.onLogout;
  onTokenRefreshed = callbacks.onTokenRefreshed;
}

/**
 * On a 401 response (expired or missing access token), this interceptor:
 * 1. Calls POST /auth/refresh — the browser sends the httpOnly cookie automatically.
 * 2. Updates the in-memory access token via `onTokenRefreshed`.
 * 3. Retries the original failed request with the new token.
 *
 * The `_retry` flag prevents an infinite loop: if the refresh call itself returns
 * 401 (e.g. the refresh token is also expired), we give up and call `onLogout`
 * instead of retrying again.
 *
 * Note: the refresh call uses the bare `axios` instance, not `api`, to avoid
 * triggering this same interceptor recursively.
 */
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;

    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;

      try {
        const { data } = await axios.post(
          '/api/v1/auth/refresh',
          {},
          { withCredentials: true }
        );

        onTokenRefreshed(data.accessToken);
        original.headers.Authorization = `Bearer ${data.accessToken}`;
        return api(original); // retry the original request with the new token
      } catch {
        onLogout(); // refresh failed (e.g. cookie expired) — force logout
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  }
);

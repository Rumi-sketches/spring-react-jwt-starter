import { api } from '../../lib/axios';
import axios from 'axios';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Sends credentials to POST /auth/login.
 *
 * On success the backend responds with:
 * - Body: `{ accessToken }` — stored in React state (never in localStorage).
 * - Set-Cookie: the httpOnly refresh-token cookie — managed by the browser automatically.
 *
 * Uses the shared `api` instance so the request interceptor adds any existing
 * Authorization header (though it will be absent on the first login).
 */
export async function login(credentials: LoginRequest): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', credentials);
  return data;
}

/**
 * Sends POST /auth/refresh to obtain a new access token.
 *
 * The browser sends the httpOnly refresh-token cookie automatically — this
 * function does not need to read or forward it.
 *
 * This deliberately uses the bare `axios` instance instead of the shared `api`
 * instance to avoid triggering the 401 response interceptor. If this call itself
 * returns 401 (e.g. the refresh token is expired), we want to propagate the error
 * to the caller — not loop back into another refresh attempt.
 */
export async function refreshToken(): Promise<AuthResponse> {
  const { data } = await axios.post<AuthResponse>(
    '/api/v1/auth/refresh',
    {},
    { withCredentials: true }
  );
  return data;
}

/**
 * Sends POST /auth/logout to revoke the current refresh token on the server
 * and clear the httpOnly cookie.
 *
 * The access token is not explicitly invalidated — it expires naturally within
 * 15 minutes. The caller (AuthContext) is responsible for discarding it from
 * memory immediately after this call completes.
 */
export async function logout(): Promise<void> {
  await api.post('/auth/logout');
}

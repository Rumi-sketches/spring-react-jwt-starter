# spring-react-jwt-starter

Hi, I'm Rumi, a mid-level developer who works mostly with Java and Spring Boot. I've been wanting to start building my own projects and rewriting some of the mockups and prototypes I've come across at work, as a hobby but also to keep my brain sharp.

JWT authentication is something I keep seeing and working on but never actually built from scratch. With the help of *AI* (which I believe is a viable and very useful tool for learning and building, if not used to delegate the writing of code you don't understand), I put together this implementation and I'm sharing it for anyone who wants to understand how it works, not just prompt it out.

---

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security 6 · PostgreSQL 16 · React 19 · Vite · TypeScript

---

## Quick start

The only requirement is [Docker Desktop](https://www.docker.com/products/docker-desktop/). Everything else runs inside containers.

```bash
git clone https://github.com/Rumi-sketches/spring-react-jwt-starter
cd spring-react-jwt-starter
docker compose up
```

Open [http://localhost:5173](http://localhost:5173)

### Default users

| Email | Password | Role |
|---|---|---|
| `admin@demo.com` | `password` | ADMIN |
| `user@demo.com` | `password` | USER |

---

## Running locally (without Docker)

Useful when you're actively modifying the code and want faster restarts.

```bash
# Start only PostgreSQL in a container
docker compose up postgres -d

# Backend
cd backend
./mvnw spring-boot:run   # Windows: .\mvnw.cmd spring-boot:run

# Frontend (new terminal)
cd frontend
npm run dev
```

---

## API reference

All endpoints are prefixed with `/api/v1`.

### Public endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Authenticate and receive tokens |
| `POST` | `/auth/refresh` | Refresh the access token (uses httpOnly cookie) |
| `POST` | `/auth/logout` | Revoke current session |

**Login request:**
```json
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "admin@demo.com",
  "password": "password"
}
```

**Login response:**
```json
HTTP 200 OK
Set-Cookie: refresh_token=...; HttpOnly; SameSite=Strict; Path=/api/v1/auth

{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer"
}
```

### Protected endpoints

Include the access token in every request:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

| Method | Path | Role required | Description |
|---|---|---|---|
| `GET` | `/api/v1/users/me` | Any authenticated | Current user info |

**Current user response:**
```json
HTTP 200 OK

{
  "email": "admin@demo.com",
  "role": "ADMIN"
}
```

---

## Project structure

```
spring-react-jwt-starter/
├── docker-compose.yml
├── backend/
│   └── src/main/java/com/jwtdemo/
│       ├── auth/
│       │   ├── AuthController.java        ← HTTP endpoints
│       │   ├── AuthService.java           ← login, refresh, logout logic
│       │   ├── JwtService.java            ← generate and validate tokens
│       │   ├── RefreshToken.java          ← DB entity
│       │   ├── RefreshTokenRepository.java
│       │   └── dto/
│       │       ├── LoginRequest.java
│       │       └── AuthResponse.java
│       ├── users/
│       │   ├── User.java                  ← JPA entity + UserDetails
│       │   ├── UserRole.java              ← enum: ADMIN, USER
│       │   ├── UserRepository.java
│       │   ├── UserDetailsServiceImpl.java ← bridge to Spring Security
│       │   └── UsersController.java       ← GET /users/me
│       └── common/
│           ├── security/
│           │   ├── SecurityConfig.java    ← filter chain, CORS, rules
│           │   └── JwtAuthFilter.java     ← reads token from every request
│           └── exceptions/
│               ├── GlobalExceptionHandler.java
│               └── UnauthorizedException.java
└── frontend/
    └── src/
        ├── features/
        │   ├── auth/
        │   │   ├── AuthContext.tsx        ← global auth state
        │   │   ├── LoginPage.tsx          ← login form
        │   │   ├── UnauthorizedPage.tsx   ← shown on missing role
        │   │   └── auth.api.ts            ← API calls
        │   └── dashboard/
        │       └── DashboardPage.tsx      ← user dashboard
        ├── components/
        │   └── ProtectedRoute.tsx         ← route guard + role guard
        ├── lib/
        │   └── axios.ts                   ← HTTP client + interceptors
        └── App.tsx                        ← router setup
```

---

## How to extend

**Add a new protected endpoint:**
```java
@GetMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> getAllUsers(@AuthenticationPrincipal User currentUser) {
    // only ADMIN can reach here
}
```

**Add a new role:**
1. Add the value to `UserRole.java`
2. Update the `CHECK` constraint in a new Flyway migration
3. Use `hasRole('NEW_ROLE')` in `@PreAuthorize`

**Change token lifetimes:**
Edit `application.yml`:
```yaml
app:
  jwt:
    access-token-expiration-ms: 300000   # 5 minutes
    refresh-token-expiration-days: 30    # 30 days
```

---

## Production checklist

A few things to sort out before taking this to production:

- [ ] Replace the JWT secret with a cryptographically random key (minimum 256 bits)
- [ ] Set `app.cookie.secure: true` (requires HTTPS)
- [ ] Configure CORS to your real domain instead of localhost
- [ ] Add rate limiting on `/auth/login` to prevent brute force
- [ ] Add account lockout after N failed login attempts
- [ ] Move all secrets to environment variables or a secrets manager
- [ ] Set up HTTPS with a valid certificate

---

## What you will learn

Reading through this codebase you'll come across:

- How JSON Web Tokens work and what goes inside them
- The difference between **access tokens** and **refresh tokens**, and why you need both
- Why refresh tokens are stored as **SHA-256 hashes** in the database
- **Refresh token rotation** and **reuse detection** — how to detect a stolen token
- Why the access token lives **in memory** (not localStorage) and the refresh token in an **httpOnly cookie**
- How Spring Security 6 filter chains work
- How to protect React routes by authentication and role

---

## How JWT authentication works

### The token pair

Every login produces two tokens with different jobs:

```
┌─────────────────────────────────────────────────────────┐
│  ACCESS TOKEN (JWT)                                     │
│  • Lives in JavaScript memory (never localStorage)      │
│  • Sent in every API request: Authorization: Bearer ... │
│  • Short-lived: 15 minutes                              │
│  • Validated without touching the database              │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  REFRESH TOKEN (opaque random string)                   │
│  • Lives in an httpOnly cookie (JavaScript cannot read) │
│  • Sent automatically by the browser to /auth/refresh   │
│  • Long-lived: 7 days                                   │
│  • Stored as SHA-256 hash in the database               │
│  • Used only to obtain a new access token               │
└─────────────────────────────────────────────────────────┘
```

### What is inside a JWT?

A JWT has three Base64-encoded parts separated by dots. It is **not encrypted** — anyone can read the payload. The signature only guarantees it was not tampered with.

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJ1c2VyQGRlbW8uY29tIiwicm9sZSI6IlVTRVIifQ . xK3L...
        HEADER                              PAYLOAD                          SIGNATURE
    (algorithm: HS256)         (email, role, issued-at, expires-at)       (HMAC-SHA256)
```

Decoded payload example:

```json
{
  "sub": "user@demo.com",
  "role": "USER",
  "iat": 1700000000,
  "exp": 1700000900
}
```

> **Never put sensitive data in a JWT payload.** It is readable by anyone who has the token.

---

## Authentication flows

### Login

```
Browser / Client                  Backend                      Database
       │                             │                              │
       │  POST /auth/login           │                              │
       │  { email, password }        │                              │
       │────────────────────────────>│                              │
       │                             │  SELECT user WHERE email=?   │
       │                             │─────────────────────────────>│
       │                             │  { user row }                │
       │                             │<─────────────────────────────│
       │                             │  BCrypt.verify(password)     │
       │                             │  generate JWT (15 min)       │
       │                             │  generate refresh token      │
       │                             │  SHA-256(refresh token)      │
       │                             │  INSERT refresh_tokens       │
       │                             │─────────────────────────────>│
       │                             │<─────────────────────────────│
       │  200 OK                     │                              │
       │  Body: { accessToken }      │                              │
       │  Set-Cookie: refresh_token  │                              │
       │<────────────────────────────│                              │
```

### Authenticated request

```
Browser / Client         axios interceptor           Backend
       │                        │                        │
       │  GET /api/v1/users/me  │                        │
       │───────────────────────>│                        │
       │                        │  adds header:          │
       │                        │  Authorization:        │
       │                        │  Bearer eyJhb...       │
       │                        │───────────────────────>│
       │                        │                    JwtAuthFilter:
       │                        │                    verify signature
       │                        │                    check expiry
       │                        │                    load user
       │                        │                    → SecurityContext
       │  200 { email, role }   │                        │
       │<───────────────────────│<───────────────────────│
```

### Automatic token refresh

```
axios interceptor                    Backend
       │                                │
       │  any request → 401             │
       │  (access token expired)        │
       │◀───────────────────────────────│
       │                                │
       │  POST /auth/refresh            │
       │  (cookie sent automatically)   │
       │───────────────────────────────>│
       │                            find hash in DB
       │                            not revoked? ✓
       │                            not expired? ✓
       │                            revoke old token
       │                            issue new pair
       │  200 { newAccessToken }        │
       │◀───────────────────────────────│
       │                                │
       │  retry original request        │
       │  with new token                │
       │───────────────────────────────>│
```

### Reuse detection — detecting a stolen refresh token

If a refresh token that was already revoked is presented again, it means either:
- The token was stolen and used by an attacker, or
- The legitimate response was not received and the client is retrying

The safe response is to **revoke all sessions for that user**:

```
Client presents refresh token that is already revoked in DB
                          │
                          ▼
              revokeAllUserTokens(user)
              ────────────────────────
              UPDATE refresh_tokens
              SET revoked = true
              WHERE user_id = ?
                          │
                          ▼
              401 — all sessions terminated
              User must log in again
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  FRONTEND  localhost:5173                                       │
│                                                                 │
│  App.tsx (React Router)                                         │
│  ├── /login          → LoginPage (public)                       │
│  ├── /unauthorized   → UnauthorizedPage (public)                │
│  └── ProtectedRoute                                             │
│       └── /dashboard → DashboardPage (any authenticated user)   │
│                                                                 │
│  AuthContext — global auth state                                │
│  ├── user: { email, role } | null                               │
│  ├── isLoading: boolean                                         │
│  ├── login() / logout()                                         │
│  └── silent refresh on page load (from cookie)                  │
│                                                                 │
│  axios instance                                                 │
│  ├── request interceptor  → adds Authorization header           │
│  └── response interceptor → handles 401 with auto-refresh       │
└───────────────────────────┬─────────────────────────────────────┘
                            │ Vite proxy → localhost:8080
┌───────────────────────────▼─────────────────────────────────────┐
│  BACKEND  localhost:8080                                        │
│                                                                 │
│  Every request passes through:                                  │
│                                                                 │
│  JwtAuthFilter                                                  │
│  └── reads Authorization header                                 │
│      validates signature + expiry                               │
│      loads user from DB                                         │
│      sets SecurityContext                                        │
│            │                                                    │
│  SecurityConfig (rules)                                         │
│  ├── /auth/** → public                                          │
│  └── everything else → authenticated                            │
│            │                                                    │
│  AuthController                                                 │
│  ├── POST /auth/login                                           │
│  ├── POST /auth/refresh                                         │
│  └── POST /auth/logout                                          │
│            │                                                    │
│  AuthService (business logic)                                   │
│  ├── AuthenticationManager (verifies credentials)               │
│  ├── JwtService (generates / validates tokens)                  │
│  └── RefreshTokenRepository (DB operations)                     │
│                                                                 │
│  UsersController                                                │
│  └── GET /api/v1/users/me (current user info)                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│  DATABASE  PostgreSQL 16                                        │
│                                                                 │
│  users                    refresh_tokens                        │
│  ├── id (UUID)            ├── id (UUID)                         │
│  ├── email (unique)       ├── user_id (FK)                      │
│  ├── password_hash        ├── token_hash (SHA-256)              │
│  ├── role (ADMIN|USER)    ├── expires_at                        │
│  ├── is_active            ├── revoked                           │
│  └── version              └── created_at                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key security decisions

**Why not store the refresh token as plain text in the database?**
If an attacker dumps the database, plain-text tokens would let them impersonate every user immediately. Storing only the SHA-256 hash means the dump is useless — the actual token values only ever exist in the client's cookie.

**Why is the access token in JavaScript memory and not localStorage?**
`localStorage` is accessible to any script running on the page, including code injected through XSS. Keeping the token in a plain React state variable means it disappears on tab close and is unreachable from outside the app.

**Why is the refresh token in an httpOnly cookie?**
`httpOnly` cookies are invisible to JavaScript entirely — the browser attaches them automatically and that's it. Combined with `SameSite=Strict`, cross-site requests won't carry the cookie either, which covers both XSS and CSRF in one move.

**Why rotation on every refresh?**
Without rotation, a stolen refresh token is valid for its full 7-day lifetime and there's nothing you can do about it. With rotation, each use invalidates the previous token. If someone presents a token that's already been rotated, it's a signal that something is wrong — so all sessions for that user get revoked immediately.

**Why two tokens instead of one long-lived JWT?**
JWTs can't be revoked. If you issue a single long-lived JWT and it gets stolen, you're stuck waiting for it to expire. The access/refresh split keeps the JWT window short (15 min) while the refresh token gives you an actual revocation mechanism.

---

## Postman collection

Import `docs/postman/spring-react-jwt-starter.postman_collection.json` to get a ready-to-use collection with all endpoints pre-configured. Cookies are handled automatically.

---

Please, if you are an expert and you find any issues, vulnerabilities, or solid ways to improve this, feel free to reach out.

---

## License

MIT — use this code however you like.

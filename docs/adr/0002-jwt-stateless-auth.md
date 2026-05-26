# ADR-0002: Stateless JWT Authentication

**Status:** Accepted
**Date:** 2026-05-26

## Context
The web app is a React SPA hitting a Spring Boot API. We need an auth scheme that:
- Works cleanly with a SPA.
- Doesn't require sticky sessions (so we can scale horizontally later).
- Protects against the common token-theft vectors.

## Decision
**Spring Security + JWT** with the following shape:
- **Access token**: short-lived (15 min), HS256-signed JWT. Returned in the login response body. Stored **in memory only** on the client (never localStorage / sessionStorage).
- **Refresh token**: long-lived (7 days), opaque (not a JWT), random 32-byte value. Stored hashed (SHA-256) in `refresh_token` table. Delivered to the client as an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`.
- **Rotation**: every `/auth/refresh` issues a new access + new refresh; old refresh marked revoked. Tokens are linked into a "family" by `family_id`.
- **Reuse detection**: presenting a revoked refresh token invalidates the entire family.

## Consequences

**Positive**
- No server-side session state; backend scales horizontally without sticky sessions.
- Access token in memory means XSS that exfiltrates a token only gets 15 minutes of access (and can't reach the refresh cookie because of HttpOnly).
- Refresh in cookie means CSRF risk, mitigated by `SameSite=Strict` plus the cookie's tight path scope.
- Refresh rotation + reuse detection catches token theft early.

**Negative**
- Page reload loses the in-memory access token; client must call `/auth/refresh` on bootstrap. Acceptable — it's a single round-trip.
- HS256 with a shared secret means the secret must be protected and rotated periodically. Documented in the runbook.
- We can't immediately revoke an issued access token (it's stateless). Mitigation: 15-min TTL is short enough; for emergency revocation we rotate the signing key.

**Rejected alternatives**
- Session cookies: would work, but ties us to sticky sessions or a shared session store. Not worth the dependency.
- OAuth2 / OIDC via external IdP: adds operational dependency for a single-tenant on-prem product; users would resent the extra hop. Can be added later without breaking the API surface.
- Access token in cookie: simpler client code but conflates auth & session, and complicates the refresh flow.

## Future
- HS256 → RS256 if we ever need third parties to verify tokens.
- Optional MFA can be added between username/password and token issuance without changing the token shape.

# JWT Logout Revocation Design

**Date:** 2026-08-24 (revised 2026-08-26)
**Feature:** Checklist 11.1 — logout must invalidate the JWT
**Status:** Approved

---

## Revision (2026-08-26): keyed by token hash, not `jti`

The original design (below) keyed revocation by the JWT's `jti` claim. Live testing from the
frontend side found this doesn't work for real users: this app's actual login flow is classic
`POST /sws/login` (`app/api/auth/login/route.ts`, kept specifically because it also syncs the
classic `JSESSIONID` that legacy features — attachments, notes, printing — depend on), and
`com.smf.securewebservices.utils.SecureWebServicesUtils.generateToken` (the code behind
`/sws/login`) mints tokens with **no `jti` claim at all**. Only `/meta/login` — a documented
fallback used solely when the org has no warehouses, not the normal path — sets `jti`. So for
the overwhelming majority of real logins, the revocation table was structurally empty of
anything to match against: the feature worked exactly as designed, and still didn't close the
checklist's own scenario 1 for real traffic.

**Fix, entirely within this module, no core changes needed:** key revocation by
`SHA-256(raw token string)` instead of the `jti` claim. Every JWT has its full raw string
available regardless of which claims it carries, so this works identically for tokens from
`/sws/login`, `/meta/login`, or any future issuer. Two different logins for the same user
produce different tokens (different `iat` at minimum) and therefore different hashes, so
per-session revocation semantics are preserved exactly as before.

This *removes* the "tokens without a `jti` can't be revoked" limitation documented below
entirely — there is no longer any class of token this mechanism can't revoke (within this
module's own traffic; the "doesn't protect other `/sws/*` beans" limitation is unchanged, see
below). It also simplifies `BaseWebService`'s check: it no longer needs to decode/verify the
JWT at all for this purpose (that already happened upstream in `SecureWebServiceServlet`) — it
just extracts the raw bearer string and hashes it.

Concretely, every mention of `jti` below as *what gets stored/queried* is superseded by "the
raw token's SHA-256 hash" — the `ETMETA_REVOKED_TOKEN` table's key column is renamed `JTI` →
`TOKEN_HASH`, `TokenRevocationStore.isRevoked`/`revoke` take a raw token string (and hash it
internally) instead of a `jti` string, and neither `LogoutService` nor `BaseWebService` extract
a `jti` claim anymore. The rest of the design — the table's audit columns, the opportunistic
expiry cleanup, the fail-closed DB-error behavior, the direct-401-write mechanism in
`dispatch()`, the `PASSWORD_EXPIRED_ALLOWED_PATHS` exemption, why login itself is unaffected —
is unchanged and still accurate as written.

---

## Context

JWT auth is stateless: logout today only clears client state (`contexts/user.tsx` in the
Next.js frontend) and the BFF's local `app/api/auth/logout/route.ts`, which calls
`clearErpSessionCookie(userToken)` — a purely local operation on the BFF's own
token→JSESSIONID/CSRF map, unrelated to the JWT itself. No backend endpoint is called at all
today. A leaked or reused token therefore keeps working until its natural expiration.

Investigation of the actual request path (see below) showed the fix cannot live in
`com.smf.securewebservices` alone as originally assumed, nor does it need to: this module can
close the gap for its own traffic.

### Where JWT auth actually happens today

- `web.xml` maps `/sws/*` to `com.smf.securewebservices.service.SecureWebServiceServlet`
  (external, `modules_core/com.smf.securewebservices`). It decodes, verifies signature/expiry,
  and sets `OBContext` **before** any `com.etendoerp.metadata` code runs.
- The frontend's only path into this backend is `/sws/com.etendoerp.metadata.meta/*`
  (`API_METADATA_URL` in `packages/api-client`), which resolves the `meta` `WebService` bean —
  `com.etendoerp.metadata.http.MetadataServlet` — via `SecureWebServiceServlet`.
- The direct `/meta/*` → `MetadataServlet` mapping in `web.xml` is dead: `MetadataServlet` only
  implements `WebService`, not `Servlet` (confirmed via `javap` and a live request returning
  Classic's generic 404, not this module's error page).
- Inside `com.etendoerp.metadata`'s own dispatch chain (`MetadataServlet` →
  `ServiceFactory.getService()` → `MetadataService`/`BaseWebService`), there is **no** JWT
  decode/verify code today. `BaseWebService.dispatch()` (`http/BaseWebService.java:116`) is the
  one place every verb of every service already funnels through, and it already runs a
  request-level guard (`rejectIfPasswordExpired`) before `process()`.

### Scope and known limitation

This design adds revocation enforcement **only** to `BaseWebService.dispatch()`, i.e. only to
traffic dispatched through this module's services. That covers 100% of what the frontend uses
(`/sws/com.etendoerp.metadata.meta/*`). It does **not** protect other `WebService` beans under
`/sws/*` outside this module — a JWT revoked here would still be accepted there. This is an
accepted, documented limitation, not a bug: closing that would require changing
`com.smf.securewebservices`, which is out of scope (this module can only touch its own code).

~~**Sharper edge of the same limitation, found during implementation:** classic `/sws/login`
mints tokens with no `jti` claim, so only tokens minted by this module's own `LoginService`
could be revoked.~~ **Superseded by the 2026-08-26 revision above** — keying by token hash
instead of `jti` closes this entirely; every token, regardless of issuer, can be revoked.

---

## Decision

Add a per-token-hash revocation blacklist owned entirely by `com.etendoerp.metadata`:

- A new AD table, `ETMETA_REVOKED_TOKEN`, storing a SHA-256 hash of each revoked token with its
  original expiration, so revoked-but-expired rows can be purged instead of growing forever.
- A new `LogoutService` that writes to it.
- A new check in `BaseWebService.dispatch()`, that reads from it before every request is
  processed and, on a hit, **writes the 401 response directly** rather than throwing (see
  "Why not throw `UnauthorizedException`" below — a thrown exception from this call site
  does not reach a 401 in practice).
- `Constants.LOGOUT_PATH` added to `Constants.PASSWORD_EXPIRED_ALLOWED_PATHS`, so an account
  with an expired password can still revoke its own token (see "Password-expired accounts"
  below).

Storage is DB-backed (not an in-memory map) because the deployment topology (single vs.
multi-node Tomcat) isn't confirmed, and a DB table is correct in both cases at negligible extra
cost over an in-process map — it also survives a restart.

On a DB error while checking revocation status, the request **fails closed** (propagates as a
500) rather than silently allowing a potentially-revoked token through. This trades a DB outage
taking down the whole `/meta` API for never serving a request without confirming the token
isn't revoked.

---

## Data Model

### `ETMETA_REVOKED_TOKEN`

| Column         | Type          | Notes                                                        |
|----------------|---------------|---------------------------------------------------------------|
| (PK)           | UUID          | standard Etendo PK                                            |
| `ad_client_id`, `ad_org_id`, `isactive`, `created`, `createdby`, `updated`, `updatedby` | — | standard Etendo audit/security columns |
| `token_hash`   | VARCHAR(100)  | hex-encoded SHA-256 of the full raw token string, enforced unique via a DB constraint (renamed from `jti` in the 2026-08-26 revision — same physical column, same size, new meaning) |
| `expires_at`   | TIMESTAMP, nullable | copy of the token's `exp` claim; `null` if the token was minted with no expiration (`SWSConfig.getExpirationTime() == 0`) — such rows are never purged |

The Java entity (e.g. `RevokedToken`) is generated at build time from the AD table
definition, same as `SavedView`, `UserFavorite`, etc. — not hand-written.

The table itself and its `JTI` column already exist on this branch (created for the original
jti-based design). This revision's DB work is a **rename**, not a fresh creation: `JTI` →
`TOKEN_HASH` on the existing table, via the same manual-SQL + `AD_COLUMN`/`AD_ELEMENT` update +
entity-regeneration path used to create the table originally (webhooks were unavailable in this
environment; the module's `/etendo:alter-db` tooling has no automated column-rename operation
either way — see its own docs, "Modify column" is manual SQL).

---

## Components

### Shared helpers: bearer token extraction (`auth.Utils`)

Two entry points, sharing one private extraction routine that strips the `"Bearer "` prefix off
the `Authorization` header:

- `decodeBearerToken(HttpServletRequest)` — existing, decodes+verifies via `decodeToken`,
  returns `DecodedJWT` or `null`. Still used by `LogoutService` for the `exp` claim.
- `extractBearerToken(HttpServletRequest)` — new, returns the raw token string (or `null`), no
  decoding/verification. This is what gets hashed for revocation lookups — no need to
  re-verify a signature `SecureWebServiceServlet` already checked upstream.

(Authorization-header parsing is separately duplicated inline in `HttpServletRequestWrapper` and
`SSOService`, and forwarded verbatim by `WidgetDataService` — none of those three are touched,
same as before.)

### `TokenRevocationStore`

Public API changes from `isRevoked(String jti)`/`revoke(String jti, Date expiresAt)` to
`isRevoked(String rawToken)`/`revoke(String rawToken, Date expiresAt)`. Hashing
(`MessageDigest.getInstance("SHA-256")`, JDK stdlib, no new dependency) happens inside the
store — callers never see or handle the hash themselves, only the raw token string.

### `LogoutService`

New service, same shape as `LoginService`, registered in `ServiceFactory` as an exact-match
path under `Constants.LOGOUT_PATH = "/logout"` (full path from the frontend:
`POST /sws/com.etendoerp.metadata.meta/logout`).

`process()`:

1. Extract the raw bearer token (`extractBearerToken`) and decode it (`decodeBearerToken`, for
   `exp`); if either is missing, 401.
2. Opportunistic cleanup: `DELETE FROM ETMETA_REVOKED_TOKEN WHERE expires_at IS NOT NULL AND
   expires_at < now()`. This keeps the table bounded without a scheduled/cron process.
3. Find-or-create by the raw token's hash: if a row already exists (double logout / already
   revoked), do nothing; otherwise insert `(token_hash, expires_at)`. The DB unique constraint
   is the real safety net for concurrent double-logout (two near-simultaneous requests with the
   same token) racing past the find check — a resulting unique-violation on insert is treated
   as "already revoked" (caught, no-op), not as a 500.
4. Return 200 with no body.

No request body is expected or read — the `Authorization` header is the only input, matching
the frontend contract (fire-and-forget from the UI, no strict response contract from the BFF).

There is no longer a "missing/blank `jti`" no-op case — every successfully-decoded token has a
raw string to hash, so this branch (and its test) from the pre-revision design is removed.

### Revocation check in `BaseWebService.dispatch()`

```java
private void dispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    if (isRevoked(request)) {
        Utils.writeJsonErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
        return;
    }
    rejectIfPasswordExpired(request);
    process(request, response);
}
```

Uses the existing `Utils.writeJsonErrorResponse(response, statusCode, errorMessage)` helper (not
the lower-level `writeJsonResponse`), so the body matches the `{"success":false,"error":...,
"status":...}` shape the frontend already gets from every other error in this module.

1. Extract the raw bearer token via `auth.Utils.extractBearerToken` — no decode/verify needed
   here at all (that already happened upstream in `SecureWebServiceServlet`); an absent header
   just means "nothing to check", not an error.
2. Query `ETMETA_REVOKED_TOKEN` for the token's hash via `TokenRevocationStore.isRevoked`.
3. If the lookup itself throws (DB error), let it propagate out of `dispatch()` uncaught — this
   is the fail-closed behavior. (Where that lands — 500 via `SecureWebServiceServlet`'s generic
   `Throwable` handler — is a pre-existing gap, see below; not something this change needs to
   fix, since a 500 already fails closed.)

#### Why not throw `UnauthorizedException`

The obvious approach — throw `UnauthorizedException`, matching `rejectIfPasswordExpired`'s
existing style, and rely on the mapping to 401 in `Utils.getHttpStatusFor` — **does not work**.
That mapping is only consulted by `MetadataServlet.handleException()`, which wraps
`ServiceFactory.getService(req, res).process()` — i.e. only code that runs *inside*
`process()`. An exception thrown from `dispatch()` *before* `process()` is called never reaches
that catch block: it propagates up through `doGet()`/`doPost()`/etc., out of the `WebService`
bean entirely, into `com.smf.securewebservices.service.BaseSecureWebServiceServlet.doService()`
(external, in `modules_core`), whose own catch chain only special-cases
`InvalidRequestException`/`InvalidContentException`/`ResourceNotFoundException`/
`OBSecurityException` — a plain `UnauthorizedException` falls through to its generic
`catch (Throwable t) { response.setStatus(500); ... }`. Verified by reading that method in
full. **This means the pre-existing `rejectIfPasswordExpired` guard almost certainly returns
500, not 401, for an expired password today** — a latent bug, out of scope to fix here, but the
new revocation check must not repeat it.

Instead, `dispatch()` writes and flushes the 401 response itself via the existing
`Utils.writeJsonErrorResponse` helper (the same one `MetadataServlet.handleException()` falls
back to) before returning, without calling `process()`. Writing and
flushing commits the response, so `BaseSecureWebServiceServlet.doService()`'s later
unconditional `response.setStatus(200)` (called after `super.service(...)` returns normally,
same method as the generic `catch (Throwable t)` above — both live on `BaseSecureWebServiceServlet`,
not `SecureWebServiceServlet`) is a no-op against an already-committed response — the same
reason `MetadataServlet`'s own in-`process()` exception handling produces correct status codes
today.

This also has to live in `dispatch()` itself, not in `MetadataServlet.process()`'s catch block:
`BaseWebService` has a second subclass, `ForwarderServlet` (proxies datasource/grid requests —
real frontend traffic), whose own `process()` doesn't map exceptions to HTTP statuses at all.
Fixing this at the shared `dispatch()` level covers both subclasses uniformly.

This runs for **every** verb of **every** service reached through `dispatch()` — which includes
`LogoutService` itself: calling logout twice with an already-revoked token correctly 401s on
the second call at `dispatch()`, before `LogoutService.process()` runs again. This means the
find-or-create branch in `LogoutService` step 3 is defensive rather than a path that's actually
exercised in practice — kept anyway for correctness if the guard's implementation ever changes.

#### Login is unaffected

By construction, not by an added exemption: the frontend's login call
(`POST /sws/com.etendoerp.metadata.meta/login`) is served by `UnauthenticatedLoginServlet`, an
exact-path `@WebServlet` that takes precedence over `SecureWebServiceServlet`'s `/sws/*`
wildcard mapping specifically so it can accept requests with no prior token. It calls
`new MetadataServlet().process(request, response)` directly — never `doPost()` — so it never
runs `dispatch()` at all, and neither the new revocation check nor the pre-existing
`rejectIfPasswordExpired` ever execute for it. Every other endpoint is reached through the
normal `doGet`/`doPost`/`doPut`/`doDelete`/`doPatch` methods, which do call `dispatch()`, and
structurally cannot be reached without a token that `SecureWebServiceServlet` already validated
first.

#### Password-expired accounts

`rejectIfPasswordExpired` runs after the revocation check and rejects any path not in
`Constants.PASSWORD_EXPIRED_ALLOWED_PATHS` (currently `SESSION_PATH`, `LABELS_PATH`,
`LANGUAGE_PATH`, `PREFERENCES_PATH`). `LOGOUT_PATH` must be added to that list — otherwise an
account whose password was force-expired (e.g. because it's suspected compromised) could never
call `/logout` to revoke its own leaked token, undermining exactly the case this feature exists
for.

---

## Data Flow

1. **Login** — unchanged, via either `/sws/login` (classic, no `jti`) or `/meta/login`
   (fallback, sets `jti`) — irrelevant now, since revocation no longer depends on `jti`.
2. **Logout** — UI clears local state optimistically, then calls `POST /api/auth/logout` (BFF)
   with the existing `Authorization` header, tolerating any response. The BFF now also calls
   `POST {ETENDO_CLASSIC_URL}/sws/com.etendoerp.metadata.meta/logout` with the same header,
   best-effort (this call is outside this module's scope — frontend work).
3. That request passes `SecureWebServiceServlet`'s existing signature/expiry check (unchanged,
   external), reaches `LogoutService`, and the raw token's SHA-256 hash is inserted into
   `ETMETA_REVOKED_TOKEN`.
4. **Any later request with the same JWT** — passes `SecureWebServiceServlet`'s check (the
   token is still cryptographically valid and unexpired) but `BaseWebService.dispatch()` finds
   its hash revoked and writes a 401 directly, before any business logic runs. This now holds
   regardless of which login path issued the token.

---

## Test Scenarios

Maps directly to the checklist:

1. **Reusing the old token returns 401** — call `LogoutService`, then dispatch a request to any
   protected service with the same token; assert the response status is 401 (not just that an
   exception type was thrown — per the mechanism above, the status is set directly on the
   `HttpServletResponse` inside `dispatch()`, so the test must inspect the mock response's
   status, not just expect a thrown exception).
2. **Client state cleared even if revocation fails** — frontend-only, already implemented
   (`contexts/user.tsx` clears state before calling logout and swallows the result). Backend
   side: assert a `LogoutService` failure (e.g. simulated DB error) surfaces as a clean 500
   through existing exception handling rather than corrupting request state.
3. **Non-revoked token keeps working** — dispatch a request without ever inserting its hash
   into `ETMETA_REVOKED_TOKEN`; assert it proceeds normally.
4. **Password-expired account can still log out** — set up a request whose user has an expired
   password and whose path is `LOGOUT_PATH`; assert it is NOT rejected by
   `rejectIfPasswordExpired` (i.e. reaches `LogoutService.process()`).
5. **A token from either login path can be revoked** — the whole point of this revision: assert
   revocation works identically for a token that has a `jti` claim and one that doesn't
   (`decoded.getClaim("jti")` returning a null-backed `Claim`), since the mechanism no longer
   inspects that claim at all.

These become a JUnit test (OBDal + mocked `HttpServletRequest`) alongside `LogoutService` and
`BaseWebService`. Note: this module's Gradle test harness does not currently run its ~140
existing tests (pre-existing build-graph/sourceSet gap), so this cannot be verified via
`./gradlew test` locally — the test is written and correct on inspection, but a live/staging run
is needed to confirm it executes.

---

## Out of Scope

- Any change to `com.smf.securewebservices` (revocation for non-`meta` `/sws/*` traffic).
- The frontend/BFF change to actually call the new endpoint on logout (separate repo, separate
  spec).
- A scheduled/cron cleanup process for `ETMETA_REVOKED_TOKEN` — the opportunistic delete in
  `LogoutService` is sufficient at expected logout volumes; revisit only if the table is
  observed to grow unbounded in practice.

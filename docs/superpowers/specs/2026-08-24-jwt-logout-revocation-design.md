# JWT Logout Revocation Design

**Date:** 2026-08-24
**Feature:** Checklist 11.1 — logout must invalidate the JWT
**Status:** Approved

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

---

## Decision

Add a per-`jti` revocation blacklist owned entirely by `com.etendoerp.metadata`:

- A new AD table, `ETMETA_REVOKED_TOKEN`, storing revoked `jti` claims with their original
  expiration, so revoked-but-expired rows can be purged instead of growing forever.
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
| `jti`          | VARCHAR(100)  | `jti` claim, enforced unique via a DB constraint                |
| `expires_at`   | TIMESTAMP, nullable | copy of the token's `exp` claim; `null` if the token was minted with no expiration (`SWSConfig.getExpirationTime() == 0`) — such rows are never purged |

The Java entity (e.g. `RevokedToken`) is generated at build time from the AD table
definition, same as `SavedView`, `UserFavorite`, etc. — not hand-written.

Creating this table goes through the module's existing AD/webhook tooling
(`/etendo:alter-db`); if webhooks aren't available in the working environment, the SQL
fallback documented for this project applies.

---

## Components

### Shared helper: bearer token extraction

Authorization-header `Bearer `-stripping is currently duplicated inline in two places
(`HttpServletRequestWrapper`, `SSOService`). A third place, `WidgetDataService`, reads the raw
`Authorization` header but forwards it verbatim (including the `Bearer ` prefix) rather than
parsing it, so it isn't the same duplication and doesn't need touching. Rather than add a third
inline copy of the actual parsing, add one static helper — e.g.
`Utils.getBearerToken(HttpServletRequest)` in `com.etendoerp.metadata.auth.Utils` — and use it
from both new call sites below. All three existing call sites are left untouched (out of scope
for this change).

### `LogoutService`

New service, same shape as `LoginService`, registered in `ServiceFactory` as an exact-match
path under `Constants.LOGOUT_PATH = "/logout"` (full path from the frontend:
`POST /sws/com.etendoerp.metadata.meta/logout`).

`process()`:

1. Extract + decode the bearer token (shared helper + existing `auth.Utils.decodeToken`) to get
   `jti` and the `exp` claim.
2. Opportunistic cleanup: `DELETE FROM ETMETA_REVOKED_TOKEN WHERE expires_at IS NOT NULL AND
   expires_at < now()`. This keeps the table bounded without a scheduled/cron process.
3. Find-or-create by `jti`: if a row already exists (double logout / already revoked), do
   nothing; otherwise insert `(jti, expires_at)`. The DB unique constraint on `jti` is the real
   safety net for concurrent double-logout (two near-simultaneous requests with the same
   token) racing past the find check — a resulting unique-violation on insert is treated as
   "already revoked" (caught, no-op), not as a 500.
4. Return 200 with no body.

No request body is expected or read — the `Authorization` header is the only input, matching
the frontend contract (fire-and-forget from the UI, no strict response contract from the BFF).

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

1. Extract + decode the bearer token (shared helper). `auth.Utils.decodeToken` can throw
   `OBException` on a malformed token (it doesn't return `null`) — `isRevoked` must catch that
   and treat it as not-revoked, same as an absent token (defensive only: every real caller of
   `dispatch()` already carries a token that `SecureWebServiceServlet` validated as
   well-formed, so this path is not expected to trigger in practice, but must not surface as a
   500 if it somehow does).
2. Query `ETMETA_REVOKED_TOKEN` for the `jti`.
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

1. **Login** — unchanged. JWT already carries a `jti` claim (`auth/Utils.java:378`).
2. **Logout** — UI clears local state optimistically, then calls `POST /api/auth/logout` (BFF)
   with the existing `Authorization` header, tolerating any response. The BFF now also calls
   `POST {ETENDO_CLASSIC_URL}/sws/com.etendoerp.metadata.meta/logout` with the same header,
   best-effort (this call is outside this module's scope — frontend work).
3. That request passes `SecureWebServiceServlet`'s existing signature/expiry check (unchanged,
   external), reaches `LogoutService`, and the `jti` is inserted into `ETMETA_REVOKED_TOKEN`.
4. **Any later request with the same JWT** — passes `SecureWebServiceServlet`'s check (the
   token is still cryptographically valid and unexpired) but `BaseWebService.dispatch()` finds
   the `jti` revoked and writes a 401 directly, before any business logic runs.

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
3. **Non-revoked token keeps working** — dispatch a request without ever inserting its `jti`
   into `ETMETA_REVOKED_TOKEN`; assert it proceeds normally.
4. **Password-expired account can still log out** — set up a request whose user has an expired
   password and whose path is `LOGOUT_PATH`; assert it is NOT rejected by
   `rejectIfPasswordExpired` (i.e. reaches `LogoutService.process()`).

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

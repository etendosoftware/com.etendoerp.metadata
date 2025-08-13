# Legacy Forwarding + JWT-backed Session (How it works)

This document explains how the metadata module forwards legacy Etendo/Openbravo HTML routes while establishing a valid backend session from a JWT token, so legacy servlets behave “as if” the user logged in with a normal JSESSIONID.

The flow combines components from `com.etendoerp.metadata` and `com.etendoerp.etendorx` (notably the SWS authentication) to validate tokens, bootstrap `OBContext`, and keep session attributes in a shared store.

## Example URL

Example of a legacy form opened through the forwarder:

`/etendo/meta/forward/SalesInvoice/Header_Edition.html?...&token=<JWT>`

Key points:
- `.../meta/forward/... .html`: legacy page routed through the metadata forwarder.
- `token`: JWT used to initialize/restore the server-side session context.
- Parameters like `inpKey`, `inpwindowId`, `inpkeyColumnId` help preselect the record and are stored in session.

## High-level Flow

1) Token issuance (login)
- `LoginServlet` (`com.etendoerp.metadata.http.LoginServlet`) delegates to `LoginManager` to authenticate either with credentials or an incoming JWT.
- On successful login, `LoginManager.generateLoginResult`:
  - Creates a servlet session and builds an `OBContext` for the user/role/org/warehouse/client through `SecureWebServicesUtils.createContext(...)`.
  - Stores `OBContext` in session (`OBContext.setOBContextInSession`).
  - Generates a JWT with claims including `user`, `role`, `organization`, `warehouse`, `client`, and `jti` (the session id). This token is returned to the client.

2) Forwarding a legacy page
- Requests to `/meta/forward/**` are handled by `ForwarderServlet`.
- If the path ends with `.html`, it is treated as a legacy form. The servlet:
  - Wraps the request with `HttpServletRequestWrapper` (metadata module).
  - Reads the JWT from the `Authorization: Bearer ...` header or `token` query param.
  - Decodes it using `SecureWebServicesUtils.decodeToken(...)`.
  - Extracts `jti` (the logical session id) and uses it to back the servlet session through `LegacyHttpSessionAdapter` + `SessionAttributeStore`.
  - Stores or reuses the token in the container session (consistency across page navigations).
  - Sets `RequestContext` and `VariablesSecureApp`, and loads `OBContext` (`OBContext.setOBContext(request)`).
  - Delegates to the actual legacy servlet, capturing and slightly rewriting HTML when necessary (script injection, resource path fixes).

3) Delegation to legacy servlet
- `ServletRegistry.getDelegatedServlet` locates the target `HttpSecureAppServlet` based on the mapping after `/meta/forward` and delegates the request to it.
- Because `RequestContext`/`OBContext` are set, legacy code sees a normal, authenticated session.

## Key Building Blocks

- `MetadataFilter` (`/meta/*`): ensures `RequestContext` has a servlet context and clears thread-local state after requests.

- `HttpServletRequestWrapper` (metadata):
  - Reads `Authorization: Bearer <token>` or `token` parameter.
  - Decodes the JWT and extracts `jti` and `user`.
  - Exposes `getSession()` backed by `LegacyHttpSessionAdapter`, using `jti` as the session id.

- `LegacyHttpSessionAdapter` + `SessionAttributeStore` (metadata):
  - Provides a servlet-session-like API using a shared map (`CachedConcurrentMap`) keyed by the `jti`.
  - This lets multiple requests with the same JWT operate over the same logical session, independent of browser cookies.

- `ForwarderServlet` (metadata):
  - `handleTokenConsistency`: stores the incoming `token` in the container session for reuse; if absent later, re-derives `jti` from the stored token to keep the same logical session.
  - `handleRecordIdentifier`: persists `inpKey` + `inpwindowId|inpkeyColumnId` in session to preselect the current record in legacy forms.
  - `handleRequestContext`: binds the wrapped request, `VariablesSecureApp`, and response to `RequestContext` and sets `OBContext` from the session.
  - For non-HTML requests, sets CSRF tokens based on `#Authenticated_user` and delegates directly.

- `SWSAuthenticationManager` (etendorx):
  - The authentication manager used by the metadata `BaseServlet` to validate Bearer tokens and set `OBContext`/`SessionInfo` when serving non-legacy routes.
  - Uses `SecureWebServicesUtils.decodeToken` to inspect claims and calls `OBContext.setOBContextInSession(...)`.

## How JSESSIONID and JWT work together

- Traditional login relies on the container `JSESSIONID` cookie and the servlet session.
- In this flow, the authoritative “session id” is the JWT’s `jti` claim. The metadata wrapper exposes a session whose id == `jti` and stores attributes in `SessionAttributeStore`.
- When you pass the same `token` across requests, the server retrieves the same logical session state, even if the browser does not carry a `JSESSIONID` cookie.
- For non-HTML endpoints served through `BaseServlet`, the `SWSAuthenticationManager` in etendorx also sets CSRF tokens and `OBContext` as if the user had performed a normal login.

## Security considerations

- Tokens must be signed and verifiable by `SecureWebServicesUtils` (ES256/HS256 depending on configuration) and contain the expected claims: `user`, `role`, `organization`, `warehouse`, `client`, `jti`.
- Tokens can be provided via `Authorization: Bearer <token>` or `token` query parameter. If both are absent or invalid, the request is treated as unauthenticated.
- CORS headers are managed by `AllowedCrossDomainsHandler` for OPTIONS/POST where applicable.
- Token lifetime and algorithm are driven by SWS configuration and preferences (`SMFSWS_EncryptionAlgorithm`, expiration time).

## Request parameters used by legacy pages

- `inpKey`, `inpwindowId`, `inpkeyColumnId`: when present, `ForwarderServlet` stores `<inpwindowId>|<inpkeyColumnId>` → `inpKey` in session so the legacy form loads the selected record.
- Other Openbravo/Etendo parameters (e.g., `Command`, `IsPopUpCall`, `inpadOrgId`, etc.) are passed unmodified to the legacy servlet.

## Troubleshooting

- Missing user context: verify the token is valid, not expired, and includes `jti` and identity claims. Ensure `SWSConfig` is correctly configured with keys.
- No session continuity: ensure the same `token` (hence the same `jti`) is sent on each navigation; check that `#JWT_TOKEN` is being stored in the servlet session by the first request.
- 401/403 on delegated servlet: confirm `OBContext` is set in session and that role/org are readable for the target window/tab.

## Legacy WAD Setup (Gradle)

Legacy HTML pages like `.../SalesInvoice/Header_Edition.html` are served by WAD-generated servlets under `org.openbravo.erpWindows`. If you see a `ClassNotFoundException` for that package, generate and deploy WAD with Gradle (Etendo wraps the legacy Ant tasks):

- Generate WAD sources

  ./gradlew wad

- Compile and deploy classes into the webapp

  ./gradlew compile.complete

- Alternatively, do a full build

  ./gradlew smartbuild

Verification
- After build, you should have classes like:
  - `WebContent/WEB-INF/classes/org/openbravo/erpWindows/SalesInvoice/Header.class`
- If classes are not there, run a clean build:

  ./gradlew clean wad compile.complete

Server restart
- If the app server does not auto-reload classes, restart it after the build to pick up new WAD classes.

## Usage example

1) Get a JWT token

You can authenticate with username/password (defaults will be applied if not provided).

curl -s -X POST \
  'http://localhost:8080/etendo/meta/login' \
  -H 'Content-Type: application/json' \
  -d '{
        "username": "<USER>",
        "password": "<PASS>"
        /* optionally: "client": "<AD_Client_ID>", "role": "<AD_Role_ID>", "organization": "<AD_Org_ID>", "warehouse": "<M_Warehouse_ID>" */
      }'

Response contains a JSON with a `token` field. Copy that token value.

2) Open a legacy form via forwarder

Pass the token either as a Bearer header or as a `token` query param. Example using the Sales Invoice header edition legacy page:

http://localhost:8080/etendo/meta/forward/SalesInvoice/Header_Edition.html?IsPopUpCall=1&Command=BUTTONDocAction111&inpcOrderId=<C_ORDER_ID>&inpKey=<C_INVOICE_ID>&inpWindowId=167&inpwindowId=167&inpTabId=263&inpTableId=318&inpcBpartnerId=<C_BPARTNER_ID>&inpadClientId=<AD_CLIENT_ID>&inpadOrgId=<AD_ORG_ID>&inpkeyColumnId=C_Invoice_ID&keyColumnName=C_Invoice_ID&inpdocstatus=DR&inpprocessing=N&inpdocaction=CO&token=<JWT_TOKEN>

Notes:
- Replace placeholders (`<JWT_TOKEN>`, `<C_ORDER_ID>`, etc.) with real IDs.
- Alternatively, use an `Authorization` header instead of the `token` query param:

  Authorization: Bearer <JWT_TOKEN>

If the first request includes the `token` query param, subsequent legacy navigations reuse it from the server session, keeping the same logical session (`jti`).

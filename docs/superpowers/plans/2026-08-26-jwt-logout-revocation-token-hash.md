# JWT Logout Revocation: Switch to Token-Hash Keying — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make revocation work for every login path (classic `/sws/login` included), by keying
`ETMETA_REVOKED_TOKEN` on a SHA-256 hash of the raw token instead of its `jti` claim.

**Architecture:** `TokenRevocationStore`'s public API moves from `(String jti)` to
`(String rawToken)`, hashing internally. `auth.Utils` gains `extractBearerToken` (raw token, no
decode) alongside the existing `decodeBearerToken` (decoded, for claims). `LogoutService` and
`BaseWebService` are updated to match — `BaseWebService`'s check gets *simpler* (no more
decode/verify needed there at all).

**Tech Stack:** Java, `java.security.MessageDigest` (JDK stdlib, no new dependency), JUnit 5 +
Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-jwt-logout-revocation-design.md`, see the
"Revision (2026-08-26)" section at the top — read it first.

**Already done (do not redo):** the DB column rename `ETMETA_REVOKED_TOKEN.JTI` →
`TOKEN_HASH` (same `VARCHAR(100)`), `AD_COLUMN`/`AD_ELEMENT` updated, entity regenerated and
**confirmed**: `RevokedToken.getTokenHash()`/`setTokenHash(String)`,
`RevokedToken.PROPERTY_TOKENHASH = "tokenHash"`. The HQL entity name is unchanged:
`ETMETA_Revoked_Token`.

---

### Task 1: `auth/Utils.java` — add `extractBearerToken`

**Files:**
- Modify: `src/com/etendoerp/metadata/auth/Utils.java`
- Test: `src-test/src/com/etendoerp/metadata/auth/UtilsTest.java`

Current `decodeBearerToken` (around line 267) does its own inline header parsing. Factor that
parsing into a private `extractRawToken`, used by both the existing `decodeBearerToken` and a
new public `extractBearerToken`.

- [ ] **Step 1: Write the failing tests**

Add to `UtilsTest.java`, next to the existing `testDecodeBearerToken*` tests (same file, same
`mock(HttpServletRequest.class)` pattern — no `SecureWebServicesUtils` mock needed since this
new method never calls `decodeToken`):

```java
    /**
     * Missing Authorization header must return null.
     */
    @Test
    public void testExtractBearerTokenReturnsNullWhenHeaderMissing() {
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertEquals(null, Utils.extractBearerToken(request));
    }

    /**
     * A header without the "Bearer " prefix must return null.
     */
    @Test
    public void testExtractBearerTokenReturnsNullWhenNotBearer() {
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        assertEquals(null, Utils.extractBearerToken(request));
    }

    /**
     * A "Bearer " prefix with nothing after it must return null.
     */
    @Test
    public void testExtractBearerTokenReturnsNullWhenTokenBlank() {
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer    ");

        assertEquals(null, Utils.extractBearerToken(request));
    }

    /**
     * A well-formed header returns the raw token, untouched (no decode/verify attempted).
     */
    @Test
    public void testExtractBearerTokenReturnsRawToken() {
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer some.raw.token");

        assertEquals("some.raw.token", Utils.extractBearerToken(request));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26
./gradlew test --tests "com.etendoerp.metadata.auth.UtilsTest" 2>&1 | tail -30
```

Expected: FAIL to compile — `Utils.extractBearerToken` doesn't exist yet.

- [ ] **Step 3: Implement it**

Replace the current `decodeBearerToken` method in `src/com/etendoerp/metadata/auth/Utils.java`,
and add two new methods alongside it (`extractBearerToken`, `extractRawToken`):

```java
  /**
   * Extracts and decodes the {@code Authorization: Bearer <token>} header off a request.
   * Returns {@code null} — never throws — if the header is missing, isn't a Bearer header, the
   * token is blank, or {@link #decodeToken} rejects it as malformed.
   *
   * @param request the HTTP request
   * @return the decoded token, or {@code null}
   */
  public static DecodedJWT decodeBearerToken(HttpServletRequest request) {
    String token = extractRawToken(request);
    if (token == null) {
      return null;
    }
    try {
      return decodeToken(token);
    } catch (OBException e) {
      return null;
    }
  }

  /**
   * Extracts the raw {@code Authorization: Bearer <token>} header value off a request, with no
   * decoding or signature verification attempted. Returns {@code null} if the header is
   * missing, isn't a Bearer header, or the token is blank.
   * <p>
   * Use this (not {@link #decodeBearerToken}) when only the raw token string is needed — e.g.
   * hashing it for {@code TokenRevocationStore} — and re-verifying a signature already checked
   * upstream by {@code SecureWebServiceServlet} would be wasted work.
   *
   * @param request the HTTP request
   * @return the raw token, or {@code null}
   */
  public static String extractBearerToken(HttpServletRequest request) {
    return extractRawToken(request);
  }

  private static String extractRawToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring(7).trim();
    return token.isEmpty() ? null : token;
  }
```

This is a pure refactor of `decodeBearerToken`'s existing body plus one new thin method — no
behavior change to `decodeBearerToken` itself, so every existing `testDecodeBearerToken*` test
must keep passing unmodified.

- [ ] **Step 4: Run tests to verify they pass**

Same command as Step 2. Expected: PASS — all existing `decodeBearerToken` tests plus the 4 new
`extractBearerToken` tests.

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/auth/Utils.java src-test/src/com/etendoerp/metadata/auth/UtilsTest.java
git commit -m "Hotfix ETP-4617: Add Utils.extractBearerToken for raw-token access"
```

---

### Task 2: `auth/TokenRevocationStore.java` — hash-based keying

**Files:**
- Modify: `src/com/etendoerp/metadata/auth/TokenRevocationStore.java`
- Test: `src-test/src/com/etendoerp/metadata/auth/TokenRevocationStoreTest.java`

- [ ] **Step 1: Write the failing tests**

Replace the entire contents of `TokenRevocationStoreTest.java` with:

```java
/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.data.RevokedToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenRevocationStoreTest {

    private static final String TOKEN_A = "header.payload-a.signature-a";
    private static final String TOKEN_B = "header.payload-b.signature-b";

    @Mock private OBDal obDal;
    @Mock private OBProvider obProvider;
    @Mock private Session session;

    private MockedStatic<OBDal> dalStatic;
    private MockedStatic<OBProvider> providerStatic;
    private MockedStatic<OBContext> contextStatic;

    @BeforeEach
    void setUp() {
        dalStatic = mockStatic(OBDal.class);
        providerStatic = mockStatic(OBProvider.class);
        contextStatic = mockStatic(OBContext.class);
        dalStatic.when(OBDal::getInstance).thenReturn(obDal);
        providerStatic.when(OBProvider::getInstance).thenReturn(obProvider);
        when(obDal.getSession()).thenReturn(session);
    }

    @AfterEach
    void tearDown() {
        dalStatic.close();
        providerStatic.close();
        contextStatic.close();
    }

    @SuppressWarnings("unchecked")
    private Query<Long> stubCountQuery(long count) {
        Query<Long> query = mock(Query.class);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(count);
        return query;
    }

    @SuppressWarnings("unchecked")
    private Query<Object> stubDeleteQuery() {
        Query<Object> query = mock(Query.class);
        when(session.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);
        return query;
    }

    @Test
    void isRevokedReturnsFalseForBlankToken() {
        assertFalse(TokenRevocationStore.isRevoked(""));
        assertFalse(TokenRevocationStore.isRevoked(null));
    }

    @Test
    void isRevokedReturnsTrueWhenHashRowExists() {
        stubCountQuery(1L);

        assertTrue(TokenRevocationStore.isRevoked(TOKEN_A));
    }

    @Test
    void isRevokedReturnsFalseWhenNoRow() {
        stubCountQuery(0L);

        assertFalse(TokenRevocationStore.isRevoked(TOKEN_A));
    }

    @Test
    void isRevokedHashesConsistently() {
        // Same token -> same hash -> same query parameter, every call.
        @SuppressWarnings("unchecked")
        Query<Long> query = mock(Query.class);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        when(query.setParameter(anyString(), hashCaptor.capture())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(0L);

        TokenRevocationStore.isRevoked(TOKEN_A);
        TokenRevocationStore.isRevoked(TOKEN_A);

        assertEquals(hashCaptor.getAllValues().get(0), hashCaptor.getAllValues().get(1));
    }

    @Test
    void differentTokensHashDifferently() {
        @SuppressWarnings("unchecked")
        Query<Long> query = mock(Query.class);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        when(query.setParameter(anyString(), hashCaptor.capture())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(0L);

        TokenRevocationStore.isRevoked(TOKEN_A);
        TokenRevocationStore.isRevoked(TOKEN_B);

        assertFalse(hashCaptor.getAllValues().get(0).equals(hashCaptor.getAllValues().get(1)));
    }

    @Test
    void revokeInsertsWhenNotAlreadyRevoked() {
        stubDeleteQuery();
        stubCountQuery(0L);
        RevokedToken entity = mock(RevokedToken.class);
        when(obProvider.get(RevokedToken.class)).thenReturn(entity);

        TokenRevocationStore.revoke(TOKEN_A, new Date());

        org.mockito.Mockito.verify(entity).setTokenHash(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(obDal).save(entity);
        org.mockito.Mockito.verify(obDal).flush();
    }

    @Test
    void revokeSkipsInsertWhenAlreadyRevoked() {
        stubDeleteQuery();
        stubCountQuery(1L);

        TokenRevocationStore.revoke(TOKEN_A, new Date());

        org.mockito.Mockito.verify(obProvider, org.mockito.Mockito.never()).get(RevokedToken.class);
    }

    @Test
    void revokeSwallowsConstraintViolationFromConcurrentDoubleLogout() {
        stubDeleteQuery();
        stubCountQuery(0L);
        RevokedToken entity = mock(RevokedToken.class);
        when(obProvider.get(RevokedToken.class)).thenReturn(entity);
        org.mockito.Mockito.doThrow(new ConstraintViolationException("dup", null, "etmeta_revoked_token_hash_uq"))
                .when(obDal).flush();

        TokenRevocationStore.revoke(TOKEN_A, new Date());
        // no exception propagated = pass
    }
}
```

Note: none of these tests need a precomputed SHA-256 value — `isRevokedHashesConsistently` and
`differentTokensHashDifferently` only compare captured hash values against each other, never
against a literal.

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26
./gradlew test --tests "com.etendoerp.metadata.auth.TokenRevocationStoreTest" 2>&1 | tail -40
```

Expected: FAIL to compile — `TokenRevocationStore`'s current implementation still calls
`revoked.setJti(...)`, but `RevokedToken.setJti` no longer exists (renamed to `setTokenHash`
when the DB column was renamed, already done in a separate commit). That compile error is the
expected red state.

- [ ] **Step 3: Implement it**

Replace `src/com/etendoerp/metadata/auth/TokenRevocationStore.java` in full:

```java
/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.data.RevokedToken;

/**
 * Revocation blacklist backed by {@code ETMETA_REVOKED_TOKEN}, keyed by a SHA-256 hash of the
 * full raw token rather than a {@code jti} claim - not every token issuer sets one (classic
 * {@code /sws/login} doesn't), but every token has a raw string regardless.
 * <p>
 * Isolated from {@code LogoutService} (which writes) and {@code BaseWebService} (which reads)
 * so both depend on two plain static methods instead of duplicating Hibernate query code —
 * mirrors how {@code PasswordExpirationUtils} already isolates the password-expiry check.
 * <p>
 * This is a system-level security table, not business data scoped to a caller's role — every
 * access runs in admin mode (same pattern as {@code LoginService}/{@code auth.Utils#generateToken})
 * so a role without an explicit table grant (the common case, since this table has no window)
 * can still be checked and written to.
 */
public class TokenRevocationStore {

    private static final String COUNT_BY_HASH_HQL =
            "select count(r) from ETMETA_Revoked_Token r where r.tokenHash = :tokenHash";

    private static final String DELETE_EXPIRED_HQL =
            "delete from ETMETA_Revoked_Token r where r.expiresAt is not null and r.expiresAt < :now";

    private static final String SHA_256 = "SHA-256";

    private TokenRevocationStore() { }

    /**
     * @param rawToken the full raw token string
     * @return {@code true} if this token has been revoked; {@code false} for a blank/null token too
     */
    public static boolean isRevoked(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return false;
        }
        try {
            OBContext.setAdminMode(true);
            Query<Long> query = OBDal.getInstance().getSession().createQuery(COUNT_BY_HASH_HQL, Long.class);
            query.setParameter("tokenHash", hash(rawToken));
            return query.uniqueResult() > 0;
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Revokes a token (idempotent) and opportunistically purges expired entries so the table
     * stays bounded without a scheduled cleanup process.
     *
     * @param rawToken  the full raw token string
     * @param expiresAt the token's original expiration, or {@code null} if it never expires
     */
    public static void revoke(String rawToken, Date expiresAt) {
        try {
            OBContext.setAdminMode(true);

            OBDal.getInstance().getSession().createQuery(DELETE_EXPIRED_HQL)
                    .setParameter("now", new Date())
                    .executeUpdate();

            if (isRevoked(rawToken)) {
                return;
            }

            try {
                RevokedToken revoked = OBProvider.getInstance().get(RevokedToken.class);
                revoked.setTokenHash(hash(rawToken));
                revoked.setExpiresAt(expiresAt);
                OBDal.getInstance().save(revoked);
                OBDal.getInstance().flush();
            } catch (ConstraintViolationException concurrentDoubleLogout) {
                // Another request revoked the same token between the isRevoked() check above and
                // this insert's flush. The DB's unique constraint is the real safety net for that
                // race — either way, the token ends up revoked, so there's nothing left to do here.
            }
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * @param rawToken the full raw token string, never {@code null} (callers already guard that)
     * @return the hex-encoded SHA-256 digest of {@code rawToken}
     */
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a guaranteed JDK algorithm (JLS MessageDigest spec) - this can't
            // actually happen, but the checked exception has to go somewhere.
            throw new OBException(e);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/auth/TokenRevocationStore.java src-test/src/com/etendoerp/metadata/auth/TokenRevocationStoreTest.java
git commit -m "Hotfix ETP-4617: Key TokenRevocationStore by token hash, not jti"
```

---

### Task 3: `service/LogoutService.java` — use the raw token, drop the jti guard

**Files:**
- Modify: `src/com/etendoerp/metadata/service/LogoutService.java`
- Test: `src-test/src/com/etendoerp/metadata/service/LogoutServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Replace `src-test/src/com/etendoerp/metadata/service/LogoutServiceTest.java` in full:

```java
/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogoutServiceTest {

    private static final String RAW_TOKEN = "header.payload.signature";

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private MockedStatic<Utils> authUtilsStatic;
    private MockedStatic<TokenRevocationStore> revocationStoreStatic;

    @BeforeEach
    void setUp() {
        authUtilsStatic = mockStatic(Utils.class);
        revocationStoreStatic = mockStatic(TokenRevocationStore.class);
    }

    @AfterEach
    void tearDown() {
        authUtilsStatic.close();
        revocationStoreStatic.close();
    }

    @Test
    void nonPostIsRejected() {
        when(request.getMethod()).thenReturn("GET");

        LogoutService service = new LogoutService(request, response);
        assertThrows(MethodNotAllowedException.class, service::process);
    }

    @Test
    void missingTokenIsRejected() {
        when(request.getMethod()).thenReturn("POST");
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(null);

        LogoutService service = new LogoutService(request, response);
        assertThrows(UnauthorizedException.class, service::process);

        revocationStoreStatic.verify(() -> TokenRevocationStore.revoke(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()), never());
    }

    @Test
    void validTokenRevokesRawTokenAndReturns200() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        DecodedJWT decoded = mock(DecodedJWT.class);
        Date expiresAt = new Date();
        when(decoded.getExpiresAt()).thenReturn(expiresAt);
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(decoded);
        authUtilsStatic.when(() -> Utils.extractBearerToken(request)).thenReturn(RAW_TOKEN);

        LogoutService service = new LogoutService(request, response);
        service.process();

        revocationStoreStatic.verify(() -> TokenRevocationStore.revoke(RAW_TOKEN, expiresAt));
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * A DB failure inside {@code TokenRevocationStore.revoke} must propagate out of
     * {@code process()} uncaught, not be swallowed - matches the design spec's "client state
     * cleared even if revocation fails" scenario: the failure is real and visible (a 500), not
     * silently absorbed into a false 200.
     */
    @Test
    void revocationFailurePropagatesUncaught() {
        when(request.getMethod()).thenReturn("POST");
        DecodedJWT decoded = mock(DecodedJWT.class);
        Date expiresAt = new Date();
        when(decoded.getExpiresAt()).thenReturn(expiresAt);
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(decoded);
        authUtilsStatic.when(() -> Utils.extractBearerToken(request)).thenReturn(RAW_TOKEN);
        revocationStoreStatic.when(() -> TokenRevocationStore.revoke(RAW_TOKEN, expiresAt))
                .thenThrow(new RuntimeException("DB unavailable"));

        LogoutService service = new LogoutService(request, response);
        assertThrows(RuntimeException.class, service::process);

        verify(response, never()).setStatus(HttpServletResponse.SC_OK);
    }
}
```

Note this deliberately **drops** the old `missingJtiClaimDoesNotRevokeOrCrash` test — that
scenario no longer exists, there's nothing left to guard against (every decoded token has a raw
string to hash).

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26
./gradlew test --tests "com.etendoerp.metadata.service.LogoutServiceTest" 2>&1 | tail -30
```

Expected: FAIL — `validTokenRevokesRawTokenAndReturns200` fails because `LogoutService` still
extracts a `jti` claim and calls `revoke(jti, ...)`, not `revoke(RAW_TOKEN, ...)`.

- [ ] **Step 3: Implement it**

Replace `src/com/etendoerp/metadata/service/LogoutService.java` in full:

```java
/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Serves {@code POST /sws/com.etendoerp.metadata.meta/logout} - revokes the caller's JWT by the
 * SHA-256 hash of its full raw string, so it stops working immediately instead of remaining
 * valid until it expires. Hashing the whole token (rather than reading a {@code jti} claim)
 * means this works regardless of which login path issued the token — not every issuer sets
 * {@code jti} (classic {@code /sws/login} doesn't).
 * <p>
 * No request body is read; the {@code Authorization} header is the only input, and the caller
 * (the frontend's BFF logout route) treats the response as best-effort and doesn't depend on its
 * body.
 *
 * @see com.etendoerp.metadata.auth.TokenRevocationStore
 */
public class LogoutService extends MetadataService {

    /**
     * Creates a new LogoutService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public LogoutService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (!"POST".equalsIgnoreCase(getRequest().getMethod())) {
            throw new MethodNotAllowedException();
        }

        DecodedJWT decoded = Utils.decodeBearerToken(getRequest());
        if (decoded == null) {
            throw new UnauthorizedException("Valid Authorization: Bearer <token> header required");
        }

        String rawToken = Utils.extractBearerToken(getRequest());
        TokenRevocationStore.revoke(rawToken, decoded.getExpiresAt());

        getResponse().setStatus(HttpServletResponse.SC_OK);
    }
}
```

No null-guard on `rawToken` before calling `revoke()`: `extractBearerToken` parses the exact
same `Authorization` header `decodeBearerToken` just parsed successfully above, using the same
`"Bearer "`-prefix logic, so it cannot return `null` here — the precondition
"`decodeBearerToken` succeeded implies `extractBearerToken` on the same request succeeds" holds
by construction, both parse the identical header the identical way.

- [ ] **Step 4: Run tests to verify they pass**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/service/LogoutService.java src-test/src/com/etendoerp/metadata/service/LogoutServiceTest.java
git commit -m "Hotfix ETP-4617: LogoutService revokes by raw token hash, not jti"
```

---

### Task 4: `http/BaseWebService.java` — simplify the revocation check

**Files:**
- Modify: `src/com/etendoerp/metadata/http/BaseWebService.java`
- Test: `src-test/src/com/etendoerp/metadata/http/BaseWebServiceGuardTest.java`

This is the enforcement point — same reach/mechanism as before (still writes the 401 directly
in `dispatch()`, still runs before `rejectIfPasswordExpired`), just simpler: no more JWT
decode/verify needed here at all.

- [ ] **Step 1: Update the failing test**

In `BaseWebServiceGuardTest.java`, find `testRevokedTokenBlocksProcessingAndWrites401` (added in
the original jti-based build) and replace it with a version that mocks `extractBearerToken`
instead of `decodeBearerToken`:

```java
  /**
   * Verifies that a request whose token hash is revoked never reaches processing, and that the
   * response is set to 401 directly (not thrown as an exception the caller has to catch) - see
   * the design spec for why a thrown UnauthorizedException from this call site would not
   * actually produce a 401 in production.
   */
  @Test
  void testRevokedTokenBlocksProcessingAndWrites401() throws Exception {
    given(BLOCKED_PATH, false);
    when(request.getHeader("Authorization")).thenReturn("Bearer revoked-token");

    try (MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsStatic =
             mockStatic(com.etendoerp.metadata.auth.Utils.class)) {
      authUtilsStatic.when(() -> com.etendoerp.metadata.auth.Utils.extractBearerToken(request))
          .thenReturn("revoked-token");
      revocationStatic.when(() -> com.etendoerp.metadata.auth.TokenRevocationStore.isRevoked("revoked-token"))
          .thenReturn(true);

      java.io.StringWriter body = new java.io.StringWriter();
      when(response.getWriter()).thenReturn(new java.io.PrintWriter(body));

      service.doGet("", request, response);

      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
    assertFalse(service.wasProcessed());
  }
```

`testNonRevokedTokenReachesProcessing` is unaffected (it never mocked `decodeBearerToken`
directly, it only stubbed `TokenRevocationStore.isRevoked(any())` — leave it as-is).

The `setUp`/`tearDown` `revocationStatic` mock (added in the original build) needs no changes.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26
./gradlew test --tests "com.etendoerp.metadata.http.BaseWebServiceGuardTest" 2>&1 | tail -40
```

Expected: FAIL — `isTokenRevoked` still calls `decodeBearerToken`/reads a `jti` claim, so
mocking `extractBearerToken` has no effect and the revoked check never fires as expected (the
request reaches `process()`, `wasProcessed()` is `true` instead of `false`).

- [ ] **Step 3: Implement it**

In `src/com/etendoerp/metadata/http/BaseWebService.java`, replace `isTokenRevoked`:

```java
    /**
     * Checks the caller's raw token hash against {@link TokenRevocationStore}. Deliberately does
     * <b>not</b> throw {@code UnauthorizedException} on a hit - an exception thrown from here
     * (before {@link #process}) never reaches this module's own exception-to-status mapping (see
     * the design spec, "Why not throw UnauthorizedException") - the caller must write the 401
     * response itself and return without calling {@link #process}.
     * <p>
     * No JWT decode/verify happens here - that already happened upstream in
     * {@code SecureWebServiceServlet} before this request ever reached this class. Only the raw
     * token string is needed, to hash and look up.
     *
     * @param request the HTTP request
     * @return {@code true} if the request's token is revoked
     */
    private boolean isTokenRevoked(HttpServletRequest request) {
        String rawToken = com.etendoerp.metadata.auth.Utils.extractBearerToken(request);
        return TokenRevocationStore.isRevoked(rawToken);
    }
```

(`TokenRevocationStore.isRevoked` already treats `null`/blank as "not revoked", so no separate
null-check is needed here — same short-circuit as before, one layer down.)

Also remove the now-unused `import com.auth0.jwt.interfaces.DecodedJWT;` from this file's
imports if nothing else in the file uses `DecodedJWT` (check first — `isTokenRevoked` was the
only user of that import).

Update the class javadoc's revocation-limitation paragraph (added in the original build) to
drop the now-inaccurate "only tokens minted by this module... can be revoked" claim:

```java
 * <p><b>Revocation only covers this module.</b> The check queries {@link TokenRevocationStore},
 * which only ever contains hashes of tokens revoked via this module's own {@code /logout}. A
 * token accepted here but never revoked through this module's own {@code /logout} — because the
 * client simply hasn't logged out yet, or logged out via a path outside this module — is
 * unaffected either way; other {@code /sws/*} services outside this module never consult this
 * table at all, so a revoked token still works against them.</p>
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: PASS — including every pre-existing test in the file.

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/http/BaseWebService.java src-test/src/com/etendoerp/metadata/http/BaseWebServiceGuardTest.java
git commit -m "Hotfix ETP-4617: Simplify BaseWebService's revocation check to use raw token hash"
```

---

### Task 5: Manual end-to-end re-verification

**Files:** none (verification only). Same drill as the original build's Task 7, but this time
specifically exercising a token from **classic `/sws/login`** — the whole point of this
revision — not just `/meta/login`.

- [ ] **Step 1: Build and deploy**

`./gradlew smartbuild` from `/Users/santiagoalaniz/Dev/Work/etendo_26`, then restart the local
Tomcat (`~/Downloads/apache-tomcat-8.5.99/bin/shutdown.sh` + `startup.sh`, wait for `302`/`200`
on `http://localhost:8080/etendo/`).

- [ ] **Step 2: Get a token from classic `/sws/login`** (not `/meta/login` this time)

```bash
ETENDO_URL="http://localhost:8080/etendo"
TOKEN=$(curl -s -X POST "$ETENDO_URL/sws/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")
echo "$TOKEN" | python3 -c "
import sys, base64, json
token = sys.stdin.read().strip()
payload = token.split('.')[1]
payload += '=' * (-len(payload) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(payload)), indent=2))
"
```

Confirm the printed claims have **no `jti` key** — that's the token this whole revision exists
to handle.

- [ ] **Step 3: Confirm it works before logout, then revoke it, then confirm reuse fails**

```bash
curl -s -o /dev/null -w "before logout: %{http_code}\n" "$ETENDO_URL/sws/com.etendoerp.metadata.meta/menu" -H "Authorization: Bearer $TOKEN"
curl -s -w "\nlogout: %{http_code}\n" -X POST "$ETENDO_URL/sws/com.etendoerp.metadata.meta/logout" -H "Authorization: Bearer $TOKEN"
curl -s -w "\nreuse after logout: %{http_code}\n" "$ETENDO_URL/sws/com.etendoerp.metadata.meta/menu" -H "Authorization: Bearer $TOKEN"
```

Expected: `200` / `200` / `401` with `{"success":false,"error":"Token has been revoked",...}`.
**This is the scenario that was broken before this revision** — confirm it's fixed.

- [ ] **Step 4: Confirm a `/meta/login`-issued token (has `jti`) still works too**

Re-run the original build's Task 7 sequence once (login via `/sws/com.etendoerp.metadata.meta/login`
instead of `/sws/login`, same before/logout/after checks) — should behave identically, proving
the mechanism didn't regress for the token shape it originally handled.

- [ ] **Step 5: Confirm real rows in the renamed column**

```sql
SELECT token_hash, expiresat, created FROM etmeta_revoked_token ORDER BY created DESC LIMIT 5;
```

- [ ] **Step 6: Record results**

No commit for this task. If any step doesn't match expected, that's a bug in Tasks 1-4 — go
back and fix it before considering this revision done.

---

## Out of scope (unchanged from the original spec)

- Any change to `com.smf.securewebservices` — this revision specifically avoids needing one.
- The frontend/BFF change to call `/logout` — separate repo.
- A scheduled cleanup job for `ETMETA_REVOKED_TOKEN`.

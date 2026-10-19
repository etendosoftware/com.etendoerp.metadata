# JWT Logout Revocation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make logout actually invalidate the JWT for traffic served by `com.etendoerp.metadata` (everything the frontend uses), by checking every request against a revocation list before it's processed.

**Architecture:** A new AD table (`ETMETA_REVOKED_TOKEN`) stores revoked `jti` claims. A new `LogoutService` writes to it. `BaseWebService.dispatch()` — the single choke point every verb of every service in this module already passes through — checks it before calling `process()`, and writes a 401 directly (not via a thrown exception — see spec for why) when the token's `jti` is found.

**Tech Stack:** Java (Etendo/Openbravo module), Hibernate via `OBDal`, JUnit 4/5 + Mockito (existing mixed convention in this module's test suite).

**Spec:** `docs/superpowers/specs/2026-08-24-jwt-logout-revocation-design.md` — read it first, this plan implements it as-is.

---

## Before you start

- This module's Gradle test harness does not currently run its ~140 existing tests
  (pre-existing build-graph/sourceSet gap, not something this plan fixes). Every "run the test"
  step below gives the direct JUnit invocation as a fallback if `./gradlew test` doesn't pick up
  new test classes either — try `./gradlew test` first each time in case it's been fixed since,
  but don't be surprised if it does nothing.
- Webhooks (used by the `/etendo:alter-db` skill in Task 1) may or may not be available in your
  environment — a prior session in this project found the `devassistant` module not installed
  locally and used a raw SQL fallback instead. Task 1 gives both paths; try the skill first.

---

### Task 1: Create the `ETMETA_REVOKED_TOKEN` table

**Files:**
- Create: `src-db/database/model/tables/ETMETA_REVOKED_TOKEN.xml` (physical model export, written automatically by `export.database` once the AD registration exists — do not hand-write this file's content, just verify it appears)
- Modify (generated automatically): `src-db/database/sourcedata/AD_TABLE.xml`, `AD_COLUMN.xml`, `AD_MODEL_OBJECT.xml`, `AD_MODEL_OBJECT_MAPPING.xml`, `AD_ELEMENT.xml`, `AD_FIELD.xml` — same set the calendar-resolver work touched for `ETMETA_WIDGET_CLASS` (see `git log --oneline -S"ETMETA_Widget_Class" -- src-db/database/sourcedata` for that precedent commit if you want a reference diff)
- Generated (do not write by hand): `build/**/com/etendoerp/metadata/data/RevokedToken.java` (or wherever this project's entity codegen places it — find it with the command in Step 4 below)

- [ ] **Step 1: Try the `/etendo:alter-db` skill first**

Invoke it with: `create table ETMETA_REVOKED_TOKEN with columns JTI (String, required, unique, up to 100 chars) and EXPIRES_AT (DateTime, nullable)`.

Follow its own webhook sequence (`CreateAndRegisterTable` → `CreateColumn` ×2 →
`CheckTablesColumnHook` → `SyncTerms` → `ElementsHandler` → `export.database`). Use:
- `Name`: `Revoked Token`
- `DBTableName`: `ETMETA_Revoked_Token`
- `JavaClass`: `com.etendoerp.metadata.data.RevokedToken`
- `DataAccessLevel`: `7` (same `ACCESSLEVEL` every other table in this module uses — confirmed
  in `src-db/database/sourcedata/AD_TABLE.xml` for `ETMETA_SavedView`, `ETMETA_User_Favorite`,
  `ETMETA_Widget_Class`, etc. — not `3`)
- Column `JTI`: reference `10` (String), `canBeNull: false`, and after the webhook creates it,
  manually widen it if it created VARCHAR(60) instead of the needed 100 (`ALTER TABLE
  etmeta_revoked_token ALTER COLUMN jti TYPE VARCHAR(100);` + update `AD_COLUMN.FIELDLENGTH`
  to `100` for that column so the AD model matches the physical column).
- Column `EXPIRES_AT`: reference `16` (DateTime), `canBeNull: true`.

If webhooks aren't reachable (Tomcat down, `devassistant` module missing, or the webhook calls
error out), skip to Step 2 (SQL fallback). Otherwise skip to Step 3.

- [ ] **Step 2 (fallback only — skip if Step 1 worked): Raw SQL**

Model this on the existing `ETMETA_SAVEDVIEW` table (`src-db/database/model/tables/ETMETA_SAVEDVIEW.xml`
plus its `AD_TABLE`/`AD_COLUMN`/`AD_ELEMENT` rows in `src-db/database/sourcedata/`). Run against
the project's Postgres instance:

```sql
CREATE TABLE etmeta_revoked_token (
    etmeta_revoked_token_id VARCHAR(32) NOT NULL DEFAULT '0',
    ad_client_id VARCHAR(32) NOT NULL DEFAULT '0',
    ad_org_id VARCHAR(32) NOT NULL DEFAULT '0',
    isactive CHAR(1) NOT NULL DEFAULT 'Y',
    created TIMESTAMP NOT NULL DEFAULT now(),
    createdby VARCHAR(32) NOT NULL DEFAULT '0',
    updated TIMESTAMP NOT NULL DEFAULT now(),
    updatedby VARCHAR(32) NOT NULL DEFAULT '0',
    jti VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NULL,
    CONSTRAINT etmeta_revoked_token_pk PRIMARY KEY (etmeta_revoked_token_id),
    CONSTRAINT etmeta_revoked_token_jti_uq UNIQUE (jti),
    CONSTRAINT etmeta_revoked_token_isactive_chk CHECK (isactive IN ('Y', 'N'))
);
```

Then insert the `AD_TABLE`/`AD_COLUMN`/`AD_MODEL_OBJECT`/`AD_ELEMENT` rows following the exact
shape of the `ETMETA_SAVEDVIEW` rows already in `src-db/database/sourcedata/AD_TABLE.xml` etc.
(same `AD_MODULE_ID`, `ACCESSLEVEL` = `7` — verify by grepping that file for `ACCESSLEVEL`,
every existing `ETMETA_*` table uses `7`, not `3` — `CLASSNAME='RevokedToken'`,
`TABLENAME='etmeta_revoked_token'`). Get real UUIDs with `SELECT get_uuid();` — do not invent
IDs by hand.

- [ ] **Step 3: Add the unique constraint if it isn't already there**

Whichever path you took, confirm the constraint exists:

```sql
SELECT conname FROM pg_constraint WHERE conrelid = 'etmeta_revoked_token'::regclass AND contype = 'u';
```

If nothing comes back: `ALTER TABLE etmeta_revoked_token ADD CONSTRAINT etmeta_revoked_token_jti_uq UNIQUE (jti);`

- [ ] **Step 4: Generate the Java entity and confirm its real property names**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26
./gradlew generate.entities
find . -iname "RevokedToken.java" -not -path "*/node_modules/*"
```

Open the generated file. **Confirm the exact getter/setter names for the `jti` and `expires_at`
columns** — this plan assumes `getJti()`/`setJti(String)` and `getExpiresAt()`/
`setExpiresAt(Date)`. Do not assume a mechanical naming rule: existing generated entities in
this module show property names are whatever the AD_Column/AD_Element's configured name ends up
being, not a fixed transform of the DB column name (single-word columns like `FILTERCLAUSE`
just lowercase to `getFilterclause()`; underscored columns can generate names like
`columnPosition` that don't map obviously from `COL_POSITION`). **Task 4 below is written
assuming `jti`/`expiresAt` — if the real generated names differ, use the real ones there (and
only there; Tasks 5 and 6 never touch `RevokedToken` directly, only `TokenRevocationStore`'s own
stable API, so a naming mismatch is contained to one file).**

- [ ] **Step 5: Commit**

```bash
git add src-db/database/model/tables/ETMETA_REVOKED_TOKEN.xml src-db/database/sourcedata/AD_TABLE.xml src-db/database/sourcedata/AD_COLUMN.xml src-db/database/sourcedata/AD_MODEL_OBJECT.xml src-db/database/sourcedata/AD_MODEL_OBJECT_MAPPING.xml src-db/database/sourcedata/AD_ELEMENT.xml src-db/database/sourcedata/AD_FIELD.xml
git commit -m "Add ETMETA_REVOKED_TOKEN table for JWT logout revocation"
```

---

### Task 2: Add `LOGOUT_PATH` and exempt it from the password-expired guard

**Files:**
- Modify: `src/com/etendoerp/metadata/utils/Constants.java`
- Test: `src-test/src/com/etendoerp/metadata/http/BaseWebServiceGuardTest.java`

- [ ] **Step 1: Write the failing test**

Add to `BaseWebServiceGuardTest` (this file already loops `PASSWORD_EXPIRED_ALLOWED_PATHS` in
`testAllAllowedPathsAreReachableWhileExpired`, so adding `LOGOUT_PATH` to the constant makes
that existing test cover it too — but add an explicit test as well so intent is unambiguous):

```java
  /**
   * Verifies that /logout stays reachable with an expired password — otherwise an account whose
   * password was force-expired could never revoke its own leaked token.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testLogoutPathIsAllowedWhileExpired() throws Exception {
    given(Constants.LOGOUT_PATH, true);

    service.doGet("", request, response);

    assertTrue(service.wasProcessed());
  }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.http.BaseWebServiceGuardTest" 2>&1 | tail -30
```

If that produces no output because the harness doesn't pick up this module's tests (see "Before
you start"), compile and run directly instead:

```bash
find /Users/santiagoalaniz/Dev/Work/etendo_26 -iname "junit-platform-console-standalone*.jar" 2>/dev/null
```

If no console-standalone jar is present, skip straight to Step 3 and rely on Step 4's manual
verification (reading the test/impl side by side) — record in your final report that this step
could not be run.

Expected (if runnable): FAIL — `Constants.LOGOUT_PATH` does not exist, compile error.

- [ ] **Step 3: Add the constant and the exemption**

In `src/com/etendoerp/metadata/utils/Constants.java`, add after `LABELS_PATH`:

```java
    public static final String LOGOUT_PATH = "/logout";
```

Then change:

```java
    public static final List<String> PASSWORD_EXPIRED_ALLOWED_PATHS = Collections.unmodifiableList(Arrays.asList(
            SESSION_PATH, LABELS_PATH, LANGUAGE_PATH, PREFERENCES_PATH));
```

to:

```java
    public static final List<String> PASSWORD_EXPIRED_ALLOWED_PATHS = Collections.unmodifiableList(Arrays.asList(
            SESSION_PATH, LABELS_PATH, LANGUAGE_PATH, PREFERENCES_PATH, LOGOUT_PATH));
```

- [ ] **Step 4: Run test to verify it passes**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/utils/Constants.java src-test/src/com/etendoerp/metadata/http/BaseWebServiceGuardTest.java
git commit -m "Add LOGOUT_PATH and exempt it from the password-expired guard"
```

---

### Task 3: Shared bearer-token decode helper

**Files:**
- Modify: `src/com/etendoerp/metadata/auth/Utils.java`
- Test: `src-test/src/com/etendoerp/metadata/auth/UtilsTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `UtilsTest.java` (same file, same `MockitoJUnitRunner.Silent` + `mockStatic(SecureWebServicesUtils.class)` pattern already used for `testDecodeToken`):

```java
    /**
     * Missing Authorization header must return null without touching SecureWebServicesUtils.
     */
    @Test
    public void testDecodeBearerTokenReturnsNullWhenHeaderMissing() {
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertEquals(null, Utils.decodeBearerToken(request));
    }

    /**
     * A header without the "Bearer " prefix must return null.
     */
    @Test
    public void testDecodeBearerTokenReturnsNullWhenNotBearer() {
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        assertEquals(null, Utils.decodeBearerToken(request));
    }

    /**
     * A "Bearer " prefix with nothing after it must return null.
     */
    @Test
    public void testDecodeBearerTokenReturnsNullWhenTokenBlank() {
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer    ");

        assertEquals(null, Utils.decodeBearerToken(request));
    }

    /**
     * A well-formed header delegates to decodeToken and returns its result.
     */
    @Test
    public void testDecodeBearerTokenDelegatesToDecodeToken() {
        try (MockedStatic<SecureWebServicesUtils> swsUtilsMock = mockStatic(SecureWebServicesUtils.class)) {
            javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer test-token");
            DecodedJWT decodedJWT = mock(DecodedJWT.class);
            swsUtilsMock.when(() -> SecureWebServicesUtils.decodeToken("test-token")).thenReturn(decodedJWT);

            assertEquals(decodedJWT, Utils.decodeBearerToken(request));
        }
    }

    /**
     * A malformed token that makes decodeToken throw must return null, not propagate.
     */
    @Test
    public void testDecodeBearerTokenReturnsNullWhenDecodeThrows() {
        try (MockedStatic<SecureWebServicesUtils> swsUtilsMock = mockStatic(SecureWebServicesUtils.class)) {
            javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer garbage");
            swsUtilsMock.when(() -> SecureWebServicesUtils.decodeToken("garbage"))
                    .thenThrow(new RuntimeException("bad token"));

            assertEquals(null, Utils.decodeBearerToken(request));
        }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.auth.UtilsTest" 2>&1 | tail -30
```

Expected: FAIL to compile — `Utils.decodeBearerToken` does not exist. (See Task 2 Step 2 note if
the harness doesn't run this module's tests at all.)

- [ ] **Step 3: Implement it**

In `src/com/etendoerp/metadata/auth/Utils.java`, add (needs `import javax.servlet.http.HttpServletRequest;`):

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
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring(7).trim();
    if (token.isEmpty()) {
      return null;
    }
    try {
      return decodeToken(token);
    } catch (Exception e) {
      return null;
    }
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/auth/Utils.java src-test/src/com/etendoerp/metadata/auth/UtilsTest.java
git commit -m "Add shared bearer-token decode helper"
```

---

### Task 4: `TokenRevocationStore`

A small DB-facing class, separate from `LogoutService` and `BaseWebService`, so both can depend
on two plain static methods (`isRevoked`/`revoke`) instead of duplicating Hibernate query code —
mirrors how `PasswordExpirationUtils.isExpired(user)` already isolates the existing
password-expiry check from `BaseWebService`, including being the thing tests mock statically
instead of standing up real `OBCriteria`/`Session` mocks in every caller's test.

**Files:**
- Create: `src/com/etendoerp/metadata/auth/TokenRevocationStore.java`
- Test: Create `src-test/src/com/etendoerp/metadata/auth/TokenRevocationStoreTest.java`

- [ ] **Step 1: Write the failing tests**

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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.data.RevokedToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenRevocationStoreTest {

    private static final String JTI = "session-123";

    @Mock private OBDal obDal;
    @Mock private OBProvider obProvider;
    @Mock private Session session;

    private MockedStatic<OBDal> dalStatic;
    private MockedStatic<OBProvider> providerStatic;

    @BeforeEach
    void setUp() {
        dalStatic = mockStatic(OBDal.class);
        providerStatic = mockStatic(OBProvider.class);
        dalStatic.when(OBDal::getInstance).thenReturn(obDal);
        providerStatic.when(OBProvider::getInstance).thenReturn(obProvider);
        when(obDal.getSession()).thenReturn(session);
    }

    @AfterEach
    void tearDown() {
        dalStatic.close();
        providerStatic.close();
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
    void isRevokedReturnsFalseForBlankJti() {
        assertFalse(TokenRevocationStore.isRevoked(""));
        assertFalse(TokenRevocationStore.isRevoked(null));
    }

    @Test
    void isRevokedReturnsTrueWhenRowExists() {
        stubCountQuery(1L);

        assertTrue(TokenRevocationStore.isRevoked(JTI));
    }

    @Test
    void isRevokedReturnsFalseWhenNoRow() {
        stubCountQuery(0L);

        assertFalse(TokenRevocationStore.isRevoked(JTI));
    }

    @Test
    void revokeInsertsWhenNotAlreadyRevoked() {
        stubDeleteQuery();
        stubCountQuery(0L);
        RevokedToken entity = mock(RevokedToken.class);
        when(obProvider.get(RevokedToken.class)).thenReturn(entity);

        TokenRevocationStore.revoke(JTI, new Date());

        org.mockito.Mockito.verify(entity).setJti(JTI);
        org.mockito.Mockito.verify(obDal).save(entity);
        org.mockito.Mockito.verify(obDal).flush();
    }

    @Test
    void revokeSkipsInsertWhenAlreadyRevoked() {
        stubDeleteQuery();
        stubCountQuery(1L);

        TokenRevocationStore.revoke(JTI, new Date());

        org.mockito.Mockito.verify(obProvider, org.mockito.Mockito.never()).get(RevokedToken.class);
    }

    @Test
    void revokeSwallowsConstraintViolationFromConcurrentDoubleLogout() {
        stubDeleteQuery();
        stubCountQuery(0L);
        RevokedToken entity = mock(RevokedToken.class);
        when(obProvider.get(RevokedToken.class)).thenReturn(entity);
        org.mockito.Mockito.doThrow(new ConstraintViolationException("dup", null, "etmeta_revoked_token_jti_uq"))
                .when(obDal).flush();

        TokenRevocationStore.revoke(JTI, new Date());
        // no exception propagated = pass
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.auth.TokenRevocationStoreTest" 2>&1 | tail -30
```

Expected: FAIL to compile — `TokenRevocationStore` doesn't exist yet.

- [ ] **Step 3: Implement it**

**Verify against Task 1 Step 4's actual generated entity before writing this** — if the real
property is not `jti`/`expiresAt`, adjust the HQL and getter/setter names accordingly.

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

import java.util.Date;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.data.RevokedToken;

/**
 * Per-{@code jti} JWT revocation blacklist backed by {@code ETMETA_REVOKED_TOKEN}.
 * <p>
 * Isolated from {@code LogoutService} (which writes) and {@code BaseWebService} (which reads)
 * so both depend on two plain static methods instead of duplicating Hibernate query code —
 * mirrors how {@code PasswordExpirationUtils} already isolates the password-expiry check.
 */
public class TokenRevocationStore {

    private static final String COUNT_BY_JTI_HQL =
            "select count(r) from etmeta_Revoked_Token r where r.jti = :jti";

    private static final String DELETE_EXPIRED_HQL =
            "delete from etmeta_Revoked_Token r where r.expiresAt is not null and r.expiresAt < :now";

    private TokenRevocationStore() { }

    /**
     * @param jti the token's {@code jti} claim
     * @return {@code true} if this jti has been revoked; {@code false} for a blank/null jti too
     */
    public static boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        Query<Long> query = OBDal.getInstance().getSession().createQuery(COUNT_BY_JTI_HQL, Long.class);
        query.setParameter("jti", jti);
        return query.uniqueResult() > 0;
    }

    /**
     * Revokes a jti (idempotent) and opportunistically purges expired entries so the table stays
     * bounded without a scheduled cleanup process.
     *
     * @param jti       the token's {@code jti} claim
     * @param expiresAt the token's original expiration, or {@code null} if it never expires
     */
    public static void revoke(String jti, Date expiresAt) {
        OBDal.getInstance().getSession().createQuery(DELETE_EXPIRED_HQL)
                .setParameter("now", new Date())
                .executeUpdate();

        if (isRevoked(jti)) {
            return;
        }

        try {
            RevokedToken revoked = OBProvider.getInstance().get(RevokedToken.class);
            revoked.setJti(jti);
            revoked.setExpiresAt(expiresAt);
            OBDal.getInstance().save(revoked);
            OBDal.getInstance().flush();
        } catch (ConstraintViolationException concurrentDoubleLogout) {
            // Another request revoked the same jti between the isRevoked() check above and this
            // insert's flush. The DB's unique constraint is the real safety net for that race —
            // either way, the jti ends up revoked, so there's nothing left to do here.
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/auth/TokenRevocationStore.java src-test/src/com/etendoerp/metadata/auth/TokenRevocationStoreTest.java
git commit -m "Add TokenRevocationStore for per-jti JWT revocation"
```

---

### Task 5: `LogoutService`

**Files:**
- Create: `src/com/etendoerp/metadata/service/LogoutService.java`
- Modify: `src/com/etendoerp/metadata/service/ServiceFactory.java`
- Test: Create `src-test/src/com/etendoerp/metadata/service/LogoutServiceTest.java`

- [ ] **Step 1: Write the failing tests**

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

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogoutServiceTest {

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
    void validTokenRevokesJtiAndReturns200() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        DecodedJWT decoded = mock(DecodedJWT.class);
        Claim jtiClaim = mock(Claim.class);
        when(jtiClaim.asString()).thenReturn("session-123");
        when(decoded.getClaim("jti")).thenReturn(jtiClaim);
        Date expiresAt = new Date();
        when(decoded.getExpiresAt()).thenReturn(expiresAt);
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(decoded);

        LogoutService service = new LogoutService(request, response);
        service.process();

        revocationStoreStatic.verify(() -> TokenRevocationStore.revoke("session-123", expiresAt));
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.service.LogoutServiceTest" 2>&1 | tail -30
```

Expected: FAIL to compile — `LogoutService` doesn't exist yet.

- [ ] **Step 3: Implement `LogoutService`**

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
 * Serves {@code POST /sws/com.etendoerp.metadata.meta/logout} - revokes the caller's JWT by its
 * {@code jti} claim so it stops working immediately instead of remaining valid until it expires.
 * <p>
 * No request body is read; the {@code Authorization} header is the only input, and the caller
 * (the frontend's BFF logout route) treats the response as best-effort and doesn't depend on its
 * body.
 *
 * @see com.etendoerp.metadata.auth.TokenRevocationStore
 * @see com.etendoerp.metadata.http.BaseWebService
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

        String jti = decoded.getClaim("jti").asString();
        TokenRevocationStore.revoke(jti, decoded.getExpiresAt());

        getResponse().setStatus(HttpServletResponse.SC_OK);
    }
}
```

- [ ] **Step 4: Register it in `ServiceFactory`**

In `src/com/etendoerp/metadata/service/ServiceFactory.java`, in the static block next to
`EXACT_MATCH_SERVICES.put(LOGIN_PATH, LoginService::new);`, add:

```java
        EXACT_MATCH_SERVICES.put(LOGOUT_PATH, LogoutService::new);
```

(`LOGOUT_PATH` resolves via the file's existing `import static com.etendoerp.metadata.utils.Constants.*;`.)

- [ ] **Step 5: Run tests to verify they pass**

Same command as Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/com/etendoerp/metadata/service/LogoutService.java src/com/etendoerp/metadata/service/ServiceFactory.java src-test/src/com/etendoerp/metadata/service/LogoutServiceTest.java
git commit -m "Add LogoutService and register /logout"
```

---

### Task 6: Revocation check in `BaseWebService.dispatch()`

This is the enforcement point — without it, revoking a jti has no effect on later requests.

**Files:**
- Modify: `src/com/etendoerp/metadata/http/BaseWebService.java`
- Test: `src-test/src/com/etendoerp/metadata/http/BaseWebServiceGuardTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `BaseWebServiceGuardTest.java`. This file mocks `PasswordExpirationUtils` statically
already — add a `TokenRevocationStore` static mock the same way, defaulted to `false` in
`setUp()` so every existing test in the file keeps passing unmodified. Full diff for
`setUp`/`tearDown`:

```java
  private MockedStatic<com.etendoerp.metadata.auth.TokenRevocationStore> revocationStatic;

  @BeforeEach
  void setUp() {
    service = new TestWebService();
    obContextStatic = mockStatic(OBContext.class);
    expirationStatic = mockStatic(PasswordExpirationUtils.class);
    revocationStatic = mockStatic(com.etendoerp.metadata.auth.TokenRevocationStore.class);

    obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getUser()).thenReturn(user);
    revocationStatic.when(() -> com.etendoerp.metadata.auth.TokenRevocationStore.isRevoked(org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);
  }

  @AfterEach
  void tearDown() {
    obContextStatic.close();
    expirationStatic.close();
    revocationStatic.close();
  }
```

(No changes needed to the `given(String pathInfo, boolean expired)` helper itself — revocation
defaults to `false` for every existing test via the `setUp` stub above, so none of them need to
know about it.)

Then add the new tests:

```java
  /**
   * Verifies that a request whose jti is revoked never reaches processing, and that the response
   * is set to 401 directly (not thrown as an exception the caller has to catch) - see the design
   * spec for why a thrown UnauthorizedException from this call site would not actually produce a
   * 401 in production.
   */
  @Test
  void testRevokedTokenBlocksProcessingAndWrites401() throws Exception {
    given(BLOCKED_PATH, false);
    when(request.getHeader("Authorization")).thenReturn("Bearer revoked-token");
    com.auth0.jwt.interfaces.DecodedJWT decoded = org.mockito.Mockito.mock(com.auth0.jwt.interfaces.DecodedJWT.class);
    com.auth0.jwt.interfaces.Claim jtiClaim = org.mockito.Mockito.mock(com.auth0.jwt.interfaces.Claim.class);
    when(jtiClaim.asString()).thenReturn("revoked-jti");
    when(decoded.getClaim("jti")).thenReturn(jtiClaim);

    try (MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsStatic =
             mockStatic(com.etendoerp.metadata.auth.Utils.class)) {
      authUtilsStatic.when(() -> com.etendoerp.metadata.auth.Utils.decodeBearerToken(request)).thenReturn(decoded);
      revocationStatic.when(() -> com.etendoerp.metadata.auth.TokenRevocationStore.isRevoked("revoked-jti"))
          .thenReturn(true);

      java.io.StringWriter body = new java.io.StringWriter();
      when(response.getWriter()).thenReturn(new java.io.PrintWriter(body));

      service.doGet("", request, response);

      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
    assertFalse(service.wasProcessed());
  }

  /**
   * Verifies that a non-revoked token is unaffected and reaches processing as normal.
   */
  @Test
  void testNonRevokedTokenReachesProcessing() throws Exception {
    given(BLOCKED_PATH, false);
    revocationStatic.when(() -> com.etendoerp.metadata.auth.TokenRevocationStore.isRevoked(org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);

    service.doGet("", request, response);

    assertTrue(service.wasProcessed());
  }
```

(Needs `import static org.mockito.Mockito.verify;` added to the file's existing static imports.)

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.http.BaseWebServiceGuardTest" 2>&1 | tail -40
```

Expected: FAIL — `testRevokedTokenBlocksProcessingAndWrites401` fails because `dispatch()`
doesn't check revocation yet (request reaches `process()`, `wasProcessed()` is `true`).

- [ ] **Step 3: Implement the check**

In `src/com/etendoerp/metadata/http/BaseWebService.java`:

Add imports:

```java
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.utils.Utils;
```

Change `dispatch()` from:

```java
    private void dispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        rejectIfPasswordExpired(request);
        process(request, response);
    }
```

to:

```java
    private void dispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (isTokenRevoked(request)) {
            Utils.writeJsonErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
            return;
        }
        rejectIfPasswordExpired(request);
        process(request, response);
    }

    /**
     * Checks the caller's {@code jti} against {@link TokenRevocationStore}. Deliberately does
     * <b>not</b> throw {@code UnauthorizedException} on a hit - an exception thrown from here
     * (before {@link #process}) never reaches this module's own exception-to-status mapping (see
     * the design spec, "Why not throw UnauthorizedException") - the caller must write the 401
     * response itself and return without calling {@link #process}.
     *
     * @param request the HTTP request
     * @return {@code true} if the request's token is revoked
     */
    private boolean isTokenRevoked(HttpServletRequest request) {
        DecodedJWT decoded = com.etendoerp.metadata.auth.Utils.decodeBearerToken(request);
        if (decoded == null) {
            return false;
        }
        String jti = decoded.getClaim("jti").asString();
        return TokenRevocationStore.isRevoked(jti);
    }
```

Note: `com.etendoerp.metadata.utils.Utils` (for `writeJsonErrorResponse`) and
`com.etendoerp.metadata.auth.Utils` (for `decodeBearerToken`) are two different classes with the
same simple name — the code above fully-qualifies the `auth` one inline to avoid an import
collision with the `utils.Utils` import already added above it.

- [ ] **Step 4: Run tests to verify they pass**

Same command as Step 2. Expected: PASS — including every pre-existing test in the file (they
default to `isRevoked(...) == false` via the `setUp` stub from Step 1).

- [ ] **Step 5: Commit**

```bash
git add src/com/etendoerp/metadata/http/BaseWebService.java src-test/src/com/etendoerp/metadata/http/BaseWebServiceGuardTest.java
git commit -m "Reject requests carrying a revoked JWT in BaseWebService.dispatch()"
```

---

### Task 7: Manual end-to-end verification

Unit tests mock `TokenRevocationStore`/`OBDal`, so nothing so far has exercised the real table
through a live request. Do this against a running local Etendo instance before considering the
feature done.

**Files:** none (verification only).

- [ ] **Step 1: Build and deploy**

Use the `/etendo:smartbuild` skill (or, directly: `./gradlew smartbuild` from the project root,
then ensure Tomcat is up).

- [ ] **Step 2: Log in and capture a token**

```bash
ETENDO_URL="http://localhost:8080/etendo"
TOKEN=$(curl -s -X POST "$ETENDO_URL/sws/com.etendoerp.metadata.meta/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo "$TOKEN"
```

- [ ] **Step 3: Confirm the token works before logout**

```bash
curl -s -o /dev/null -w "%{http_code}\n" "$ETENDO_URL/sws/com.etendoerp.metadata.meta/session" \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200`.

- [ ] **Step 4: Log out**

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$ETENDO_URL/sws/com.etendoerp.metadata.meta/logout" \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `200`.

- [ ] **Step 5: Confirm the same token is now rejected**

```bash
curl -s -o /dev/null -w "%{http_code}\n" "$ETENDO_URL/sws/com.etendoerp.metadata.meta/session" \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `401` — this is the actual checklist requirement (scenario 1), verified against the
real `SecureWebServiceServlet` → `BaseWebService.dispatch()` chain, not a mock.

- [ ] **Step 6: Confirm a fresh (non-revoked) token still works**

```bash
TOKEN2=$(curl -s -X POST "$ETENDO_URL/sws/com.etendoerp.metadata.meta/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
curl -s -o /dev/null -w "%{http_code}\n" "$ETENDO_URL/sws/com.etendoerp.metadata.meta/session" \
  -H "Authorization: Bearer $TOKEN2"
```

Expected: `200` (scenario 3).

- [ ] **Step 7: Confirm a password-expired account can still log out**

Force-expire a test user's password in the DB (or via the admin UI), log in as them to get a
token, then repeat Steps 4-5 with that token. Expected: logout still returns `200` and the
follow-up request still returns `401` (scenario 4 / the fix from Task 2).

- [ ] **Step 8: Record results**

No commit for this task — it's verification, not code. If any step doesn't match the expected
result, that's a bug in the prior tasks; go back and fix it (don't move on with a known-broken
manual check).

---

## Out of scope (per spec)

- Changes to `com.smf.securewebservices` — a JWT revoked here still works against any `/sws/*`
  endpoint outside this module.
- The frontend/BFF change to call `/logout` — separate repo (`app/api/auth/logout/route.ts`
  needs to add the `fetch` to this endpoint, best-effort, per the spec's Data Flow section).
- A scheduled cleanup job for `ETMETA_REVOKED_TOKEN` — `LogoutService`'s opportunistic delete is
  enough for now.

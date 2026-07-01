/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Language;

import com.etendoerp.metadata.service.AbstractMockedContextTest;
import com.etendoerp.metadata.utils.Constants;

/** Unit tests for {@link HttpCacheSupport}. */
@ExtendWith(MockitoExtension.class)
class HttpCacheSupportTest extends AbstractMockedContextTest {

    private static final String ROLE_ID = "role-1";
    private static final String LANGUAGE_ID = "lang-1";
    private static final String QUOTED_ETAG = "\"abc123\"";

    @Mock Role role;
    @Mock User user;
    @Mock Language language;
    @Mock Query<Date> query;

    @Test
    void isCacheableTrueForAllowlistedGetEndpoints() {
        assertTrue(HttpCacheSupport.isCacheable(mockRequest(Constants.GET), Constants.MENU_PATH));
        assertTrue(HttpCacheSupport.isCacheable(mockRequest(Constants.GET), Constants.SESSION_PATH));
        assertTrue(HttpCacheSupport.isCacheable(mockRequest(Constants.GET), Constants.LABELS_PATH));
        assertTrue(HttpCacheSupport.isCacheable(mockRequest(Constants.HEAD), Constants.MENU_PATH));
    }

    @Test
    void isCacheableFalseForNonGetMethods() {
        assertFalse(HttpCacheSupport.isCacheable(mockRequest(Constants.POST), Constants.MENU_PATH));
        assertFalse(HttpCacheSupport.isCacheable(mockRequest(Constants.PUT), Constants.SESSION_PATH));
    }

    @Test
    void isCacheableFalseForMutableEndpoints() {
        assertFalse(HttpCacheSupport.isCacheable(mockRequest(Constants.GET), Constants.TAB_PATH + "123"));
        assertFalse(HttpCacheSupport.isCacheable(mockRequest(Constants.GET), Constants.PROCESS_EXECUTION_PATH));
        assertFalse(HttpCacheSupport.isCacheable(mockRequest(Constants.GET), "/unknown"));
    }

    @Test
    void computeETagForMenuIsPresentAndStable() throws Exception {
        when(role.getId()).thenReturn(ROLE_ID);
        when(language.getId()).thenReturn(LANGUAGE_ID);
        when(obContext.getRole()).thenReturn(role);
        when(obContext.getLanguage()).thenReturn(language);
        when(session.createQuery(anyString(), eq(Date.class))).thenReturn(query);
        when(query.uniqueResult()).thenReturn(new Date(1000L));

        String[] etags = new String[2];

        runWithMockedContext(() -> etags[0] = HttpCacheSupport.computeETag(Constants.MENU_PATH));
        runWithMockedContext(() -> etags[1] = HttpCacheSupport.computeETag(Constants.MENU_PATH));

        assertNotNull(etags[0]);
        assertTrue(etags[0].startsWith("\"") && etags[0].endsWith("\""));
        assertEquals(etags[0], etags[1]);
    }

    @Test
    void computeETagForMenuChangesWhenMaxUpdatedChanges() throws Exception {
        when(role.getId()).thenReturn(ROLE_ID);
        when(language.getId()).thenReturn(LANGUAGE_ID);
        when(obContext.getRole()).thenReturn(role);
        when(obContext.getLanguage()).thenReturn(language);
        when(session.createQuery(anyString(), eq(Date.class))).thenReturn(query);

        String[] etags = new String[2];

        when(query.uniqueResult()).thenReturn(new Date(1000L));
        runWithMockedContext(() -> etags[0] = HttpCacheSupport.computeETag(Constants.MENU_PATH));

        when(query.uniqueResult()).thenReturn(new Date(2000L));
        runWithMockedContext(() -> etags[1] = HttpCacheSupport.computeETag(Constants.MENU_PATH));

        assertNotEquals(etags[0], etags[1]);
    }

    @Test
    void computeETagForMenuNullWhenRoleMissing() throws Exception {
        when(obContext.getRole()).thenReturn(null);

        String[] etag = new String[1];
        runWithMockedContext(() -> etag[0] = HttpCacheSupport.computeETag(Constants.MENU_PATH));

        assertNull(etag[0]);
    }

    @Test
    void computeETagForSessionUsesUserAndRole() throws Exception {
        when(user.getId()).thenReturn("user-1");
        when(role.getId()).thenReturn(ROLE_ID);
        when(obContext.getUser()).thenReturn(user);
        when(obContext.getRole()).thenReturn(role);
        when(session.createQuery(argThat(s -> s != null && s.contains("ADRole")), eq(Date.class)))
                .thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(new Date(5000L));

        String[] etag = new String[1];
        runWithMockedContext(() -> etag[0] = HttpCacheSupport.computeETag(Constants.SESSION_PATH));

        assertNotNull(etag[0]);
    }

    @Test
    void computeETagForSessionNullWhenUserMissing() throws Exception {
        when(obContext.getUser()).thenReturn(null);

        String[] etag = new String[1];
        runWithMockedContext(() -> etag[0] = HttpCacheSupport.computeETag(Constants.SESSION_PATH));

        assertNull(etag[0]);
    }

    @Test
    void computeETagForLabelsUsesLanguageAndModuleVersions() throws Exception {
        when(language.getId()).thenReturn(LANGUAGE_ID);
        when(obContext.getLanguage()).thenReturn(language);
        when(session.createQuery(anyString(), eq(Date.class))).thenReturn(query);
        when(query.uniqueResult()).thenReturn(new Date(9000L));

        String[] etag = new String[1];
        runWithMockedContext(() -> etag[0] = HttpCacheSupport.computeETag(Constants.LABELS_PATH));

        assertNotNull(etag[0]);
    }

    @Test
    void computeETagForLabelsNullWhenLanguageMissing() throws Exception {
        when(obContext.getLanguage()).thenReturn(null);

        String[] etag = new String[1];
        runWithMockedContext(() -> etag[0] = HttpCacheSupport.computeETag(Constants.LABELS_PATH));

        assertNull(etag[0]);
    }

    @Test
    void matchesReturnsTrueForWildcard() {
        assertTrue(HttpCacheSupport.matches("*", QUOTED_ETAG));
    }

    @Test
    void matchesReturnsTrueForExactSingleValue() {
        assertTrue(HttpCacheSupport.matches(QUOTED_ETAG, QUOTED_ETAG));
    }

    @Test
    void matchesReturnsTrueForCommaSeparatedMultiValue() {
        assertTrue(HttpCacheSupport.matches("\"zzz\", " + QUOTED_ETAG + ", \"yyy\"", QUOTED_ETAG));
    }

    @Test
    void matchesReturnsFalseForMismatch() {
        assertFalse(HttpCacheSupport.matches("\"other\"", QUOTED_ETAG));
    }

    @Test
    void matchesReturnsFalseForNullOrBlankHeader() {
        assertFalse(HttpCacheSupport.matches(null, QUOTED_ETAG));
        assertFalse(HttpCacheSupport.matches("", QUOTED_ETAG));
    }

    private HttpServletRequest mockRequest(String method) {
        HttpServletRequest req = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        return req;
    }
}

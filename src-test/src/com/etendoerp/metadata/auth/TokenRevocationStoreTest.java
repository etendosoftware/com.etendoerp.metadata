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

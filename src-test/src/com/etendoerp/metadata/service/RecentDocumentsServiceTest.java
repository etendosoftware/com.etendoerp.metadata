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

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.data.RecentDocument;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.common.enterprise.Organization;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecentDocumentsServiceTest extends AbstractMockedContextTest {

    private static final String USER_ID = "user-001";
    private static final String ROLE_ID = "role-001";
    private static final String WINDOW_ID = "window-001";
    private static final String TAB_ID = "tab-001";
    private static final String RECORD_ID = "record-001";
    private static final String IDENTIFIER_VALUE = "SO-001";
    private static final String TRACK_BODY = "{\"windowId\":\"" + WINDOW_ID + "\",\"tabId\":\"" + TAB_ID
            + "\",\"recordId\":\"" + RECORD_ID + "\",\"identifier\":\"" + IDENTIFIER_VALUE + "\",\"tabLevel\":0}";

    @Mock OBProvider obProvider;

    private void runWithRecentDocumentsContext(ThrowingRunnable action) throws Exception {
        try (MockedStatic<OBProvider> providerStatic = mockStatic(OBProvider.class)) {
            providerStatic.when(OBProvider::getInstance).thenReturn(obProvider);

            runWithMockedContext(() -> {
                lenient().when(obContext.getCurrentClient()).thenReturn(mock(Client.class));
                lenient().when(obContext.getCurrentOrganization()).thenReturn(mock(Organization.class));

                User mockUser = mock(User.class);
                lenient().when(mockUser.getId()).thenReturn(USER_ID);
                lenient().when(obContext.getUser()).thenReturn(mockUser);

                Role mockRole = mock(Role.class);
                lenient().when(mockRole.getId()).thenReturn(ROLE_ID);
                lenient().when(obContext.getRole()).thenReturn(mockRole);

                action.run();
            });
        }
    }

    private void setRequestBody(String body) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    @SuppressWarnings("unchecked")
    private void setupFindExistingQuery(RecentDocument existing) {
        Query<RecentDocument> findQuery = mock(Query.class);
        when(session.createQuery(any(String.class), eq(RecentDocument.class))).thenReturn(findQuery);
        when(findQuery.setParameter(anyString(), anyString())).thenReturn(findQuery);
        when(findQuery.setMaxResults(anyInt())).thenReturn(findQuery);
        when(findQuery.uniqueResult()).thenReturn(existing);
    }

    @SuppressWarnings("unchecked")
    private void setupTrimQuery(List<String> ids) {
        Query<String> trimQuery = mock(Query.class);
        when(session.createQuery(any(String.class), eq(String.class))).thenReturn(trimQuery);
        when(trimQuery.setParameter(anyString(), anyString())).thenReturn(trimQuery);
        when(trimQuery.list()).thenReturn(ids);
    }

    @SuppressWarnings("unchecked")
    private Query<Object> setupDeleteByIdsQuery() {
        Query<Object> deleteQuery = mock(Query.class);
        when(session.createQuery(any(String.class))).thenReturn(deleteQuery);
        when(deleteQuery.setParameterList(anyString(), any(List.class))).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);
        return deleteQuery;
    }

    @Test
    void processThrowsNotFoundForUnsupportedMethod() throws Exception {
        runWithRecentDocumentsContext(() -> {
            when(request.getMethod()).thenReturn("DELETE");
            RecentDocumentsService svc = new RecentDocumentsService(request, response);
            assertThrows(NotFoundException.class, svc::process);
        });
    }

    @Test
    void processThrowsInternalServerOnInvalidJson() throws Exception {
        runWithRecentDocumentsContext(() -> {
            when(request.getMethod()).thenReturn("POST");
            setRequestBody("not-valid-json");
            RecentDocumentsService svc = new RecentDocumentsService(request, response);
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void getReturnsItemsOrderedFromTheListQuery() throws Exception {
        runWithRecentDocumentsContext(() -> {
            when(request.getMethod()).thenReturn("GET");

            Query<Object[]> listQuery = mock(Query.class);
            when(session.createQuery(any(String.class), eq(Object[].class))).thenReturn(listQuery);
            when(listQuery.setParameter(anyString(), anyString())).thenReturn(listQuery);
            when(listQuery.setMaxResults(anyInt())).thenReturn(listQuery);
            Object[] row = { RECORD_ID, IDENTIFIER_VALUE, WINDOW_ID, "Sales Order", TAB_ID, 0L, new Date(1000L) };
            when(listQuery.list()).thenReturn(Collections.singletonList(row));

            RecentDocumentsService svc = new RecentDocumentsService(request, response);
            svc.process();

            String output = responseCapture.toString();
            assertTrue(output.contains(RECORD_ID));
            assertTrue(output.contains(IDENTIFIER_VALUE));
            assertTrue(output.contains(WINDOW_ID));
            assertTrue(output.contains("Sales Order"));
        });
    }

    @Test
    void postCreatesNewDocumentWhenNoneExists() throws Exception {
        runWithRecentDocumentsContext(() -> {
            when(request.getMethod()).thenReturn("POST");
            setRequestBody(TRACK_BODY);

            setupFindExistingQuery(null);
            setupTrimQuery(Collections.singletonList("only-doc-id"));

            RecentDocument mockDoc = mock(RecentDocument.class);
            when(obProvider.get(RecentDocument.class)).thenReturn(mockDoc);
            when(obDal.get(User.class, USER_ID)).thenReturn(mock(User.class));
            when(obDal.get(Role.class, ROLE_ID)).thenReturn(mock(Role.class));
            when(obDal.get(Window.class, WINDOW_ID)).thenReturn(mock(Window.class));
            when(obDal.get(Tab.class, TAB_ID)).thenReturn(mock(Tab.class));

            RecentDocumentsService svc = new RecentDocumentsService(request, response);
            svc.process();

            verify(mockDoc).setRecordID(RECORD_ID);
            verify(mockDoc).setIdentifier(IDENTIFIER_VALUE);
            verify(mockDoc).setTabLevel(0L);
            verify(session).saveOrUpdate(mockDoc);
            verify(obDal, times(1)).flush();

            String output = responseCapture.toString();
            assertTrue(output.contains("ok"));
        });
    }

    @Test
    void postUpdatesExistingDocumentInPlace() throws Exception {
        runWithRecentDocumentsContext(() -> {
            when(request.getMethod()).thenReturn("POST");
            setRequestBody(TRACK_BODY);

            RecentDocument existing = mock(RecentDocument.class);
            setupFindExistingQuery(existing);
            setupTrimQuery(Collections.singletonList("only-doc-id"));

            RecentDocumentsService svc = new RecentDocumentsService(request, response);
            svc.process();

            verify(existing, never()).setRecordID(anyString());
            verify(existing).setIdentifier(IDENTIFIER_VALUE);
            verify(existing).setTabLevel(0L);
            verify(session).saveOrUpdate(existing);
            verify(obProvider, never()).get(RecentDocument.class);
        });
    }

    @Test
    void postTrimsRowsBeyondMaxRecentDocuments() throws Exception {
        runWithRecentDocumentsContext(() -> {
            when(request.getMethod()).thenReturn("POST");
            setRequestBody(TRACK_BODY);

            RecentDocument existing = mock(RecentDocument.class);
            setupFindExistingQuery(existing);

            List<String> elevenIds = Arrays.asList(
                    "id-1", "id-2", "id-3", "id-4", "id-5", "id-6", "id-7", "id-8", "id-9", "id-10", "id-11");
            setupTrimQuery(elevenIds);
            Query<Object> deleteQuery = setupDeleteByIdsQuery();

            RecentDocumentsService svc = new RecentDocumentsService(request, response);
            svc.process();

            verify(deleteQuery).setParameterList("ids", elevenIds.subList(10, 11));
            verify(deleteQuery).executeUpdate();
        });
    }
}

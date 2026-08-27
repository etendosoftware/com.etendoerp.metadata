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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.DELETE;
import static com.etendoerp.metadata.utils.Constants.GET;
import static com.etendoerp.metadata.utils.Constants.POST;
import static com.etendoerp.metadata.utils.Constants.PUT;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.data.SavedView;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Unit tests for {@link SavedViewService}'s scope precedence resolution and scope write
 * authorization (USER &gt; ROLE &gt; ORGANIZATION &gt; CLIENT &gt; SYSTEM), split out from
 * {@link SavedViewServiceTest} to keep each test class focused on a single concern.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
@SuppressWarnings("unchecked")
public class SavedViewServiceScopeTest {

    private static final String VIEW_ID = "view-abc-123";
    private static final String VIEW_NAME = "My Saved View";
    private static final String TAB_ID = "tab-xyz-456";
    private static final String USER_ID = "user-001";
    private static final String CLIENT_ID = "client-001";
    private static final String ORG_ID = "org-001";
    private static final String ROLE_ID = "role-001";
    private static final String SYSTEM_ID = "0";
    private static final String SAVED_VIEW_BASE_PATH = "/saved-views";
    private static final String PATH_WITH_ID = "/saved-views/" + VIEW_ID;
    private static final String RESPONSE_CONTAINS_VIEW_ID = "Response should contain view ID";
    private static final String ISDEFAULT_PARAM = "isdefault";
    private static final String JSON_NAME_PREFIX = "{\"name\":\"";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private OBDal mockOBDal;
    @Mock private OBContext mockOBContext;
    @Mock private OBProvider mockOBProvider;
    @Mock private SavedView mockView;
    @Mock private Tab mockTab;
    @Mock private User mockUser;
    @Mock private Client mockClient;
    @Mock private Organization mockOrg;
    @Mock private Role mockRole;
    @Mock private OBCriteria<SavedView> mockCriteria;

    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<OBProvider> obProviderMock;

    private StringWriter responseWriter;
    private SavedViewService service;

    /**
     * Initialises static mocks, common stub chains, and the service under test
     * before each test method.
     *
     * @throws IOException if the response writer cannot be configured
     */
    @Before
    public void setUp() throws IOException {
        responseWriter = new StringWriter();
        // A fresh PrintWriter per call: write() closes it via try-with-resources, and some
        // tests invoke process() more than once against the same mocked response.
        when(mockResponse.getWriter()).thenAnswer(inv -> new PrintWriter(responseWriter));

        obDalMock = mockStatic(OBDal.class);
        obContextMock = mockStatic(OBContext.class);
        obProviderMock = mockStatic(OBProvider.class);

        obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
        obContextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
        obProviderMock.when(OBProvider::getInstance).thenReturn(mockOBProvider);
        obContextMock.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(inv -> null);
        obContextMock.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);

        when(mockView.getId()).thenReturn(VIEW_ID);
        when(mockView.getName()).thenReturn(VIEW_NAME);
        when(mockView.getTab()).thenReturn(mockTab);
        when(mockTab.getId()).thenReturn(TAB_ID);
        when(mockView.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockView.isDefault()).thenReturn(false);
        when(mockView.isActive()).thenReturn(true);
        when(mockView.getFilterclause()).thenReturn(null);
        when(mockView.getGridconfiguration()).thenReturn(null);

        when(mockOBContext.getUser()).thenReturn(mockUser);
        when(mockOBContext.getCurrentClient()).thenReturn(mockClient);
        when(mockOBContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockOBContext.getRole()).thenReturn(mockRole);
        // "O" (Organization-level only) models a regular, non-administrator business role.
        when(mockOBContext.getUserLevel()).thenReturn("O");
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockRole.getId()).thenReturn(ROLE_ID);
        when(mockOBDal.get(Client.class, CLIENT_ID)).thenReturn(mockClient);
        when(mockOBDal.get(Organization.class, ORG_ID)).thenReturn(mockOrg);
        when(mockOBDal.get(User.class, USER_ID)).thenReturn(mockUser);
        when(mockOBDal.get(Role.class, ROLE_ID)).thenReturn(mockRole);

        when(mockRequest.getPathInfo()).thenReturn(PATH_WITH_ID);
        service = new SavedViewService(mockRequest, mockResponse);
    }

    /**
     * Closes all static mocks and clears thread-local state after each test.
     */
    @After
    public void tearDown() {
        if (obDalMock != null) obDalMock.close();
        if (obContextMock != null) obContextMock.close();
        if (obProviderMock != null) obProviderMock.close();
        MetadataService.clear();
    }

    /** Stubs {@code mockView} as an active, no-owner ROLE-scoped shared view. */
    private void givenSharedRoleView() {
        when(mockView.getUser()).thenReturn(null);
        when(mockView.getRole()).thenReturn(mockRole);
    }

    // --- Scoped default resolution (USER > ROLE > ORGANIZATION > CLIENT > SYSTEM) ---

    /**
     * Scenario: Role default view applied to user without own view, and role view
     * prevails over an eventual system view. Both BDD scenarios exercise the same
     * precedence path: the user-scope query returns nothing, so resolution falls
     * through to (and stops at) the role-scope query, never reaching org/client/system.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testResolveEffectiveDefaultRoleAppliedWhenNoUserView() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("true");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList(), Collections.singletonList(mockView));

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
        verify(mockCriteria, times(2)).list();
    }

    /**
     * Scenario: User view prevails over role view.
     * The user-scope query already returns a match, so resolution must stop
     * there without ever querying role/org/client/system scope.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testResolveEffectiveDefaultUserPrevailsOverRole() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("true");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(mockView));

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
        verify(mockCriteria, times(1)).list();
    }

    /**
     * Scenario: no view at any scope resolves to an empty (not missing) response,
     * falling through all five precedence levels.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testResolveEffectiveDefaultNoViewAtAnyScopeReturnsEmptyList() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("true");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        service.process();

        assertTrue("Response should contain totalRows: 0", responseWriter.toString().contains("\"totalRows\":0"));
        verify(mockCriteria, times(5)).list();
    }

    /**
     * Scenario: deleting the user's own default view falls back to the role/system view.
     * First the user's own view is deleted; a subsequent default lookup then finds
     * nothing at user scope and falls back to the role-scope view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeletingOwnDefaultFallsBackToRoleView() throws IOException {
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockRequest.getPathInfo()).thenReturn(PATH_WITH_ID);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();

        verify(mockOBDal).remove(mockView);

        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("true");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList(), Collections.singletonList(mockView));

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
    }

    // --- Scope write authorization ---

    /**
     * Verifies that a regular (Organization-level only) role cannot create a ROLE-scoped
     * shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnauthorizedException.class)
    public void testHandlePostRoleScopeRejectedForRegularUser() throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ROLE\"}")));

        service.process();
    }

    /**
     * Verifies that a Client Administrator role (userLevel contains "C") can create a
     * ROLE-scoped shared view, and that it is persisted with user=null, role=&lt;role&gt;.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostRoleScopeAllowedForClientAdmin() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn("CO");
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ROLE\",\"tab\":\"" + TAB_ID + "\"}")));
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);

        service.process();

        verify(mockView).setRole(mockRole);
        verify(mockView).setUser(null);
        verify(mockOBDal).save(mockView);
    }

    /**
     * Verifies that a System Administrator role (userLevel contains "S") can create a
     * SYSTEM-scoped shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostSystemScopeAllowedForSystemAdmin() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn("SCO");
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"SYSTEM\",\"tab\":\"" + TAB_ID + "\"}")));
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);
        when(mockOBDal.get(Client.class, SYSTEM_ID)).thenReturn(mockClient);
        when(mockOBDal.get(Organization.class, SYSTEM_ID)).thenReturn(mockOrg);

        service.process();

        verify(mockOBDal).save(mockView);
    }

    /**
     * Verifies that a Client Administrator role (missing "S") cannot create a
     * SYSTEM-scoped shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnauthorizedException.class)
    public void testHandlePostSystemScopeRejectedForClientAdmin() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn("CO");
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"SYSTEM\"}")));

        service.process();
    }

    /**
     * Verifies that a non-owner, non-administrator user cannot edit a ROLE-scoped shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnauthorizedException.class)
    public void testHandlePutSharedRoleViewRejectedForNonOwnerRegularUser() throws IOException {
        givenSharedRoleView();
        when(mockRequest.getMethod()).thenReturn(PUT);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{\"name\":\"X\"}")));

        service.process();
    }

    /**
     * Verifies that a Client Administrator (non-owner) can edit a ROLE-scoped shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePutSharedRoleViewAllowedForClientAdmin() throws IOException {
        givenSharedRoleView();
        when(mockOBContext.getUserLevel()).thenReturn("CO");
        when(mockRequest.getMethod()).thenReturn(PUT);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{\"name\":\"Updated\"}")));

        service.process();

        verify(mockOBDal).save(mockView);
        verify(mockOBDal).flush();
    }

    /**
     * Verifies that a non-owner, non-administrator user cannot delete a ROLE-scoped shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnauthorizedException.class)
    public void testHandleDeleteSharedRoleViewRejectedForNonOwnerRegularUser() throws IOException {
        givenSharedRoleView();
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();
    }
}

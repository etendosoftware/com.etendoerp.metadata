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
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.data.SavedView;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Unit tests for {@link SavedViewService}'s scope precedence resolution and scope write
 * authorization (USER &gt; ROLE &gt; ORGANIZATION &gt; CLIENT &gt; SYSTEM), split out from
 * {@link SavedViewServiceTest} to keep each test class focused on a single concern.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
@SuppressWarnings("unchecked")
public class SavedViewServiceScopeTest extends AbstractSavedViewServiceTest {

    private static final String SYSTEM_ID = "0";
    private static final String JSON_NAME_PREFIX = "{\"name\":\"";

    /** Stubs {@code mockView} as an active, no-owner ROLE-scoped shared view. */
    private void givenSharedRoleView() {
        when(mockView.getUser()).thenReturn(null);
        when(mockView.getRole()).thenReturn(mockRole);
    }

    /** Stubs {@code mockView} as owned by neither a user nor a role (org/client/system scope). */
    private void givenNoOwnerNoRole() {
        when(mockView.getUser()).thenReturn(null);
        when(mockView.getRole()).thenReturn(null);
    }

    /** Stubs a POST request whose body is {@code bodyJson}. */
    private void givenPostRequest(String bodyJson) throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(bodyJson)));
    }

    /** Stubs a GET-by-id request resolving {@link #mockView} for {@link #VIEW_ID}. */
    private void givenGetByIdRequest() {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
    }

    /** Stubs {@code mockView}'s organization to a fresh mock reporting the given id. */
    private void givenViewOrganizationId(String id) {
        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(id);
        when(mockView.getOrganization()).thenReturn(org);
    }

    /** Stubs {@code mockView}'s client to a fresh mock reporting the given id. */
    private void givenViewClientId(String id) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(id);
        when(mockView.getClient()).thenReturn(client);
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
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ROLE\"}");

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
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ROLE\",\"tab\":\"" + TAB_ID + "\"}");
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
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"SYSTEM\",\"tab\":\"" + TAB_ID + "\"}");
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
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"SYSTEM\"}");

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

    /**
     * Verifies that a Client Administrator role can create an ORGANIZATION-scoped shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostOrganizationScopeAllowedForClientAdmin() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn("CO");
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ORGANIZATION\",\"tab\":\"" + TAB_ID + "\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);

        service.process();

        verify(mockView).setOrganization(mockOrg);
        verify(mockView).setUser(null);
        verify(mockOBDal).save(mockView);
    }

    /**
     * Verifies that a Client Administrator role can create a CLIENT-scoped shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostClientScopeAllowedForClientAdmin() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn("CO");
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"CLIENT\",\"tab\":\"" + TAB_ID + "\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);
        when(mockOBDal.get(Organization.class, SYSTEM_ID)).thenReturn(mockOrg);

        service.process();

        verify(mockView).setClient(mockClient);
        verify(mockView).setUser(null);
        verify(mockOBDal).save(mockView);
    }

    /**
     * Verifies that setting isdefault=true on a new view atomically clears any other
     * active default sharing the same (tab, scope target) — here, the same user.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostSetsDefaultClearsSiblingUserDefault() throws IOException {
        SavedView mockSibling = mock(SavedView.class);
        when(mockView.isDefault()).thenReturn(true);
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"tab\":\"" + TAB_ID + "\",\"isdefault\":true}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(mockSibling));

        service.process();

        verify(mockSibling).setDefault(false);
        verify(mockOBDal).save(mockSibling);
        verify(mockOBDal).save(mockView);
    }

    /**
     * Verifies that GET by id is visible for a ROLE-scoped view matching the current role.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandleGetByIdVisibleForMatchingRole() throws IOException {
        givenSharedRoleView();
        givenGetByIdRequest();

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
    }

    /**
     * Verifies that GET by id is visible for an ORGANIZATION-scoped view matching the
     * current organization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandleGetByIdVisibleForMatchingOrganization() throws IOException {
        givenNoOwnerNoRole();
        when(mockView.getOrganization()).thenReturn(mockOrg);
        givenGetByIdRequest();

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
    }

    /**
     * Verifies that GET by id is visible for a CLIENT-scoped view (organization "*") matching
     * the current client.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandleGetByIdVisibleForMatchingClientWhenOrgIsSystem() throws IOException {
        givenNoOwnerNoRole();
        givenViewOrganizationId(SYSTEM_ID);
        when(mockView.getClient()).thenReturn(mockClient);
        givenGetByIdRequest();

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
    }

    /**
     * Verifies that GET by id is visible for a SYSTEM-scoped view (client and organization
     * both "*"/System) regardless of the current client/organization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandleGetByIdVisibleForSystemScope() throws IOException {
        givenNoOwnerNoRole();
        givenViewOrganizationId(SYSTEM_ID);
        givenViewClientId(SYSTEM_ID);
        givenGetByIdRequest();

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
    }

    /**
     * Verifies that GET by id hides a shared view scoped to a different organization,
     * masking its existence as a 404 rather than a 403.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testHandleGetByIdNotVisibleForOtherOrganizationThrowsNotFound() throws IOException {
        givenNoOwnerNoRole();
        givenViewOrganizationId("other-org-999");
        givenGetByIdRequest();

        service.process();
    }
}

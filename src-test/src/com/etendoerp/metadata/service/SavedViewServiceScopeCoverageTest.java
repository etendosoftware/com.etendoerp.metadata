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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.DELETE;
import static com.etendoerp.metadata.utils.Constants.GET;
import static com.etendoerp.metadata.utils.Constants.POST;
import static com.etendoerp.metadata.utils.Constants.PUT;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.data.SavedView;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Additional unit tests for {@link SavedViewService} covering the scope branches not exercised by
 * {@link SavedViewServiceScopeTest}: fallback through the organization/client/system defaults,
 * scope target validation, per-scope sibling default clearing, and per-scope visibility and
 * editability.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
@SuppressWarnings("unchecked")
public class SavedViewServiceScopeCoverageTest extends AbstractSavedViewServiceTest {

    private static final String SYSTEM_ID = "0";
    private static final String OTHER_ID = "other-999";
    private static final String TAB_PARAM = "tab";
    private static final String JSON_NAME_PREFIX = "{\"name\":\"";
    private static final String ADMIN_LEVEL = "CO";
    private static final String SYSTEM_ADMIN_LEVEL = "SCO";

    /** Stubs {@code mockView} as owned by neither a user nor a role. */
    private void givenNoOwnerNoRole() {
        when(mockView.getUser()).thenReturn(null);
        when(mockView.getRole()).thenReturn(null);
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

    /** Stubs a "default view" lookup for {@link #TAB_ID}; {@code mockCriteria.list()} is left to the caller. */
    private void givenDefaultLookupRequest() {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter(TAB_PARAM)).thenReturn(TAB_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("true");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
    }

    /** Stubs a PUT of {@code {"isdefault":true}} on {@link #mockView} as an administrator. */
    private void givenPutDefaultRequest(String userLevel) {
        when(mockOBContext.getUserLevel()).thenReturn(userLevel);
        when(mockRequest.getMethod()).thenReturn(PUT);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockView.isDefault()).thenReturn(true);
        try {
            when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{\"isdefault\":true}")));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
    }

    // --- Default resolution fallthrough ---

    /**
     * With no user or role default, the organization-scoped default is returned.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testResolveEffectiveDefaultFallsBackToOrganizationView() throws IOException {
        givenDefaultLookupRequest();
        when(mockCriteria.list()).thenReturn(Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(mockView));

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
        verify(mockCriteria, times(3)).list();
    }

    /**
     * With no user, role or organization default, the client-scoped default is returned.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testResolveEffectiveDefaultFallsBackToClientView() throws IOException {
        givenDefaultLookupRequest();
        when(mockCriteria.list()).thenReturn(Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.singletonList(mockView));

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
        verify(mockCriteria, times(4)).list();
    }

    /**
     * With no default at any more specific scope, the system-scoped default is returned.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testResolveEffectiveDefaultFallsBackToSystemView() throws IOException {
        givenDefaultLookupRequest();
        when(mockCriteria.list()).thenReturn(Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.singletonList(mockView));

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
        verify(mockCriteria, times(5)).list();
    }

    /**
     * Asking for the default without a tab cannot be resolved by precedence, so the plain
     * visibility-filtered list is returned instead.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testGetDefaultWithoutTabReturnsVisibilityFilteredList() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("true");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(mockView));

        service.process();

        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
        verify(mockCriteria, times(1)).list();
    }

    // --- Scope target validation ---

    /**
     * A ROLE-scoped view can target an explicitly given role.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostRoleScopeWithExplicitRole() throws IOException {
        Role otherRole = mock(Role.class);
        when(otherRole.getId()).thenReturn(OTHER_ID);
        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ROLE\",\"role\":\"" + OTHER_ID + "\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Role.class, OTHER_ID)).thenReturn(otherRole);

        service.process();

        verify(mockView).setRole(otherRole);
    }

    /**
     * A ROLE-scoped view targeting an unknown role is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testHandlePostRoleScopeWithUnknownRoleRejected() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ROLE\",\"role\":\"missing\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);

        service.process();
    }

    /**
     * An ORGANIZATION-scoped view can target an explicitly given organization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostOrganizationScopeWithExplicitOrganization() throws IOException {
        Organization otherOrg = mock(Organization.class);
        when(otherOrg.getId()).thenReturn(OTHER_ID);
        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ORGANIZATION\",\"organization\":\""
            + OTHER_ID + "\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Organization.class, OTHER_ID)).thenReturn(otherOrg);

        service.process();

        verify(mockView).setOrganization(otherOrg);
        verify(mockView).setClient(mockClient);
    }

    /**
     * An ORGANIZATION-scoped view targeting an unknown organization is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testHandlePostOrganizationScopeWithUnknownOrganizationRejected() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ORGANIZATION\",\"organization\":\"missing\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);

        service.process();
    }

    /**
     * The "*" (system) organization is not a valid target for an ORGANIZATION-scoped view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testHandlePostOrganizationScopeWithSystemOrganizationRejected() throws IOException {
        Organization systemOrg = mock(Organization.class);
        when(systemOrg.getId()).thenReturn(SYSTEM_ID);
        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"ORGANIZATION\",\"organization\":\""
            + SYSTEM_ID + "\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Organization.class, SYSTEM_ID)).thenReturn(systemOrg);

        service.process();
    }

    /**
     * An unrecognized scope is treated as a personal (USER) view, which any user may create.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePostUnknownScopeFallsBackToUserScope() throws IOException {
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"bogus\"}");
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);

        service.process();

        verify(mockView).setUser(mockUser);
        verify(mockOBDal).save(mockView);
    }

    /**
     * A session without any user level cannot create a shared view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnauthorizedException.class)
    public void testHandlePostSharedScopeRejectedWhenUserLevelIsNull() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn(null);
        givenPostRequest(JSON_NAME_PREFIX + VIEW_NAME + "\",\"scope\":\"CLIENT\"}");

        service.process();
    }

    // --- Sibling default clearing per scope ---

    /**
     * Making a ROLE-scoped view the default clears the other defaults of the same role.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePutDefaultClearsSiblingRoleDefault() throws IOException {
        SavedView sibling = mock(SavedView.class);
        when(mockView.getUser()).thenReturn(null);
        when(mockView.getRole()).thenReturn(mockRole);
        givenPutDefaultRequest(ADMIN_LEVEL);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(sibling));

        service.process();

        verify(sibling).setDefault(false);
        verify(mockOBDal).save(sibling);
    }

    /**
     * Making an ORGANIZATION-scoped view the default clears the other defaults of that organization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePutDefaultClearsSiblingOrganizationDefault() throws IOException {
        SavedView sibling = mock(SavedView.class);
        givenNoOwnerNoRole();
        givenViewOrganizationId(ORG_ID);
        givenPutDefaultRequest(ADMIN_LEVEL);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(sibling));

        service.process();

        verify(sibling).setDefault(false);
        verify(mockOBDal).save(sibling);
    }

    /**
     * Making a CLIENT-scoped view the default clears the other defaults of that client.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePutDefaultClearsSiblingClientDefault() throws IOException {
        SavedView sibling = mock(SavedView.class);
        givenNoOwnerNoRole();
        givenViewOrganizationId(SYSTEM_ID);
        givenViewClientId(CLIENT_ID);
        givenPutDefaultRequest(ADMIN_LEVEL);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(sibling));

        service.process();

        verify(sibling).setDefault(false);
        verify(mockOBDal).save(sibling);
    }

    /**
     * Making a SYSTEM-scoped view the default clears the other system defaults.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandlePutDefaultClearsSiblingSystemDefault() throws IOException {
        SavedView sibling = mock(SavedView.class);
        givenNoOwnerNoRole();
        givenViewOrganizationId(SYSTEM_ID);
        givenViewClientId(SYSTEM_ID);
        givenPutDefaultRequest(SYSTEM_ADMIN_LEVEL);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(sibling));

        service.process();

        verify(sibling).setDefault(false);
        verify(mockOBDal).save(sibling);
    }

    /**
     * A client administrator cannot modify a SYSTEM-scoped view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnauthorizedException.class)
    public void testHandleDeleteSystemViewRejectedForClientAdmin() throws IOException {
        givenNoOwnerNoRole();
        givenViewOrganizationId(SYSTEM_ID);
        givenViewClientId(SYSTEM_ID);
        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();
    }

    // --- Visibility and editability ---

    /**
     * A view owned by another user is reported as not found.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testHandleGetByIdOwnedByAnotherUserThrowsNotFound() throws IOException {
        org.openbravo.model.ad.access.User otherUser = mock(org.openbravo.model.ad.access.User.class);
        when(otherUser.getId()).thenReturn(OTHER_ID);
        when(mockView.getUser()).thenReturn(otherUser);
        givenGetByIdRequest();

        service.process();
    }

    /**
     * A ROLE-scoped view of a different role is reported as not found.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testHandleGetByIdOfAnotherRoleThrowsNotFound() throws IOException {
        Role otherRole = mock(Role.class);
        when(otherRole.getId()).thenReturn(OTHER_ID);
        when(mockView.getUser()).thenReturn(null);
        when(mockView.getRole()).thenReturn(otherRole);
        givenGetByIdRequest();

        service.process();
    }

    /**
     * A CLIENT-scoped view of a different client is reported as not found.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testHandleGetByIdOfAnotherClientThrowsNotFound() throws IOException {
        givenNoOwnerNoRole();
        givenViewOrganizationId(SYSTEM_ID);
        givenViewClientId(OTHER_ID);
        givenGetByIdRequest();

        service.process();
    }

    /**
     * A shared ORGANIZATION view is returned to a regular user as read-only.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandleGetByIdOrganizationViewIsReadOnlyForRegularUser() throws IOException {
        givenNoOwnerNoRole();
        givenViewOrganizationId(ORG_ID);
        givenGetByIdRequest();

        service.process();

        String body = responseWriter.toString();
        assertTrue("Scope should be ORGANIZATION", body.contains("\"scope\":\"ORGANIZATION\""));
        assertTrue("A regular user cannot edit a shared view", body.contains("\"editable\":false"));
    }

    /**
     * A shared CLIENT view is returned to a client administrator as editable.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testHandleGetByIdClientViewIsEditableForClientAdmin() throws IOException {
        givenNoOwnerNoRole();
        givenViewOrganizationId(SYSTEM_ID);
        givenViewClientId(CLIENT_ID);
        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        givenGetByIdRequest();

        service.process();

        String body = responseWriter.toString();
        assertTrue("Scope should be CLIENT", body.contains("\"scope\":\"CLIENT\""));
        assertTrue("A client admin can edit a client view", body.contains("\"editable\":true"));
    }
}

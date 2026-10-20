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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.ApplicationUtils;
import org.openbravo.client.application.UIPersonalization;
import org.openbravo.client.application.personalization.PersonalizationHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.exceptions.ForbiddenException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Unit tests for {@link FormPersonalizationService}: what a session reads, which levels it may save at
 * and the permission checks on saving and discarding.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FormPersonalizationServiceTest {

    private static final String PATH = "/form-personalization";
    private static final String TAB_ID = "tab-1";
    private static final String CLIENT_ID = "client-1";
    private static final String ORG_ID = "org-1";
    private static final String ROLE_ID = "role-1";
    private static final String USER_ID = "user-1";
    private static final String OTHER_ID = "other-9";
    private static final String SYSTEM_ID = "0";
    private static final String PERSONALIZATION_ID = "pers-1";
    private static final String FORM_BODY = "\"form\":{\"fields\":[{\"name\":\"a\",\"colSpan\":2}]}";
    private static final String TAB_FIELD = "\"tab\":\"" + TAB_ID + "\"";
    private static final String SCOPE_KEY = "scope";
    private static final String PERSONALIZATION_KEY = "personalization";
    private static final String PERMISSIONS_KEY = "permissions";
    private static final String VALUE_WITH_FORM = "{\"form\":{\"fields\":[{\"name\":\"a\"}]}}";
    private static final String ID_PARAM = "id";
    private static final String TAB_PARAM = "tab";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private OBDal mockOBDal;
    @Mock private OBContext mockOBContext;
    @Mock private Tab mockTab;
    @Mock private Client mockClient;
    @Mock private Organization mockOrg;
    @Mock private Role mockRole;
    @Mock private User mockUser;
    @Mock private PersonalizationHandler mockHandler;
    @Mock private UIPersonalization mockPersonalization;

    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<WeldUtils> weldMock;
    private MockedStatic<ApplicationUtils> appUtilsMock;

    private StringWriter responseWriter;
    private FormPersonalizationService service;

    /**
     * Initialises the static mocks and a plain (non-administrator) session on a tab without
     * personalization.
     *
     * @throws IOException if the response writer cannot be configured
     */
    @Before
    public void setUp() throws IOException {
        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenAnswer(inv -> new PrintWriter(responseWriter));

        obDalMock = mockStatic(OBDal.class);
        obContextMock = mockStatic(OBContext.class);
        weldMock = mockStatic(WeldUtils.class);
        appUtilsMock = mockStatic(ApplicationUtils.class);

        obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
        obContextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
        obContextMock.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(inv -> null);
        obContextMock.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);
        weldMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(PersonalizationHandler.class))
            .thenReturn(mockHandler);
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(false);
        appUtilsMock.when(ApplicationUtils::getAdminRoles).thenReturn(Collections.emptyList());
        appUtilsMock.when(ApplicationUtils::getAdminOrgs).thenReturn(Collections.emptyList());

        when(mockOBContext.getCurrentClient()).thenReturn(mockClient);
        when(mockOBContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockOBContext.getRole()).thenReturn(mockRole);
        when(mockOBContext.getUser()).thenReturn(mockUser);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockRole.getId()).thenReturn(ROLE_ID);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockTab.getId()).thenReturn(TAB_ID);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);

        when(mockPersonalization.getId()).thenReturn(PERSONALIZATION_ID);
        when(mockPersonalization.getType()).thenReturn("Form");
        when(mockPersonalization.getValue()).thenReturn(VALUE_WITH_FORM);
        when(mockHandler.getPersonalizationForTab(mockTab)).thenReturn(null);

        when(mockRequest.getPathInfo()).thenReturn(PATH);
        service = new FormPersonalizationService(mockRequest, mockResponse);
    }

    /** Closes the static mocks and clears thread-local state. */
    @After
    public void tearDown() {
        if (obDalMock != null) obDalMock.close();
        if (obContextMock != null) obContextMock.close();
        if (weldMock != null) weldMock.close();
        if (appUtilsMock != null) appUtilsMock.close();
        MetadataService.clear();
    }

    // --- helpers ---

    private void givenGet(String tabId) {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getParameter(TAB_PARAM)).thenReturn(tabId);
    }

    private void givenPost(String body) throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        when(mockHandler.storePersonalization(any(), any(), any(), any(), any(), any(), any(), any(), any(),
            anyBoolean())).thenReturn(mockPersonalization);
    }

    private void givenDelete(String id) {
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockRequest.getParameter(ID_PARAM)).thenReturn(id);
        when(mockOBDal.get(UIPersonalization.class, PERSONALIZATION_ID)).thenReturn(mockPersonalization);
    }

    private JSONObject response() throws JSONException {
        return new JSONObject(responseWriter.toString());
    }

    private UserRoles roleAdminOf(String roleId) {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getName()).thenReturn("Role " + roleId);
        UserRoles userRole = mock(UserRoles.class);
        when(userRole.getRole()).thenReturn(role);
        return userRole;
    }

    private RoleOrganization orgAdminOf(String orgId) {
        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(orgId);
        when(org.getName()).thenReturn("Org " + orgId);
        RoleOrganization roleOrg = mock(RoleOrganization.class);
        when(roleOrg.getOrganization()).thenReturn(org);
        return roleOrg;
    }

    private void givenAdminOfRole(String roleId) {
        // Built first: creating mocks inside thenReturn() would interrupt the stubbing in progress.
        List<UserRoles> adminRoles = Collections.singletonList(roleAdminOf(roleId));
        appUtilsMock.when(ApplicationUtils::getAdminRoles).thenReturn(adminRoles);
    }

    private void givenAdminOfOrganization(String orgId) {
        List<RoleOrganization> adminOrgs = Collections.singletonList(orgAdminOf(orgId));
        appUtilsMock.when(ApplicationUtils::getAdminOrgs).thenReturn(adminOrgs);
    }

    private void givenStoredAt(Client visibleClient, Organization visibleOrg, Role visibleRole, User visibleUser) {
        when(mockPersonalization.getVisibleAtClient()).thenReturn(visibleClient);
        when(mockPersonalization.getVisibleAtOrganization()).thenReturn(visibleOrg);
        when(mockPersonalization.getVisibleAtRole()).thenReturn(visibleRole);
        when(mockPersonalization.getUser()).thenReturn(visibleUser);
    }

    private Role roleWithId(String id) {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(id);
        return role;
    }

    private Organization orgWithId(String id) {
        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(id);
        return org;
    }

    private Client clientWithId(String id) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(id);
        return client;
    }

    private User userWithId(String id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    // --- GET ---

    /**
     * Without a personalization the form uses the layout defined in the dictionary.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetWithoutPersonalization() throws Exception {
        givenGet(TAB_ID);

        service.process();

        JSONObject body = response();
        assertEquals(TAB_ID, body.getString(TAB_PARAM));
        assertTrue(body.isNull(PERSONALIZATION_KEY));
        assertEquals("[\"USER\"]", body.getJSONObject(PERMISSIONS_KEY).getJSONArray("scopes").toString());
    }

    /**
     * The personalization that applies is returned with its scope, form and whether it can be edited.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetReturnsThePersonalizationThatApplies() throws Exception {
        givenGet(TAB_ID);
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);
        when(mockHandler.getPersonalizationForTab(mockTab)).thenReturn(mockPersonalization);

        service.process();

        JSONObject personalization = response().getJSONObject(PERSONALIZATION_KEY);
        assertEquals(PERSONALIZATION_ID, personalization.getString("id"));
        assertEquals("USER", personalization.getString(SCOPE_KEY));
        assertTrue(personalization.getBoolean("canEdit"));
        assertEquals("a", personalization.getJSONObject("form").getJSONArray("fields").getJSONObject(0).getString("name"));
    }

    /**
     * A record without form data (for example one that only holds other personalization) is ignored.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetIgnoresARecordWithoutForm() throws Exception {
        givenGet(TAB_ID);
        when(mockPersonalization.getValue()).thenReturn("{\"views\":{}}");
        when(mockHandler.getPersonalizationForTab(mockTab)).thenReturn(mockPersonalization);

        service.process();

        assertTrue(response().isNull(PERSONALIZATION_KEY));
    }

    /**
     * A shared personalization cannot be edited by a session that does not administer it.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetSharedPersonalizationIsReadOnlyForRegularSession() throws Exception {
        givenGet(TAB_ID);
        givenStoredAt(mockClient, null, mockRole, null);
        when(mockHandler.getPersonalizationForTab(mockTab)).thenReturn(mockPersonalization);

        service.process();

        JSONObject personalization = response().getJSONObject(PERSONALIZATION_KEY);
        assertEquals("ROLE", personalization.getString(SCOPE_KEY));
        assertFalse(personalization.getBoolean("canEdit"));
    }

    /**
     * Administrators are offered the levels they administer, with the roles and organizations.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetOffersTheLevelsTheSessionAdministers() throws Exception {
        givenGet(TAB_ID);
        givenAdminOfRole(ROLE_ID);
        givenAdminOfOrganization(ORG_ID);
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);
        when(mockRole.getId()).thenReturn(SYSTEM_ID);

        service.process();

        JSONObject permissions = response().getJSONObject(PERMISSIONS_KEY);
        assertEquals("[\"USER\",\"ROLE\",\"ORGANIZATION\",\"CLIENT\",\"SYSTEM\"]", permissions.getJSONArray("scopes").toString());
        assertEquals(ROLE_ID, permissions.getJSONArray("roles").getJSONObject(0).getString("id"));
        assertEquals(ORG_ID, permissions.getJSONArray("organizations").getJSONObject(0).getString("id"));
    }

    /**
     * The tab is required.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testGetWithoutTabRejected() throws IOException {
        givenGet(null);

        service.process();
    }

    /**
     * An unknown tab is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testGetWithUnknownTabRejected() throws IOException {
        givenGet(OTHER_ID);

        service.process();
    }

    // --- POST ---

    /**
     * Saving without a scope stores the personalization for the current user, bound to the current
     * client, organization and role like Classic's user level.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testPostStoresAtUserLevelByDefault() throws Exception {
        givenPost("{" + TAB_FIELD + "," + FORM_BODY + "}");
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), eq(ORG_ID), eq(ROLE_ID), eq(USER_ID),
            eq(TAB_ID), isNull(), eq("form"), any(), eq(false));
        verify(mockOBDal).flush();
        assertEquals("USER", response().getJSONObject(PERSONALIZATION_KEY).getString(SCOPE_KEY));
    }

    /**
     * The tab can also come in the query string.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostTakesTheTabFromTheQueryString() throws IOException {
        givenPost("{" + FORM_BODY + "}");
        when(mockRequest.getParameter(TAB_PARAM)).thenReturn(TAB_ID);

        service.process();

        verify(mockHandler).storePersonalization(any(), any(), any(), any(), any(), eq(TAB_ID), any(), any(), any(),
            anyBoolean());
    }

    /**
     * A role administrator can personalize for a role they administer.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostRoleScopeAllowedForRoleAdministrator() throws IOException {
        givenAdminOfRole(OTHER_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ROLE\",\"role\":\"" + OTHER_ID + "\"," + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), isNull(), eq(OTHER_ID), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * Without a role to administer, the role level is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostRoleScopeRejectedForRegularSession() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ROLE\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * A role the session does not administer is rejected even if it administers another one.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostRoleScopeRejectedForAnotherRole() throws IOException {
        givenAdminOfRole(OTHER_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ROLE\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * An organization administrator can personalize for their organization, which is the default target.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostOrganizationScopeAllowedForOrganizationAdministrator() throws IOException {
        givenAdminOfOrganization(ORG_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ORGANIZATION\"," + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), eq(ORG_ID), isNull(), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * An organization the session does not administer is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostOrganizationScopeRejectedForAnotherOrganization() throws IOException {
        givenAdminOfOrganization(ORG_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ORGANIZATION\",\"organization\":\"" + OTHER_ID + "\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * A client administrator can personalize for the whole client.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostClientScopeAllowedForClientAdministrator() throws IOException {
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"CLIENT\"," + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), isNull(), isNull(), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * Anyone else is rejected at the client level.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostClientScopeRejectedForRegularSession() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"scope\":\"CLIENT\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * Only the system role can personalize for the whole system, with no level set.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostSystemScopeAllowedForSystemRole() throws IOException {
        when(mockRole.getId()).thenReturn(SYSTEM_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"system\"," + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), isNull(), isNull(), isNull(), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * Any other role is rejected at the system level.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostSystemScopeRejectedForOtherRoles() throws IOException {
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"SYSTEM\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * An unknown scope is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostInvalidScopeRejected() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"scope\":\"EVERYONE\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * The form must be sent.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostWithoutFormRejected() throws IOException {
        givenPost("{" + TAB_FIELD + "}");

        service.process();
    }

    /**
     * The form must have a list of fields.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostWithoutFieldsRejected() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"form\":{\"other\":1}}");

        service.process();
    }

    /**
     * A body that is not JSON is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostInvalidJsonRejected() throws IOException {
        givenPost("not json");

        service.process();
    }

    /**
     * Nothing is stored when the request is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testRejectedPostStoresNothing() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"scope\":\"CLIENT\"," + FORM_BODY + "}");

        try {
            service.process();
        } catch (ForbiddenException expected) {
            verify(mockHandler, never()).storePersonalization(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyBoolean());
            return;
        }
        throw new AssertionError("The request should have been rejected");
    }

    // --- DELETE ---

    /**
     * The id of the personalization is required.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testDeleteWithoutIdRejected() throws IOException {
        givenDelete(null);

        service.process();
    }

    /**
     * Discarding something already gone is a success.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testDeleteUnknownIdIsASuccess() throws Exception {
        givenDelete(OTHER_ID);

        service.process();

        assertEquals("success", response().getString("result"));
        verify(mockOBDal, never()).remove(any());
    }

    /**
     * Only form personalizations can be discarded through this service.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testDeleteRejectsOtherPersonalizationTypes() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        when(mockPersonalization.getType()).thenReturn("Window");

        service.process();
    }

    /**
     * A user can discard their own personalization.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testDeleteOwnPersonalization() throws Exception {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
        verify(mockOBDal).flush();
        assertEquals("success", response().getString("result"));
    }

    /**
     * Nobody can discard another user's personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteAnotherUsersPersonalizationRejected() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, mockOrg, mockRole, userWithId(OTHER_ID));

        service.process();
    }

    /**
     * A shared role personalization needs the administrator of that role.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteSharedRolePersonalizationRejectedForRegularSession() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, null, mockRole, null);

        service.process();
    }

    /**
     * The administrator of a role can discard its shared personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteSharedRolePersonalizationAllowedForRoleAdministrator() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, null, roleWithId(OTHER_ID), null);
        givenAdminOfRole(OTHER_ID);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }

    /**
     * The administrator of an organization can discard its shared personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteSharedOrganizationPersonalizationAllowedForOrganizationAdministrator() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, orgWithId(ORG_ID), null, null);
        givenAdminOfOrganization(ORG_ID);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }

    /**
     * An organization personalization of an organization the session does not administer is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteSharedOrganizationPersonalizationRejectedForAnotherOrganization() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, orgWithId(OTHER_ID), null, null);
        givenAdminOfOrganization(ORG_ID);

        service.process();
    }

    /**
     * A client administrator can discard the personalization of the whole client.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteClientPersonalizationAllowedForClientAdministrator() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, null, null, null);
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }

    /**
     * A system-wide personalization is only editable by the system role.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteSystemPersonalizationRejectedForOtherRoles() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(null, null, null, null);
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);

        service.process();
    }

    /**
     * The system role can discard a system-wide personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteSystemPersonalizationAllowedForSystemRole() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(clientWithId(SYSTEM_ID), null, null, null);
        when(mockRole.getId()).thenReturn(SYSTEM_ID);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }

    // --- Other ---

    /**
     * Methods other than GET, POST and DELETE are not allowed.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = MethodNotAllowedException.class)
    public void testUnsupportedMethodThrowsMethodNotAllowed() throws IOException {
        when(mockRequest.getMethod()).thenReturn("PUT");

        service.process();
    }

    /** The scope of a stored personalization is the most specific level it is bound to. */
    @Test
    public void testDeriveScope() {
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);
        assertEquals("USER", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(mockClient, mockOrg, mockRole, null);
        assertEquals("ROLE", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(mockClient, mockOrg, null, null);
        assertEquals("ORGANIZATION", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(mockClient, null, null, null);
        assertEquals("CLIENT", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(clientWithId(SYSTEM_ID), null, null, null);
        assertEquals("SYSTEM", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(null, null, null, null);
        assertEquals("SYSTEM", FormPersonalizationService.deriveScope(mockPersonalization));
    }
}

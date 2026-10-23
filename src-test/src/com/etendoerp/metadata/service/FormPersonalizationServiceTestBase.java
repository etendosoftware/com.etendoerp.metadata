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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
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

/**
 * Shared setup and helpers of the {@link FormPersonalizationService} unit tests: the static mocks and a plain
 * session on a tab without personalization.
 */
public abstract class FormPersonalizationServiceTestBase {

    protected static final String PATH = "/form-personalization";
    protected static final String TAB_ID = "tab-1";
    protected static final String CLIENT_ID = "client-1";
    protected static final String ORG_ID = "org-1";
    protected static final String ROLE_ID = "role-1";
    protected static final String USER_ID = "user-1";
    protected static final String OTHER_ID = "other-9";
    protected static final String SYSTEM_ID = "0";
    protected static final String PERSONALIZATION_ID = "pers-1";
    protected static final String FORM_BODY = "\"form\":{\"fields\":[{\"name\":\"a\",\"colSpan\":2}]}";
    protected static final String TAB_FIELD = "\"tab\":\"" + TAB_ID + "\"";
    protected static final String CLIENT_SCOPE_FIELD = ",\"scope\":\"CLIENT\",";
    protected static final String SCOPE_KEY = "scope";
    protected static final String PERSONALIZATION_KEY = "personalization";
    protected static final String PERMISSIONS_KEY = "permissions";
    protected static final String VALUE_WITH_FORM = "{\"form\":{\"fields\":[{\"name\":\"a\"}]}}";
    protected static final String ID_PARAM = "id";
    protected static final String TAB_PARAM = "tab";

    @Mock protected HttpServletRequest mockRequest;
    @Mock protected HttpServletResponse mockResponse;
    @Mock protected OBDal mockOBDal;
    @Mock protected OBContext mockOBContext;
    @Mock protected Tab mockTab;
    @Mock protected Client mockClient;
    @Mock protected Organization mockOrg;
    @Mock protected Role mockRole;
    @Mock protected User mockUser;
    @Mock protected PersonalizationHandler mockHandler;
    @Mock protected UIPersonalization mockPersonalization;

    protected MockedStatic<OBDal> obDalMock;
    protected MockedStatic<OBContext> obContextMock;
    protected MockedStatic<WeldUtils> weldMock;
    protected MockedStatic<ApplicationUtils> appUtilsMock;

    protected StringWriter responseWriter;
    protected FormPersonalizationService service;

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


    protected void givenGet(String tabId) {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getParameter(TAB_PARAM)).thenReturn(tabId);
    }

    protected void givenPost(String body) throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        when(mockHandler.storePersonalization(any(), any(), any(), any(), any(), any(), any(), any(), any(),
            anyBoolean())).thenReturn(mockPersonalization);
    }

    protected void givenDelete(String id) {
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockRequest.getParameter(ID_PARAM)).thenReturn(id);
        when(mockOBDal.get(UIPersonalization.class, PERSONALIZATION_ID)).thenReturn(mockPersonalization);
    }

    protected JSONObject response() throws JSONException {
        return new JSONObject(responseWriter.toString());
    }

    protected UserRoles roleAdminOf(String roleId) {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getName()).thenReturn("Role " + roleId);
        UserRoles userRole = mock(UserRoles.class);
        when(userRole.getRole()).thenReturn(role);
        return userRole;
    }

    protected RoleOrganization orgAdminOf(String orgId) {
        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(orgId);
        when(org.getName()).thenReturn("Org " + orgId);
        RoleOrganization roleOrg = mock(RoleOrganization.class);
        when(roleOrg.getOrganization()).thenReturn(org);
        return roleOrg;
    }

    protected void givenAdminOfRole(String roleId) {
        // Built first: creating mocks inside thenReturn() would interrupt the stubbing in progress.
        List<UserRoles> adminRoles = Collections.singletonList(roleAdminOf(roleId));
        appUtilsMock.when(ApplicationUtils::getAdminRoles).thenReturn(adminRoles);
    }

    protected void givenAdminOfOrganization(String orgId) {
        List<RoleOrganization> adminOrgs = Collections.singletonList(orgAdminOf(orgId));
        appUtilsMock.when(ApplicationUtils::getAdminOrgs).thenReturn(adminOrgs);
    }

    protected void givenStoredAt(Client visibleClient, Organization visibleOrg, Role visibleRole, User visibleUser) {
        when(mockPersonalization.getVisibleAtClient()).thenReturn(visibleClient);
        when(mockPersonalization.getVisibleAtOrganization()).thenReturn(visibleOrg);
        when(mockPersonalization.getVisibleAtRole()).thenReturn(visibleRole);
        when(mockPersonalization.getUser()).thenReturn(visibleUser);
    }

    protected Role roleWithId(String id) {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(id);
        return role;
    }

    protected Organization orgWithId(String id) {
        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(id);
        return org;
    }

    protected Client clientWithId(String id) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(id);
        return client;
    }

    protected User userWithId(String id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }
}

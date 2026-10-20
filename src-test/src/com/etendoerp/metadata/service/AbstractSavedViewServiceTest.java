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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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

/**
 * Shared Mockito fixture for {@link SavedViewService} unit tests: mocks a "regular,
 * non-administrator" user/role session (userLevel "O") for tab {@link #TAB_ID}, owning
 * {@link #mockView} (id {@link #VIEW_ID}). Concrete subclasses supply the {@code @RunWith}
 * runner (so Mockito initializes the {@code @Mock} fields declared here) and their own tests.
 */
@SuppressWarnings("unchecked")
public abstract class AbstractSavedViewServiceTest {

    protected static final String VIEW_ID = "view-abc-123";
    protected static final String VIEW_NAME = "My Saved View";
    protected static final String TAB_ID = "tab-xyz-456";
    protected static final String USER_ID = "user-001";
    protected static final String CLIENT_ID = "client-001";
    protected static final String ORG_ID = "org-001";
    protected static final String ROLE_ID = "role-001";
    protected static final String SAVED_VIEW_BASE_PATH = "/saved-views";
    protected static final String PATH_WITH_ID = "/saved-views/" + VIEW_ID;
    protected static final String RESPONSE_CONTAINS_VIEW_ID = "Response should contain view ID";
    protected static final String ISDEFAULT_PARAM = "isdefault";

    @Mock protected HttpServletRequest mockRequest;
    @Mock protected HttpServletResponse mockResponse;
    @Mock protected OBDal mockOBDal;
    @Mock protected OBContext mockOBContext;
    @Mock protected OBProvider mockOBProvider;
    @Mock protected SavedView mockView;
    @Mock protected Tab mockTab;
    @Mock protected User mockUser;
    @Mock protected Client mockClient;
    @Mock protected Organization mockOrg;
    @Mock protected Role mockRole;
    @Mock protected OBCriteria<SavedView> mockCriteria;

    protected MockedStatic<OBDal> obDalMock;
    protected MockedStatic<OBContext> obContextMock;
    protected MockedStatic<OBProvider> obProviderMock;

    protected StringWriter responseWriter;
    protected SavedViewService service;

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
}

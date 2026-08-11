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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Test class for ChangeProfileService.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ChangeProfileServiceTest {
    private static final String USER_ID = "user-1";
    private static final String ROLE_ID = "role-1";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @Mock
    private OBContext obContext;

    @Mock
    private OBDal obDal;

    @Mock
    private User contextUser;

    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock;

    /**
     * Initializes the static mocks shared by every test.
     */
    @Before
    public void setUp() {
        obContextMock = mockStatic(OBContext.class);
        obDalMock = mockStatic(OBDal.class);
        authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);

        obContextMock.when(OBContext::getOBContext).thenReturn(obContext);
        when(contextUser.getId()).thenReturn(USER_ID);
        when(obContext.getUser()).thenReturn(contextUser);

        obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    }

    /**
     * Releases the static mocks opened in {@link #setUp()}.
     */
    @After
    public void tearDown() {
        authUtilsMock.close();
        obDalMock.close();
        obContextMock.close();
    }

    private void setRequestBody(String body) throws Exception {
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    /**
     * A GET request must be rejected with {@link MethodNotAllowedException}.
     */
    @Test
    public void testProcessThrowsMethodNotAllowedForGet() {
        when(mockRequest.getMethod()).thenReturn("GET");

        ChangeProfileService service = new ChangeProfileService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected MethodNotAllowedException");
        } catch (MethodNotAllowedException expected) {
            // expected
        } catch (Exception e) {
            fail("Expected MethodNotAllowedException, got " + e);
        }
    }

    /**
     * A valid role/organization/warehouse combination should produce a JWT.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessHappyPath() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"role\":\"role-1\",\"organization\":\"org-1\",\"warehouse\":\"wh-1\"}");

        StringWriter stringWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(stringWriter));

        User mockUser = mock(User.class);
        Role mockRole = mock(Role.class);
        when(mockRole.getClient()).thenReturn(mock(Client.class));
        Organization mockOrg = mock(Organization.class);
        Warehouse mockWarehouse = mock(Warehouse.class);

        when(obDal.get(User.class, USER_ID)).thenReturn(mockUser);
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(mockRole);
        when(obDal.get(Organization.class, "org-1")).thenReturn(mockOrg);
        when(obDal.get(Warehouse.class, "wh-1")).thenReturn(mockWarehouse);

        authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
            .thenReturn("fake-jwt-token");

        ChangeProfileService service = new ChangeProfileService(mockRequest, mockResponse);
        service.process();

        String output = stringWriter.toString();
        assertTrue("Output should contain the generated token", output.contains("fake-jwt-token"));
    }

    /**
     * An unresolvable role id should be rejected with {@link UnprocessableContentException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessInvalidRoleThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"role\":\"missing-role\"}");

        User mockUser = mock(User.class);
        when(obDal.get(User.class, USER_ID)).thenReturn(mockUser);
        when(obDal.get(Role.class, "missing-role")).thenReturn(null);

        ChangeProfileService service = new ChangeProfileService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnprocessableContentException for an unresolvable role id");
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }

    /**
     * An unresolvable organization id should be rejected with {@link UnprocessableContentException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessInvalidOrganizationThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"role\":\"role-1\",\"organization\":\"missing-org\"}");

        User mockUser = mock(User.class);
        Role mockRole = mock(Role.class);
        when(mockRole.getClient()).thenReturn(mock(Client.class));
        when(obDal.get(User.class, USER_ID)).thenReturn(mockUser);
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(mockRole);
        when(obDal.get(Organization.class, "missing-org")).thenReturn(null);

        ChangeProfileService service = new ChangeProfileService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnprocessableContentException for an unresolvable organization id");
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }

    /**
     * An unresolvable warehouse id should be rejected with {@link UnprocessableContentException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessInvalidWarehouseThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"role\":\"role-1\",\"warehouse\":\"missing-wh\"}");

        User mockUser = mock(User.class);
        Role mockRole = mock(Role.class);
        when(mockRole.getClient()).thenReturn(mock(Client.class));
        when(obDal.get(User.class, USER_ID)).thenReturn(mockUser);
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(mockRole);
        when(obDal.get(Warehouse.class, "missing-wh")).thenReturn(null);

        ChangeProfileService service = new ChangeProfileService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnprocessableContentException for an unresolvable warehouse id");
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }
}

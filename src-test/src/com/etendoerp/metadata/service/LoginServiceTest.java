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
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Test class for LoginService.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginServiceTest {
    private static final String USERNAME = "lorena";
    private static final String PASSWORD = "tecnicia";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @Mock
    private OBContext obContext;

    @Mock
    private OBDal obDal;

    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<PasswordHash> passwordHashMock;
    private MockedStatic<Utils> authUtilsMock;

    /**
     * Initializes the static mocks shared by every test.
     */
    @Before
    public void setUp() {
        obContextMock = mockStatic(OBContext.class);
        obDalMock = mockStatic(OBDal.class);
        passwordHashMock = mockStatic(PasswordHash.class);
        authUtilsMock = mockStatic(Utils.class);

        obContextMock.when(OBContext::getOBContext).thenReturn(obContext);
        obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    }

    /**
     * Releases the static mocks opened in {@link #setUp()}.
     */
    @After
    public void tearDown() {
        authUtilsMock.close();
        passwordHashMock.close();
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

        LoginService service = new LoginService(mockRequest, mockResponse);
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
     * A request without username/password must be rejected with {@link UnprocessableContentException}.
     */
    @Test
    public void testProcessMissingCredentialsThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{}");

        LoginService service = new LoginService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnprocessableContentException");
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }

    /**
     * Wrong credentials must be rejected with {@link UnauthorizedException}.
     */
    @Test
    public void testProcessInvalidCredentialsThrowsUnauthorized() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"lorena\",\"password\":\"wrong\"}");

        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, "wrong"))
                .thenReturn(Optional.empty());

        LoginService service = new LoginService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnauthorizedException");
        } catch (UnauthorizedException expected) {
            // expected
        }
    }

    /**
     * When no role is specified in the request, the user's default role should be used.
     */
    @Test
    public void testProcessHappyPathUsesDefaultRoleWhenNotSpecified() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"lorena\",\"password\":\"tecnicia\"}");

        StringWriter stringWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(stringWriter));

        User mockUser = mock(User.class);
        Role defaultRole = mock(Role.class);
        when(defaultRole.getId()).thenReturn("default-role-id");
        when(defaultRole.getClient()).thenReturn(mock(Client.class));
        when(mockUser.getDefaultRole()).thenReturn(defaultRole);

        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, PASSWORD))
                .thenReturn(Optional.of(mockUser));
        when(obDal.get(Role.class, "default-role-id")).thenReturn(defaultRole);

        authUtilsMock.when(() -> Utils.generateToken(org.mockito.ArgumentMatchers.any(AuthData.class),
                org.mockito.ArgumentMatchers.isNull())).thenReturn("fake-jwt-token");

        LoginService service = new LoginService(mockRequest, mockResponse);
        service.process();

        String output = stringWriter.toString();
        assertTrue("Output should contain the generated token", output.contains("fake-jwt-token"));
    }

    /**
     * When neither the request nor the user provides a role, resolution must fail
     * with {@link UnprocessableContentException}.
     */
    @Test
    public void testProcessNoRoleAvailableThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"lorena\",\"password\":\"tecnicia\"}");

        User mockUser = mock(User.class);
        when(mockUser.getDefaultRole()).thenReturn(null);

        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, PASSWORD))
                .thenReturn(Optional.of(mockUser));

        LoginService service = new LoginService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnprocessableContentException when no role can be resolved");
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }

    /**
     * An unresolvable organization id should be rejected with {@link UnprocessableContentException}.
     */
    @Test
    public void testProcessInvalidOrganizationThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"lorena\",\"password\":\"tecnicia\",\"role\":\"role-1\",\"organization\":\"missing-org\"}");

        User mockUser = mock(User.class);
        Role role = mock(Role.class);
        when(role.getClient()).thenReturn(mock(Client.class));

        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, PASSWORD))
                .thenReturn(Optional.of(mockUser));
        when(obDal.get(Role.class, "role-1")).thenReturn(role);
        when(obDal.get(Organization.class, "missing-org")).thenReturn(null);

        LoginService service = new LoginService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnprocessableContentException for an unresolvable organization id");
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }

    /**
     * An unresolvable warehouse id should be rejected with {@link UnprocessableContentException}.
     */
    @Test
    public void testProcessInvalidWarehouseThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"lorena\",\"password\":\"tecnicia\",\"role\":\"role-1\",\"warehouse\":\"missing-wh\"}");

        User mockUser = mock(User.class);
        Role role = mock(Role.class);
        when(role.getClient()).thenReturn(mock(Client.class));

        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, PASSWORD))
                .thenReturn(Optional.of(mockUser));
        when(obDal.get(Role.class, "role-1")).thenReturn(role);
        when(obDal.get(Warehouse.class, "missing-wh")).thenReturn(null);

        LoginService service = new LoginService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnprocessableContentException for an unresolvable warehouse id");
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }
}

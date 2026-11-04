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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.metadata.auth.LoginAttemptGuard;
import com.etendoerp.metadata.auth.LoginDefaultsResolver;
import com.etendoerp.metadata.auth.LoginSessionRegistry;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Test class for LoginService.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginServiceTest extends AbstractProfileServiceTest {
    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password"; // NOSONAR - fake value, only used to stub a mocked LoginAttemptGuard call
    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "session-1";

    private static final String CREDENTIALS_BODY = "{\"username\":\"test-user\",\"password\":\"test-password\"}";
    private static final String FAKE_TOKEN = "fake-jwt-token";
    private static final String POST_METHOD = "POST";
    private static final String ROLE_ID = "role-1";

    private MockedStatic<LoginAttemptGuard> guardMock;
    private MockedStatic<LoginSessionRegistry> registryMock;
    private MockedStatic<LoginDefaultsResolver> resolverMock;

    /**
     * Initializes the {@link LoginAttemptGuard}, {@link LoginSessionRegistry} and
     * {@link LoginDefaultsResolver} static mocks, on top of the shared mocks from
     * {@link AbstractProfileServiceTest}.
     */
    @Before
    public void setUpLoginMocks() {
        guardMock = mockStatic(LoginAttemptGuard.class);
        registryMock = mockStatic(LoginSessionRegistry.class);
        resolverMock = mockStatic(LoginDefaultsResolver.class);
    }

    /**
     * Releases the static mocks opened in {@link #setUpLoginMocks()}.
     */
    @After
    public void tearDownLoginMocks() {
        resolverMock.close();
        registryMock.close();
        guardMock.close();
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
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessMissingCredentialsThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody("{}");

        LoginService service = new LoginService(mockRequest, mockResponse);
        assertProcessThrowsUnprocessable(service, "Expected UnprocessableContentException");
    }

    /**
     * Credentials refused by {@link LoginAttemptGuard} (wrong password or locked user) must be
     * rejected with its {@link UnauthorizedException}, and no token or session must be issued.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessInvalidCredentialsThrowsUnauthorized() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody(CREDENTIALS_BODY);

        guardMock.when(() -> LoginAttemptGuard.authenticate(mockRequest, USERNAME, PASSWORD))
                .thenThrow(new UnauthorizedException("User is locked"));

        LoginService service = new LoginService(mockRequest, mockResponse);
        try {
            service.process();
            fail("Expected UnauthorizedException");
        } catch (UnauthorizedException expected) {
            assertEquals("User is locked", expected.getMessage());
        }
        authUtilsMock.verify(() -> Utils.generateToken(any(AuthData.class), any()), never());
        registryMock.verify(() -> LoginSessionRegistry.register(any(), any(), any(), any()), never());
    }

    /**
     * A successful login is recorded in the Classic session registry with the typed username, and
     * its id is returned next to the token.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    public void testProcessRegistersSuccessfulSession() throws Exception {
        StringWriter stringWriter = stubTokenResponse();
        User mockUser = stubLoginRequest(CREDENTIALS_BODY);
        stubClassicDefaults(mockUser);
        registryMock.when(() -> LoginSessionRegistry.register(mockRequest, USERNAME, USER_ID,
                LoginSessionRegistry.SUCCESS_STATUS)).thenReturn(SESSION_ID);

        new LoginService(mockRequest, mockResponse).process();

        JSONObject response = new JSONObject(stringWriter.toString());
        assertEquals(FAKE_TOKEN, response.getString("token"));
        assertEquals(SESSION_ID, response.getString(LoginSessionRegistry.SESSION_ID));
        registryMock.verify(() -> LoginSessionRegistry.register(eq(mockRequest), eq(USERNAME), eq(USER_ID),
                eq(LoginSessionRegistry.SUCCESS_STATUS)));
    }

    /**
     * When no role is specified in the request, the Classic login defaults resolved by
     * {@link LoginDefaultsResolver} are used to issue the token.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    public void testProcessUsesClassicDefaultsWhenRoleNotSpecified() throws Exception {
        StringWriter stringWriter = stubTokenResponse();
        User mockUser = stubLoginRequest(CREDENTIALS_BODY);
        AuthData defaults = stubClassicDefaults(mockUser);

        new LoginService(mockRequest, mockResponse).process();

        assertSame(defaults, captureTokenAuthData());
        assertTrue("Output should contain the generated token", stringWriter.toString().contains(FAKE_TOKEN));
    }

    /**
     * When the user has no valid Classic login defaults, the login must fail with
     * {@link UnprocessableContentException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessNoValidDefaultsThrowsUnprocessable() throws Exception {
        User mockUser = stubLoginRequest(CREDENTIALS_BODY);
        resolverMock.when(() -> LoginDefaultsResolver.resolve(mockUser))
                .thenThrow(new UnprocessableContentException("No valid login defaults"));

        LoginService service = new LoginService(mockRequest, mockResponse);
        assertProcessThrowsUnprocessable(service, "Expected UnprocessableContentException when no role can be resolved");
    }

    /**
     * An organization in a role-less request replaces the default organization and drops the default
     * warehouse, which belongs to the default organization.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    public void testProcessOrganizationInBodyReplacesDefaultOrganization() throws Exception {
        stubTokenResponse();
        User mockUser = stubLoginRequest(
                "{\"username\":\"test-user\",\"password\":\"test-password\",\"organization\":\"org-1\"}");
        stubClassicDefaults(mockUser);
        Organization organization = mock(Organization.class);
        when(obDal.get(Organization.class, "org-1")).thenReturn(organization);

        new LoginService(mockRequest, mockResponse).process();

        AuthData authData = captureTokenAuthData();
        assertSame(organization, authData.getOrg());
        assertNull(authData.getWarehouse());
    }

    /**
     * A warehouse in a role-less request replaces the default warehouse and keeps the default
     * organization.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    public void testProcessWarehouseInBodyReplacesDefaultWarehouse() throws Exception {
        stubTokenResponse();
        User mockUser = stubLoginRequest(
                "{\"username\":\"test-user\",\"password\":\"test-password\",\"warehouse\":\"wh-1\"}");
        AuthData defaults = stubClassicDefaults(mockUser);
        Organization defaultOrganization = defaults.getOrg();
        Warehouse warehouse = mock(Warehouse.class);
        when(obDal.get(Warehouse.class, "wh-1")).thenReturn(warehouse);

        new LoginService(mockRequest, mockResponse).process();

        AuthData authData = captureTokenAuthData();
        assertSame(defaultOrganization, authData.getOrg());
        assertSame(warehouse, authData.getWarehouse());
    }

    /**
     * A role in the request is used as is, with its own client, and the Classic defaults are not
     * resolved.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    public void testProcessUsesRequestedRole() throws Exception {
        stubTokenResponse();
        stubLoginRequest("{\"username\":\"test-user\",\"password\":\"test-password\",\"role\":\"role-1\"}");
        Role role = stubRequestedRole();
        Client client = role.getClient();

        new LoginService(mockRequest, mockResponse).process();

        AuthData authData = captureTokenAuthData();
        assertSame(role, authData.getRole());
        assertSame(client, authData.getClient());
        resolverMock.verify(() -> LoginDefaultsResolver.resolve(any(User.class)), never());
    }

    /**
     * An unresolvable organization id should be rejected with {@link UnprocessableContentException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessInvalidOrganizationThrowsUnprocessable() throws Exception {
        stubLoginRequest(
                "{\"username\":\"test-user\",\"password\":\"test-password\",\"role\":\"role-1\",\"organization\":\"missing-org\"}");
        stubRequestedRole();
        when(obDal.get(Organization.class, "missing-org")).thenReturn(null);

        LoginService service = new LoginService(mockRequest, mockResponse);
        assertProcessThrowsUnprocessable(service,
                "Expected UnprocessableContentException for an unresolvable organization id");
    }

    /**
     * An unresolvable warehouse id should be rejected with {@link UnprocessableContentException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessInvalidWarehouseThrowsUnprocessable() throws Exception {
        stubLoginRequest(
                "{\"username\":\"test-user\",\"password\":\"test-password\",\"role\":\"role-1\",\"warehouse\":\"missing-wh\"}");
        stubRequestedRole();
        when(obDal.get(Warehouse.class, "missing-wh")).thenReturn(null);

        LoginService service = new LoginService(mockRequest, mockResponse);
        assertProcessThrowsUnprocessable(service,
                "Expected UnprocessableContentException for an unresolvable warehouse id");
    }

    /**
     * Stubs a POST login request with the given body whose credentials authenticate a mocked user.
     *
     * @param body the raw JSON body
     * @return the authenticated user
     * @throws Exception if stubbing the mocked reader fails
     */
    private User stubLoginRequest(String body) throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody(body);
        User mockUser = mock(User.class);
        guardMock.when(() -> LoginAttemptGuard.authenticate(mockRequest, USERNAME, PASSWORD)).thenReturn(USER_ID);
        when(obDal.get(User.class, USER_ID)).thenReturn(mockUser);
        return mockUser;
    }

    /**
     * Stubs the role named in the request body ({@link #ROLE_ID}) with its own client.
     *
     * @return the stubbed role
     */
    private Role stubRequestedRole() {
        Role role = mock(Role.class);
        when(role.getClient()).thenReturn(mock(Client.class));
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(role);
        return role;
    }

    /**
     * Stubs the Classic login defaults of the given user with a role, organization, warehouse and
     * client.
     *
     * @param user the authenticated user
     * @return the stubbed defaults
     */
    private AuthData stubClassicDefaults(User user) {
        AuthData defaults = new AuthData(user, mock(Role.class), mock(Organization.class), mock(Warehouse.class),
                mock(Client.class));
        resolverMock.when(() -> LoginDefaultsResolver.resolve(user)).thenReturn(defaults);
        return defaults;
    }

    /**
     * Stubs token generation and the response writer.
     *
     * @return the writer receiving the response body
     * @throws Exception if stubbing the mocked writer fails
     */
    private StringWriter stubTokenResponse() throws Exception {
        StringWriter stringWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(stringWriter));
        authUtilsMock.when(() -> Utils.generateToken(any(AuthData.class), isNull())).thenReturn(FAKE_TOKEN);
        return stringWriter;
    }

    /**
     * Captures the login context the token was generated with.
     *
     * @return the captured login context
     */
    private AuthData captureTokenAuthData() {
        ArgumentCaptor<AuthData> captor = ArgumentCaptor.forClass(AuthData.class);
        authUtilsMock.verify(() -> Utils.generateToken(captor.capture(), isNull()));
        return captor.getValue();
    }
}

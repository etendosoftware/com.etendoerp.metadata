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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.auth.LoginDefaultsResolver;
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
    private static final String PASSWORD = "test-password"; // NOSONAR - fake value, only used to stub a mocked PasswordHash call

    private static final String CREDENTIALS_BODY = "{\"username\":\"test-user\",\"password\":\"test-password\"}";
    private static final String FAKE_TOKEN = "fake-jwt-token";
    private static final String POST_METHOD = "POST";
    private static final String ROLE_ID = "role-1";

    private MockedStatic<PasswordHash> passwordHashMock;
    private MockedStatic<LoginDefaultsResolver> resolverMock;

    /**
     * Initializes the {@code PasswordHash} and {@link LoginDefaultsResolver} static mocks, on top of the shared mocks from
     * {@link AbstractProfileServiceTest}.
     */
    @Before
    public void setUpPasswordHashMock() {
        passwordHashMock = mockStatic(PasswordHash.class);
        resolverMock = mockStatic(LoginDefaultsResolver.class);
    }

    /**
     * Releases the static mocks opened in {@link #setUpPasswordHashMock()}.
     */
    @After
    public void tearDownPasswordHashMock() {
        resolverMock.close();
        passwordHashMock.close();
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
     * Wrong credentials must be rejected with {@link UnauthorizedException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessInvalidCredentialsThrowsUnauthorized() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody("{\"username\":\"test-user\",\"password\":\"wrong\"}");

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
        Role role = mock(Role.class);
        Client client = mock(Client.class);
        when(role.getClient()).thenReturn(client);
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(role);

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
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody("{\"username\":\"test-user\",\"password\":\"test-password\",\"role\":\"role-1\",\"organization\":\"missing-org\"}");

        User mockUser = mock(User.class);
        Role role = mock(Role.class);
        when(role.getClient()).thenReturn(mock(Client.class));

        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, PASSWORD))
                .thenReturn(Optional.of(mockUser));
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(role);
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
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody("{\"username\":\"test-user\",\"password\":\"test-password\",\"role\":\"role-1\",\"warehouse\":\"missing-wh\"}");

        User mockUser = mock(User.class);
        Role role = mock(Role.class);
        when(role.getClient()).thenReturn(mock(Client.class));

        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, PASSWORD))
                .thenReturn(Optional.of(mockUser));
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(role);
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
        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(USERNAME, PASSWORD))
                .thenReturn(Optional.of(mockUser));
        return mockUser;
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

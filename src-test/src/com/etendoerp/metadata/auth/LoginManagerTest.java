package com.etendoerp.metadata.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.test.base.OBBaseTest;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.http.BaseServlet;
import com.etendoerp.metadata.utils.Utils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * Unit tests for the LoginManager class, which handles user login and token generation.
 * This class tests various scenarios including successful login, invalid credentials,
 * and token extraction from the Authorization header.
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginManagerTest extends OBBaseTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @Mock
    private User user;

    @Mock
    private Role role;

    @Mock
    private Role defaultRole;

    @Mock
    private Organization organization;

    @Mock
    private Warehouse warehouse;

    @Mock
    private Client client;

    @Mock
    private UserRoles userRoles;

    @Mock
    private OBDal obDal;

    @Mock
    private DalConnectionProvider connectionProvider;

    @Mock
    private DecodedJWT decodedJWT;

    @Mock
    private Claim claim;

    @Mock
    private LoginUtils.RoleDefaults roleDefaults;

    private LoginManager loginManager;
    private JSONObject requestData;

    /**
     * Sets up the test environment before each test execution.
     * Initializes the LoginManager instance and prepares mock objects and test data.
     *
     * @throws Exception if any error occurs during setup
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        loginManager = new LoginManager();

        requestData = new JSONObject();
        requestData.put("username", "testuser");
        requestData.put("password", "testpass");
        requestData.put("role", "role-id");
        requestData.put("organization", "org-id");
        requestData.put("warehouse", "warehouse-id");
        requestData.put("client", "client-id");

        when(request.getSession(true)).thenReturn(session);
        when(user.getId()).thenReturn("user-id");
        when(role.getId()).thenReturn("role-id");
        when(organization.getId()).thenReturn("org-id");
        when(warehouse.getId()).thenReturn("warehouse-id");
        when(client.getId()).thenReturn("client-id");
        when(session.getId()).thenReturn("session-id");

        when(user.getDefaultRole()).thenReturn(defaultRole);
        List<UserRoles> userRolesList = new ArrayList<>();
        userRolesList.add(userRoles);
        when(user.getADUserRolesList()).thenReturn(userRolesList);
        when(userRoles.getRole()).thenReturn(role);
    }

    /**
     * Test for the extractToken method to ensure it correctly extracts the token from the Authorization header.
     */
    @Test
    public void extractTokenShouldReturnTokenWithoutBearerPrefix() {
        String authorization = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

        String result = LoginManager.extractToken(authorization);

        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", result);
    }

    /**
     * Test for the extractToken method to ensure it returns null when the Authorization header does not start with "Bearer ".
     */
    @Test
    public void extractTokenShouldReturnNullWhenAuthorizationIsNull() {
        String result = LoginManager.extractToken(null);

        assertNull(result);
    }

    /**
     * Tests that the processLogin method returns a JSON object with a token when valid credentials are provided.
     * This test simulates a successful login scenario where the user provides valid username and password,
     * and verifies that the method returns a JSON object containing the JWT token.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test
    public void processLoginShouldReturnJSONWithTokenForValidCredentials() throws Exception {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<PasswordHash> passwordHashMock = mockStatic(PasswordHash.class);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);
             MockedStatic<LoginUtils> loginUtilsMock = mockStatic(LoginUtils.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            // Setup mocks
            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(requestData);
            passwordHashMock.when(() -> PasswordHash.getUserWithPassword("testuser", "testpass"))
                .thenReturn(Optional.of(user));

            setFieldWithReflection("entityProvider", loginManager, obDal);
            setFieldWithReflection("conn", loginManager, connectionProvider);

            when(obDal.get(Role.class, "role-id")).thenReturn(role);
            when(obDal.get(Organization.class, "org-id")).thenReturn(organization);
            when(obDal.get(Warehouse.class, "warehouse-id")).thenReturn(warehouse);
            when(obDal.get(Client.class, "client-id")).thenReturn(client);

            roleDefaults.org = "org-id";
            roleDefaults.warehouse = "warehouse-id";
            roleDefaults.client = "client-id";

            loginUtilsMock.when(() -> LoginUtils.getLoginDefaults("user-id", "role-id", connectionProvider))
                .thenReturn(roleDefaults);

            contextMock.when(() -> OBContext.setOBContext((HttpServletRequest) any())).thenAnswer(invocation -> null);
            contextMock.when(() -> OBContext.setOBContextInSession(any(), any())).thenAnswer(invocation -> null);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq("session-id")))
                .thenReturn("test-jwt-token");

            JSONObject result = loginManager.processLogin(request);

            assertNotNull(result);
            assertTrue(result.has("token"));
            assertEquals("test-jwt-token", result.getString("token"));

            verify(session).setMaxInactiveInterval(3600);
        }
    }

    /**
     * Tests that the processLogin method returns a JSON object with a token when a valid JWT token is provided.
     * This test simulates a scenario where the user provides a valid JWT token in the Authorization header,
     * and verifies that the method correctly processes the token and returns a JSON object with the token.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test
    public void processLoginShouldReturnJSONWithTokenForValidToken() throws Exception {
        JSONObject emptyData = new JSONObject();
        String token = "valid-jwt-token";

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);
             MockedStatic<LoginUtils> loginUtilsMock = mockStatic(LoginUtils.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(emptyData);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.decodeToken(token))
                .thenReturn(decodedJWT);

            when(decodedJWT.getClaim("user")).thenReturn(claim);
            when(decodedJWT.getClaim("role")).thenReturn(claim);
            when(decodedJWT.getClaim("organization")).thenReturn(claim);
            when(decodedJWT.getClaim("warehouse")).thenReturn(claim);
            when(claim.asString()).thenReturn("user-id", "role-id", "org-id", "warehouse-id");

            setFieldWithReflection("entityProvider", loginManager, obDal);
            setFieldWithReflection("conn", loginManager, connectionProvider);

            when(obDal.get(User.class, "user-id")).thenReturn(user);
            when(obDal.get(Role.class, "role-id")).thenReturn(role);
            when(obDal.get(Organization.class, "org-id")).thenReturn(organization);
            when(obDal.get(Warehouse.class, "warehouse-id")).thenReturn(warehouse);
            when(obDal.get(Client.class, "client-id")).thenReturn(client);

            roleDefaults.org = "org-id";
            roleDefaults.warehouse = "warehouse-id";
            roleDefaults.client = "client-id";

            loginUtilsMock.when(() -> LoginUtils.getLoginDefaults("user-id", "role-id", connectionProvider))
                .thenReturn(roleDefaults);

            contextMock.when(() -> OBContext.setOBContext((HttpServletRequest) any())).thenAnswer(invocation -> null);
            contextMock.when(() -> OBContext.setOBContextInSession(any(), any())).thenAnswer(invocation -> null);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq("session-id")))
                .thenReturn("test-jwt-token");

            JSONObject result = loginManager.processLogin(request);

            assertNotNull(result);
            assertTrue(result.has("token"));
            assertEquals("test-jwt-token", result.getString("token"));
        }
    }

    /**
     * Tests that the processLogin method throws an UnauthorizedException when no credentials or token are provided.
     * This test simulates a scenario where the request does not contain any login data,
     * and verifies that the method correctly throws an UnauthorizedException.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test(expected = UnauthorizedException.class)
    public void processLoginShouldThrowExceptionWhenNoCredentialsOrToken() throws Exception {
        JSONObject emptyData = new JSONObject();

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(emptyData);
            when(request.getHeader("Authorization")).thenReturn(null);

            loginManager.processLogin(request);
        }
    }

    /**
     * Tests that the processLogin method throws an UnauthorizedException when invalid credentials are provided.
     * This test simulates a scenario where the user credentials do not match any existing user,
     * and verifies that the method correctly throws an UnauthorizedException.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test(expected = UnauthorizedException.class)
    public void processLoginShouldThrowExceptionWhenInvalidCredentials() throws Exception {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<PasswordHash> passwordHashMock = mockStatic(PasswordHash.class)) {

            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(requestData);
            passwordHashMock.when(() -> PasswordHash.getUserWithPassword("testuser", "testpass"))
                .thenReturn(Optional.empty());

            loginManager.processLogin(request);
        }
    }

    /**
     * Tests that the processLogin method throws a RuntimeException when token decoding fails.
     * This test simulates a scenario where the JWT token cannot be decoded, and verifies that
     * the method correctly propagates this exception.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test(expected = RuntimeException.class)
    public void processLoginShouldThrowRuntimeExceptionWhenTokenDecodingFails() throws Exception {
        JSONObject emptyData = new JSONObject();
        String token = "invalid-jwt-token";

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class)) {

            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(emptyData);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.decodeToken(token))
                .thenThrow(new RuntimeException("Token decoding failed"));

            loginManager.processLogin(request);
        }
    }

    /**
     * Tests that the processLogin method uses the default role when the role is not provided in the request.
     * This test simulates a scenario where the user has a default role, and verifies that the login defaults
     * are correctly applied using that default role.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test
    public void processLoginShouldUseDefaultRoleWhenRoleNotProvided() throws Exception {
        JSONObject dataWithoutRole = new JSONObject();
        dataWithoutRole.put("username", "testuser");
        dataWithoutRole.put("password", "testpass");

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<PasswordHash> passwordHashMock = mockStatic(PasswordHash.class);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);
             MockedStatic<LoginUtils> loginUtilsMock = mockStatic(LoginUtils.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SecureWebServicesUtils> ignored1 = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored = mockStatic(BaseServlet.class)) {

            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(dataWithoutRole);
            passwordHashMock.when(() -> PasswordHash.getUserWithPassword("testuser", "testpass"))
                .thenReturn(Optional.of(user));

            setFieldWithReflection("entityProvider", loginManager, obDal);
            setFieldWithReflection("conn", loginManager, connectionProvider);

            when(user.getDefaultRole()).thenReturn(defaultRole);
            when(defaultRole.getId()).thenReturn("default-role-id");

            roleDefaults.org = "org-id";
            roleDefaults.warehouse = "warehouse-id";
            roleDefaults.client = "client-id";

            loginUtilsMock.when(() -> LoginUtils.getLoginDefaults("user-id", "default-role-id", connectionProvider))
                .thenReturn(roleDefaults);

            when(obDal.get(Organization.class, "org-id")).thenReturn(organization);
            when(obDal.get(Warehouse.class, "warehouse-id")).thenReturn(warehouse);
            when(obDal.get(Client.class, "client-id")).thenReturn(client);

            contextMock.when(() -> OBContext.setOBContext((HttpServletRequest) any())).thenAnswer(invocation -> null);
            contextMock.when(() -> OBContext.setOBContextInSession(any(), any())).thenAnswer(invocation -> null);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq("session-id")))
                .thenReturn("test-jwt-token");

            JSONObject result = loginManager.processLogin(request);

            assertNotNull(result);
            assertTrue(result.has("token"));
        }
    }

    /**
     * Tests that the processLogin method uses the first user role when no default role is set.
     * This test simulates a scenario where the user has no default role, and verifies that
     * the first user role is used for login defaults.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test
    public void processLoginShouldUseFirstUserRoleWhenNoDefaultRole() throws Exception {
        JSONObject dataWithoutRole = new JSONObject();
        dataWithoutRole.put("username", "testuser");
        dataWithoutRole.put("password", "testpass");

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<PasswordHash> passwordHashMock = mockStatic(PasswordHash.class);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);
             MockedStatic<LoginUtils> loginUtilsMock = mockStatic(LoginUtils.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(dataWithoutRole);
            passwordHashMock.when(() -> PasswordHash.getUserWithPassword("testuser", "testpass"))
                .thenReturn(Optional.of(user));

            setFieldWithReflection("entityProvider", loginManager, obDal);
            setFieldWithReflection("conn", loginManager, connectionProvider);

            when(user.getDefaultRole()).thenReturn(null);

            roleDefaults.org = "org-id";
            roleDefaults.warehouse = "warehouse-id";
            roleDefaults.client = "client-id";

            loginUtilsMock.when(() -> LoginUtils.getLoginDefaults("user-id", "role-id", connectionProvider))
                .thenReturn(roleDefaults);

            when(obDal.get(Organization.class, "org-id")).thenReturn(organization);
            when(obDal.get(Warehouse.class, "warehouse-id")).thenReturn(warehouse);
            when(obDal.get(Client.class, "client-id")).thenReturn(client);

            contextMock.when(() -> OBContext.setOBContext((HttpServletRequest) any())).thenAnswer(invocation -> null);
            contextMock.when(() -> OBContext.setOBContextInSession(any(), any())).thenAnswer(invocation -> null);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq("session-id")))
                .thenReturn("test-jwt-token");

            JSONObject result = loginManager.processLogin(request);

            assertNotNull(result);
            assertTrue(result.has("token"));
        }
    }

    /**
     * Tests that the processLogin method throws an InternalServerException when JWT creation fails.
     * This test simulates a scenario where the JWT creation process throws a JWTCreationException,
     * and verifies that the method correctly propagates this exception as an InternalServerException.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test(expected = InternalServerException.class)
    public void processLoginShouldThrowInternalServerExceptionWhenJWTCreationFails() throws Exception {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<PasswordHash> passwordHashMock = mockStatic(PasswordHash.class);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);
             MockedStatic<LoginUtils> loginUtilsMock = mockStatic(LoginUtils.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(requestData);
            passwordHashMock.when(() -> PasswordHash.getUserWithPassword("testuser", "testpass"))
                .thenReturn(Optional.of(user));

            setFieldWithReflection("entityProvider", loginManager, obDal);
            setFieldWithReflection("conn", loginManager, connectionProvider);

            when(obDal.get(Role.class, "role-id")).thenReturn(role);
            when(obDal.get(Organization.class, "org-id")).thenReturn(organization);
            when(obDal.get(Warehouse.class, "warehouse-id")).thenReturn(warehouse);
            when(obDal.get(Client.class, "client-id")).thenReturn(client);

            roleDefaults.org = "org-id";
            roleDefaults.warehouse = "warehouse-id";
            roleDefaults.client = "client-id";

            loginUtilsMock.when(() -> LoginUtils.getLoginDefaults("user-id", "role-id", connectionProvider))
                .thenReturn(roleDefaults);

            contextMock.when(() -> OBContext.setOBContext((HttpServletRequest) any())).thenAnswer(invocation -> null);
            contextMock.when(() -> OBContext.setOBContextInSession(any(), any())).thenAnswer(invocation -> null);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq("session-id")))
                .thenThrow(new JWTCreationException("JWT creation failed", new RuntimeException()));

            loginManager.processLogin(request);
        }
    }

  /**
   * Tests that the processLogin method handles a JSONException gracefully.
   * This test simulates a scenario where the input JSONObject throws a JSONException
   * when attempting to retrieve the "role" property. It verifies that the processLogin
   * method does not crash and still returns a valid JSON object with a token.
   *
   * @throws Exception if any unexpected error occurs during the test execution
   */
    @Test
    public void processLoginShouldHandleJSONExceptionGracefully() throws Exception {
        JSONObject invalidData = mock(JSONObject.class);
        when(invalidData.has("username")).thenReturn(true);
        when(invalidData.has("password")).thenReturn(true);
        when(invalidData.optString("username", null)).thenReturn("testuser");
        when(invalidData.optString("password", null)).thenReturn("testpass");
        when(invalidData.has("role")).thenReturn(true);
        when(invalidData.getString("role")).thenThrow(new JSONException("JSON error"));

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<PasswordHash> passwordHashMock = mockStatic(PasswordHash.class);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);
             MockedStatic<LoginUtils> loginUtilsMock = mockStatic(LoginUtils.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(invalidData);
            passwordHashMock.when(() -> PasswordHash.getUserWithPassword("testuser", "testpass"))
                .thenReturn(Optional.of(user));

            setFieldWithReflection("entityProvider", loginManager, obDal);
            setFieldWithReflection("conn", loginManager, connectionProvider);

            when(user.getDefaultRole()).thenReturn(defaultRole);
            when(defaultRole.getId()).thenReturn("default-role-id");

            roleDefaults.org = "org-id";
            roleDefaults.warehouse = "warehouse-id";
            roleDefaults.client = "client-id";

            loginUtilsMock.when(() -> LoginUtils.getLoginDefaults("user-id", "default-role-id", connectionProvider))
                .thenReturn(roleDefaults);

            when(obDal.get(Organization.class, "org-id")).thenReturn(organization);
            when(obDal.get(Warehouse.class, "warehouse-id")).thenReturn(warehouse);
            when(obDal.get(Client.class, "client-id")).thenReturn(client);

            contextMock.when(() -> OBContext.setOBContext((HttpServletRequest) any())).thenAnswer(invocation -> null);
            contextMock.when(() -> OBContext.setOBContextInSession(any(), any())).thenAnswer(invocation -> null);

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq("session-id")))
                .thenReturn("test-jwt-token");

            JSONObject result = loginManager.processLogin(request);

            assertNotNull(result);
            assertTrue(result.has("token"));
        }
    }

    /**
     * Helper method to set a private field using reflection.
     *
     * @param fieldName the name of the field to set
     * @param targetObject the object whose field is to be set
     * @param value the value to set the field to
     */
    private void setFieldWithReflection(String fieldName, Object targetObject, Object value) {
        try {
            java.lang.reflect.Field field = LoginManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(targetObject, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}

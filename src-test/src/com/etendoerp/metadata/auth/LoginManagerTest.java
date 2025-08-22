package com.etendoerp.metadata.auth;

import static com.etendoerp.metadata.MetadataTestConstants.AUTHORIZATION;
import static com.etendoerp.metadata.MetadataTestConstants.CLIENT_ID;
import static com.etendoerp.metadata.MetadataTestConstants.DEFAULT_ROLE_ID;
import static com.etendoerp.metadata.MetadataTestConstants.ENTITY_PROVIDER;
import static com.etendoerp.metadata.MetadataTestConstants.ORG_ID;
import static com.etendoerp.metadata.MetadataTestConstants.PASSWORD;
import static com.etendoerp.metadata.MetadataTestConstants.ROLE_ID;
import static com.etendoerp.metadata.MetadataTestConstants.SESSION_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_JWT_TOKEN;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_PASSWORD;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_USER;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static com.etendoerp.metadata.MetadataTestConstants.USERNAME;
import static com.etendoerp.metadata.MetadataTestConstants.USER_ID;
import static com.etendoerp.metadata.MetadataTestConstants.WAREHOUSE_ID;
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

    @Mock private HttpServletRequest request;
    @Mock private HttpSession session;
    @Mock private User user;
    @Mock private Role role;
    @Mock private Role defaultRole;
    @Mock private Organization organization;
    @Mock private Warehouse warehouse;
    @Mock private Client client;
    @Mock private UserRoles userRoles;
    @Mock private OBDal obDal;
    @Mock private DalConnectionProvider connectionProvider;
    @Mock private DecodedJWT decodedJWT;
    @Mock private Claim claim;
    @Mock private LoginUtils.RoleDefaults roleDefaults;

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
        setupBasicMocks();
        setupRequestData();
        setupRoleDefaults();
    }

    /**
     * Sets up the basic mocks required for the tests.
     * This includes mocking the session, user, role, organization, warehouse, and client objects,
     * as well as setting up their IDs and default values.
     */
    private void setupBasicMocks() {
        when(request.getSession(true)).thenReturn(session);
        when(user.getId()).thenReturn(USER_ID);
        when(role.getId()).thenReturn(ROLE_ID);
        when(organization.getId()).thenReturn(ORG_ID);
        when(warehouse.getId()).thenReturn(WAREHOUSE_ID);
        when(client.getId()).thenReturn(CLIENT_ID);
        when(session.getId()).thenReturn(SESSION_ID);

        when(user.getDefaultRole()).thenReturn(defaultRole);
        List<UserRoles> userRolesList = new ArrayList<>();
        userRolesList.add(userRoles);
        when(user.getADUserRolesList()).thenReturn(userRolesList);
        when(userRoles.getRole()).thenReturn(role);
    }

    /**
     * Sets up the request data for login, including username, password, role, organization,
     * warehouse, and client information.
     *
     * @throws JSONException if there is an error creating the JSON object
     */
    private void setupRequestData() throws JSONException {
        requestData = new JSONObject();
        requestData.put(USERNAME, TEST_USER);
        requestData.put(PASSWORD, TEST_PASSWORD);
        requestData.put("role", ROLE_ID);
        requestData.put("organization", ORG_ID);
        requestData.put("warehouse", WAREHOUSE_ID);
        requestData.put("client", CLIENT_ID);
    }

    /**
     * Sets up the default values for the role used in the login process.
     * This includes setting the organization, warehouse, and client IDs for the role defaults.
     */
    private void setupRoleDefaults() {
        roleDefaults.org = ORG_ID;
        roleDefaults.warehouse = WAREHOUSE_ID;
        roleDefaults.client = CLIENT_ID;
    }

    /**
     * Sets up the LoginManager fields using reflection to inject the necessary dependencies.
     * This includes setting the entity provider and connection provider for the login manager.
     */
    private void setupLoginManagerFields() {
        setFieldWithReflection(ENTITY_PROVIDER, loginManager, obDal);
        setFieldWithReflection("conn", loginManager, connectionProvider);
    }

    /**
     * Mocks the necessary entity retrievals from the OBDal data access layer.
     * This method sets up the expected behavior for retrieving Role, Organization, Warehouse,
     * Client, and User entities based on their IDs.
     */
    private void setupEntityMocks() {
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(role);
        when(obDal.get(Organization.class, ORG_ID)).thenReturn(organization);
        when(obDal.get(Warehouse.class, WAREHOUSE_ID)).thenReturn(warehouse);
        when(obDal.get(Client.class, CLIENT_ID)).thenReturn(client);
        when(obDal.get(User.class, USER_ID)).thenReturn(user);
    }

    /**
     * Creates a mocked static Utils instance to return the request data.
     *
     * @param data the JSON object containing request data
     * @return a mocked static Utils instance
     */
    private MockedStatic<Utils> createUtilsMock(JSONObject data) {
        MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
        utilsMock.when(() -> Utils.getRequestData(request)).thenReturn(data);
        return utilsMock;
    }

    /**
     * Creates a mocked static PasswordHash instance to simulate user credential validation.
     *
     * @param validCredentials whether the credentials are valid
     * @return a mocked static PasswordHash instance
     */
    private MockedStatic<PasswordHash> createPasswordHashMock(boolean validCredentials) {
        MockedStatic<PasswordHash> passwordHashMock = mockStatic(PasswordHash.class);
        Optional<User> userResult = validCredentials ? Optional.of(user) : Optional.empty();
        passwordHashMock.when(() -> PasswordHash.getUserWithPassword(TEST_USER, TEST_PASSWORD))
            .thenReturn(userResult);
        return passwordHashMock;
    }

    /**
     * Creates a mocked static Utils instance to generate a JWT token.
     *
     * @return a mocked static Utils instance
     */
    private MockedStatic<com.etendoerp.metadata.auth.Utils> createAuthUtilsMock() {
        MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock =
            mockStatic(com.etendoerp.metadata.auth.Utils.class);
        authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq(SESSION_ID)))
            .thenReturn(TEST_JWT_TOKEN);
        return authUtilsMock;
    }

    /**
     * Creates a mocked static Utils instance to decode a JWT token.
     *
     * @param token the JWT token to decode
     * @return a mocked static Utils instance
     */
    private MockedStatic<com.etendoerp.metadata.auth.Utils> createAuthUtilsMockWithTokenDecoding(String token) {
        MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock = createAuthUtilsMock();
        authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.decodeToken(token))
            .thenReturn(decodedJWT);
        return authUtilsMock;
    }

    /**
     * Creates a mocked static LoginUtils instance to return role defaults.
     *
     * @param roleId the ID of the role for which to get defaults
     * @return a mocked static LoginUtils instance
     */
    private MockedStatic<LoginUtils> createLoginUtilsMock(String roleId) {
        MockedStatic<LoginUtils> loginUtilsMock = mockStatic(LoginUtils.class);
        loginUtilsMock.when(() -> LoginUtils.getLoginDefaults(USER_ID, roleId, connectionProvider))
            .thenReturn(roleDefaults);
        return loginUtilsMock;
    }

    /**
     * Creates a mocked static OBContext instance to set the context for the login process.
     *
     * @return a mocked static OBContext instance
     */
    private MockedStatic<OBContext> createOBContextMock() {
        MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
        contextMock.when(() -> OBContext.setOBContext((HttpServletRequest) any())).thenAnswer(invocation -> null);
        contextMock.when(() -> OBContext.setOBContextInSession(any(), any())).thenAnswer(invocation -> null);
        return contextMock;
    }

    /**
     * Sets up the claims for the decoded JWT token.
     * This method mocks the behavior of the decoded JWT to return specific claims for user, role,
     * organization, and warehouse when requested.
     */
    private void setupTokenClaims() {
        when(decodedJWT.getClaim("user")).thenReturn(claim);
        when(decodedJWT.getClaim("role")).thenReturn(claim);
        when(decodedJWT.getClaim("organization")).thenReturn(claim);
        when(decodedJWT.getClaim("warehouse")).thenReturn(claim);
        when(claim.asString()).thenReturn(USER_ID, ROLE_ID, ORG_ID, WAREHOUSE_ID);
    }

    /**
     * Executes a successful login process with the provided data and role ID.
     *
     * @param data the JSON object containing login data
     * @param roleId the ID of the role to use for login
     * @return a JSON object containing the login result
     * @throws Exception if any error occurs during the login process
     */
    private JSONObject executeSuccessfulLogin(JSONObject data, String roleId) throws Exception {
        try (MockedStatic<Utils> ignored2 = createUtilsMock(data);
             MockedStatic<PasswordHash> ignored3 = createPasswordHashMock(true);
             MockedStatic<com.etendoerp.metadata.auth.Utils> ignored6 = createAuthUtilsMock();
             MockedStatic<LoginUtils> ignored5 = createLoginUtilsMock(roleId);
             MockedStatic<OBContext> ignored4 = createOBContextMock();
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            setupLoginManagerFields();
            setupEntityMocks();
            return loginManager.processLogin(request);
        }
    }

    /**
     * Executes the login process using a JWT token.
     *
     * @param token the JWT token to use for login
     * @return a JSON object containing the login result
     * @throws Exception if any error occurs during the login process
     */
    private JSONObject executeTokenLogin(String token) throws Exception {
        JSONObject emptyData = new JSONObject();

        try (MockedStatic<Utils> ignored2 = createUtilsMock(emptyData);
             MockedStatic<com.etendoerp.metadata.auth.Utils> ignored3 = createAuthUtilsMockWithTokenDecoding(token);
             MockedStatic<LoginUtils> ignored4 = createLoginUtilsMock(ROLE_ID);
             MockedStatic<OBContext> ignored5 = createOBContextMock();
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            setupTokenClaims();
            setupLoginManagerFields();
            setupEntityMocks();
            return loginManager.processLogin(request);
        }
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
        JSONObject result = executeSuccessfulLogin(requestData, ROLE_ID);

        assertNotNull(result);
        assertTrue(result.has(TOKEN));
        assertEquals(TEST_JWT_TOKEN, result.getString(TOKEN));
        verify(session).setMaxInactiveInterval(3600);
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
        String token = "valid-jwt-token";
        JSONObject result = executeTokenLogin(token);

        assertNotNull(result);
        assertTrue(result.has(TOKEN));
        assertEquals(TEST_JWT_TOKEN, result.getString(TOKEN));
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

        try (MockedStatic<Utils> utilsMock = createUtilsMock(emptyData)) {
            when(request.getHeader(AUTHORIZATION)).thenReturn(null);
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
        try (MockedStatic<Utils> ignored = createUtilsMock(requestData);
             MockedStatic<PasswordHash> ignored1 = createPasswordHashMock(false)) {
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

        try (MockedStatic<Utils> utilsMock = createUtilsMock(emptyData);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock =
                 mockStatic(com.etendoerp.metadata.auth.Utils.class)) {

            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
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
        dataWithoutRole.put(USERNAME, TEST_USER);
        dataWithoutRole.put(PASSWORD, TEST_PASSWORD);

        when(defaultRole.getId()).thenReturn(DEFAULT_ROLE_ID);
        JSONObject result = executeSuccessfulLogin(dataWithoutRole, DEFAULT_ROLE_ID);

        assertNotNull(result);
        assertTrue(result.has(TOKEN));
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
        dataWithoutRole.put(USERNAME, TEST_USER);
        dataWithoutRole.put(PASSWORD, TEST_PASSWORD);

        when(user.getDefaultRole()).thenReturn(null);
        JSONObject result = executeSuccessfulLogin(dataWithoutRole, ROLE_ID);

        assertNotNull(result);
        assertTrue(result.has(TOKEN));
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
        try (MockedStatic<Utils> ignored2 = createUtilsMock(requestData);
             MockedStatic<PasswordHash> ignored3 = createPasswordHashMock(true);
             MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock =
                 mockStatic(com.etendoerp.metadata.auth.Utils.class);
             MockedStatic<LoginUtils> ignored4 = createLoginUtilsMock(ROLE_ID);
             MockedStatic<OBContext> ignored5 = createOBContextMock();
             MockedStatic<SecureWebServicesUtils> ignored = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<BaseServlet> ignored1 = mockStatic(BaseServlet.class)) {

            setupLoginManagerFields();
            setupEntityMocks();

            authUtilsMock.when(() -> com.etendoerp.metadata.auth.Utils.generateToken(any(AuthData.class), eq(SESSION_ID)))
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
        when(invalidData.has(USERNAME)).thenReturn(true);
        when(invalidData.has(PASSWORD)).thenReturn(true);
        when(invalidData.optString(USERNAME, null)).thenReturn(TEST_USER);
        when(invalidData.optString(PASSWORD, null)).thenReturn(TEST_PASSWORD);
        when(invalidData.has("role")).thenReturn(true);
        when(invalidData.getString("role")).thenThrow(new JSONException("JSON error"));

        when(defaultRole.getId()).thenReturn(DEFAULT_ROLE_ID);
        JSONObject result = executeSuccessfulLogin(invalidData, DEFAULT_ROLE_ID);

        assertNotNull(result);
        assertTrue(result.has(TOKEN));
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

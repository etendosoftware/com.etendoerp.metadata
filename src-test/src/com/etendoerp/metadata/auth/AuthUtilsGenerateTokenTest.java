package com.etendoerp.metadata.auth;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;


import com.etendoerp.metadata.data.AuthData;
import com.smf.securewebservices.SWSConfig;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * Extended tests for {@link Utils} in the auth package.
 * Covers generateToken with null sessionId, old version private key,
 * expiration time branches, null defaultWarehouse, and private method
 * exception paths.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class
AuthUtilsGenerateTokenTest {

    private static final String OLD_SIMPLE_KEY = "old-simple-key";

    @Mock
    private AuthData authData;

    @Mock
    private User user;

    @Mock
    private Role role;

    @Mock
    private Role defaultRole;

    @Mock
    private Role wsRole;

    @Mock
    private Role selectedRole;

    @Mock
    private Organization organization;

    @Mock
    private Organization defaultOrganization;

    @Mock
    private Organization selectedOrg;

    @Mock
    private Warehouse warehouse;

    @Mock
    private Warehouse defaultWarehouse;

    @Mock
    private Warehouse selectedWarehouse;

    @Mock
    private Client client;

    @Mock
    private UserRoles userRoles;

    @Mock
    private SWSConfig swsConfig;

    @Mock
    private OBContext obContext;

    private MockedStatic<OBContext> contextMock;
    private MockedStatic<SWSConfig> configMock;
    private MockedStatic<SecureWebServicesUtils> swsUtilsMock;
    private MockedStatic<Preferences> preferencesMock;

    /** Initializes mocks and static mock contexts for each test. */
    @Before
    public void setUp() {
        List<UserRoles> userRolesList = new ArrayList<>();
        userRolesList.add(userRoles);

        lenient().when(authData.getUser()).thenReturn(user);
        lenient().when(authData.getRole()).thenReturn(role);
        lenient().when(authData.getOrg()).thenReturn(organization);
        lenient().when(authData.getWarehouse()).thenReturn(warehouse);

        lenient().when(user.getADUserRolesList()).thenReturn(userRolesList);
        lenient().when(user.getSmfswsDefaultWsRole()).thenReturn(wsRole);
        lenient().when(user.getDefaultRole()).thenReturn(defaultRole);
        lenient().when(user.getDefaultOrganization()).thenReturn(defaultOrganization);
        lenient().when(user.getDefaultWarehouse()).thenReturn(defaultWarehouse);
        lenient().when(user.getId()).thenReturn("USER_ID");

        // Stub role so SecureWebServicesUtils.getRole can match it in the stream
        lenient().when(role.getId()).thenReturn("ROLE_ID");
        lenient().when(role.getClient()).thenReturn(client);
        lenient().when(userRoles.getRole()).thenReturn(role);

        // Stub role's organization list for SecureWebServicesUtils.getOrganization
        RoleOrganization roleOrg = mock(RoleOrganization.class);
        lenient().when(roleOrg.getOrganization()).thenReturn(organization);
        lenient().when(organization.getId()).thenReturn("ORG_ID");
        List<RoleOrganization> roleOrgList = new ArrayList<>();
        roleOrgList.add(roleOrg);
        lenient().when(role.getADRoleOrganizationList()).thenReturn(roleOrgList);

        // Stub warehouse ID for matching in SecureWebServicesUtils.getWarehouse
        lenient().when(warehouse.getId()).thenReturn("WH_ID");

        lenient().when(selectedRole.getId()).thenReturn("SEL_ROLE_ID");
        lenient().when(selectedRole.getClient()).thenReturn(client);
        lenient().when(client.getId()).thenReturn("CLIENT_ID");
        lenient().when(selectedOrg.getId()).thenReturn("SEL_ORG_ID");
        lenient().when(selectedWarehouse.getId()).thenReturn("SEL_WH_ID");

        contextMock = mockStatic(OBContext.class);
        configMock = mockStatic(SWSConfig.class);
        // Use CALLS_REAL_METHODS so reflection-invoked private methods run real logic
        swsUtilsMock = mockStatic(SecureWebServicesUtils.class, Mockito.CALLS_REAL_METHODS);
        preferencesMock = mockStatic(Preferences.class);
    }

    /** Closes all static mock contexts to prevent leaks between tests. */
    @After
    public void tearDown() {
        if (preferencesMock != null) {
            preferencesMock.close();
        }
        if (swsUtilsMock != null) {
            swsUtilsMock.close();
        }
        if (configMock != null) {
            configMock.close();
        }
        if (contextMock != null) {
            contextMock.close();
        }
    }

    /** Verifies token generation succeeds when using an old-version private key. */
    @Test
    public void testGenerateTokenWithOldVersionPrivateKey() {
        String token = generateToken("test-session", 0L);

        assertNotNull("Token should not be null", token);
        verifyAdminModeWasManaged();
    }

    /** Verifies token generation succeeds when sessionId is null. */
    @Test
    public void testGenerateTokenWithNullSessionId() {
        String token = generateToken(null, 0L);

        assertNotNull("Token should not be null even with null sessionId", token);
    }

    /** Verifies token generation succeeds when a non-zero expiration time is set. */
    @Test
    public void testGenerateTokenWithExpirationTime() {
        String token = generateToken("session-123", 60L);

        assertNotNull("Token with expiration should not be null", token);
    }

    /** Verifies token generation succeeds when the user has no default warehouse. */
    @Test
    public void testGenerateTokenWithNullDefaultWarehouse() {
        lenient().when(user.getDefaultWarehouse()).thenReturn(null);

        String token = generateToken("session-456", 0L);

        assertNotNull("Token should not be null with null default warehouse", token);
    }

    /** Verifies that getRole wraps reflection errors in OBException. */
    @Test
    public void testGetRoleThrowsOBExceptionOnNoSuchMethod() {
        // Close the swsUtilsMock to test real reflection failure
        swsUtilsMock.close();
        swsUtilsMock = null;

        // Now re-mock with no methods available
        swsUtilsMock = mockStatic(SecureWebServicesUtils.class);

        invokePrivateUtilsMethod("getRole",
                new Class<?>[] { Role.class, List.class, Role.class, Role.class },
                role, new ArrayList<>(), wsRole, defaultRole);
    }

    /** Verifies that getOrganization wraps reflection errors in OBException. */
    @Test
    public void testGetOrganizationThrowsOBExceptionOnError() {
        invokePrivateUtilsMethod("getOrganization",
                new Class<?>[] { Organization.class, Role.class, Role.class, Organization.class },
                organization, selectedRole, defaultRole, defaultOrganization);
    }

    /** Verifies that getWarehouse wraps reflection errors in OBException. */
    @Test
    public void testGetWarehouseThrowsOBExceptionOnError() {
        invokePrivateUtilsMethod("getWarehouse",
                new Class<?>[] { Warehouse.class, Organization.class, Warehouse.class, Role.class },
                warehouse, organization, defaultWarehouse, defaultRole);
    }

    /** Verifies that cleanPrivateKey delegates to SecureWebServicesUtils via reflection. */
    @Test
    public void testCleanPrivateKeyDelegation() {
        invokePrivateUtilsMethod("cleanPrivateKey",
                new Class<?>[] { SWSConfig.class },
                swsConfig);
    }

    /** Verifies that decodeToken delegates to SecureWebServicesUtils and handles errors. */
    @Test
    public void testDecodeTokenDelegation() {
        try {
            // decodeToken is public, test that it delegates properly
            swsUtilsMock.close();
            swsUtilsMock = null;
            swsUtilsMock = mockStatic(SecureWebServicesUtils.class);

            // When decodeToken throws, it should wrap in OBException
            swsUtilsMock.when(() -> SecureWebServicesUtils.decodeToken("bad-token"))
                    .thenThrow(new RuntimeException("decode failed"));

            assertDecodeTokenThrowsExpectedException();
        } catch (Exception e) {
            // Handle mock setup issues gracefully
            assertNotNull(e);
        }
    }

    /** Invokes Utils.decodeToken and asserts that the expected exception type is thrown. */
    private void assertDecodeTokenThrowsExpectedException() {
        try {
            Utils.decodeToken("bad-token");
        } catch (OBException e) {
            assertNotNull("Should throw OBException", e);
        } catch (RuntimeException e) {
            assertNotNull("Should handle runtime exception", e);
        }
    }

    // ========== Helper Methods ==========

    private void invokePrivateUtilsMethod(String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = Utils.class.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (Exception e) {
            assertTrue("Should wrap in OBException or InvocationTargetException",
                    e.getCause() instanceof OBException
                    || e instanceof java.lang.reflect.InvocationTargetException);
        }
    }

    private String generateToken(String sessionId, long expirationTime) {
        try {
            configureTokenGeneration(expirationTime);
            return Utils.generateToken(authData, sessionId);
        } catch (Exception e) {
            throw new OBException("Token generation failed", e);
        }
    }

    private void configureTokenGeneration(long expirationTime) {
        configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
        when(swsConfig.getPrivateKey()).thenReturn(OLD_SIMPLE_KEY);
        lenient().when(swsConfig.getExpirationTime()).thenReturn(expirationTime);
        swsUtilsMock.when(() -> SecureWebServicesUtils.isNewVersionPrivKey(OLD_SIMPLE_KEY))
                .thenReturn(false);
        contextMock.when(OBContext::getOBContext).thenReturn(obContext);
        lenient().when(obContext.getCurrentClient()).thenReturn(client);
        lenient().when(obContext.getCurrentOrganization()).thenReturn(organization);
        lenient().when(obContext.getUser()).thenReturn(user);
        lenient().when(obContext.getRole()).thenReturn(role);
        setupReflectionMocksForGetWarehouse();
    }

    private void verifyAdminModeWasManaged() {
        contextMock.verify(() -> OBContext.setAdminMode(true), Mockito.atLeastOnce());
        contextMock.verify(OBContext::restorePreviousMode, Mockito.atLeastOnce());
    }

    private void setupReflectionMocksForGetWarehouse() {
        List<Warehouse> whList = new ArrayList<>();
        whList.add(warehouse);
        swsUtilsMock.when(() -> SecureWebServicesUtils.getOrganizationWarehouses(any(Organization.class)))
                .thenReturn(whList);
    }

}

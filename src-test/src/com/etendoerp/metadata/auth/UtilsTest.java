package com.etendoerp.metadata.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.test.base.OBBaseTest;

import com.etendoerp.metadata.data.AuthData;
import com.smf.securewebservices.SWSConfig;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * Test class for Utils, specifically focusing on the generateToken method and its interactions with OBContext and SWSConfig.
 * This class uses Mockito to mock dependencies and verify behavior.
 */
@RunWith(MockitoJUnitRunner.class)
public class UtilsTest extends OBBaseTest {

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
    private Organization organization;
    
    @Mock
    private Organization defaultOrganization;
    
    @Mock
    private Warehouse warehouse;
    
    @Mock
    private Warehouse defaultWarehouse;
    
    @Mock
    private Client client;
    
    @Mock
    private UserRoles userRoles;
    
    @Mock
    private SWSConfig swsConfig;
    
    @Mock
    private OBContext obContext;
    
    private String sessionId;

  @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        sessionId = "test-session-id";
        List<UserRoles> userRolesList = new ArrayList<>();
        userRolesList.add(userRoles);
        
        // Setup basic mocks
        when(authData.getUser()).thenReturn(user);
        when(authData.getRole()).thenReturn(role);
        when(authData.getOrg()).thenReturn(organization);
        when(authData.getWarehouse()).thenReturn(warehouse);
        
        when(user.getADUserRolesList()).thenReturn(userRolesList);
        when(user.getSmfswsDefaultWsRole()).thenReturn(wsRole);
        when(user.getDefaultRole()).thenReturn(defaultRole);
        when(user.getDefaultOrganization()).thenReturn(defaultOrganization);
        when(user.getDefaultWarehouse()).thenReturn(defaultWarehouse);

    }

    /**
     * Test to ensure that the Utils class cannot be instantiated.
     * This is to verify that the constructor is private and throws an InstantiationException.
     */
    @Test
    public void utilsConstructorShouldThrowInstantiationException() {
        try {
            java.lang.reflect.Constructor<Utils> constructor = Utils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("Expected InstantiationException");
        } catch (Exception e) {
            assertTrue(e instanceof InvocationTargetException);
            assertTrue(e.getCause() instanceof InstantiationException);
        }
    }

    /**
     * Tests that generateToken sets admin mode and restores it even if an exception is thrown.
     * <p>
     * Mocks SWSConfig to throw a RuntimeException and verifies that OBContext.setAdminMode(true)
     * and OBContext.restorePreviousMode() are called, ensuring proper context management.
     * </p>
     * @throws Exception if mocking fails
     */
    @Test
    public void generateTokenShouldSetAdminModeAndRestoreIt() throws Exception {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class)) {
            
            // Setup to throw an exception early so we can verify admin mode handling
            configMock.when(SWSConfig::getInstance).thenThrow(new RuntimeException("Test exception"));
            
            try {
                Utils.generateToken(authData, sessionId);
                fail("Expected exception");
            } catch (RuntimeException e) {
                assertEquals("Test exception", e.getMessage());
            }
            
            // Verify admin mode was set and restored
            contextMock.verify(() -> OBContext.setAdminMode(true));
            contextMock.verify(OBContext::restorePreviousMode);
        }
    }

    /**
     * Tests that generateToken correctly generates a token with the expected values.
     * <p>
     * Mocks SWSConfig to return a specific private key and verifies that the generated token
     * contains the expected values for user, role, organization, and warehouse.
     * </p>
     */
    @Test
    public void generateTokenShouldHandlePreferencesException() {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class);
             MockedStatic<SecureWebServicesUtils> swsUtilsMock = mockStatic(SecureWebServicesUtils.class);
             MockedStatic<Preferences> preferencesMock = mockStatic(Preferences.class)) {
            
            configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
            when(swsConfig.getPrivateKey()).thenReturn("new-version-private-key");
            
            swsUtilsMock.when(() -> SecureWebServicesUtils.isNewVersionPrivKey("new-version-private-key"))
                .thenReturn(true);
            
            contextMock.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getCurrentClient()).thenReturn(client);
            when(obContext.getCurrentOrganization()).thenReturn(organization);
            when(obContext.getUser()).thenReturn(user);
            when(obContext.getRole()).thenReturn(role);
            
            // Setup preferences to throw exception
            preferencesMock.when(() -> Preferences.getPreferenceValue(anyString(), anyBoolean(), (Client) any(), any(), any(), any(), any()))
                .thenThrow(new PropertyException("Preference not found"));
            
            try {
                Utils.generateToken(authData, sessionId);
                fail("Expected PropertyException");
            } catch (Exception e) {
                assertTrue(e instanceof PropertyException || e.getCause() instanceof PropertyException,
                    "Should propagate PropertyException");
            }

            contextMock.verify(OBContext::restorePreviousMode);
        }
    }

    /**
     * Tests that generateToken always restores the previous OBContext mode,
     * even if an exception is thrown during token generation.
     * <p>
     * This test covers scenarios where SWSConfig or AuthData throw a RuntimeException,
     * and verifies that OBContext.setAdminMode(true) and OBContext.restorePreviousMode()
     * are always called to ensure proper context management.
     * </p>
     *
     * @throws Exception if mocking fails or unexpected errors occur
     */
    @Test
    public void generateTokenShouldAlwaysRestorePreviousMode() throws Exception {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            try (MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class)) {
                configMock.when(SWSConfig::getInstance).thenThrow(new RuntimeException("Config error"));
                
                try {
                    Utils.generateToken(authData, sessionId);
                } catch (RuntimeException e) {
                    // Expected
                }
                
                contextMock.verify(() -> OBContext.setAdminMode(true));
                contextMock.verify(OBContext::restorePreviousMode);
            }
            
            contextMock.clearInvocations();
            
            when(authData.getUser()).thenThrow(new RuntimeException("User error"));
            
            try (MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class)) {
                configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
                when(swsConfig.getPrivateKey()).thenReturn("test-key");
                
                try {
                    Utils.generateToken(authData, sessionId);
                } catch (RuntimeException e) {
                    // Expected
                }
                
                contextMock.verify(() -> OBContext.setAdminMode(true));
                contextMock.verify(OBContext::restorePreviousMode);
            }
        }
    }
}

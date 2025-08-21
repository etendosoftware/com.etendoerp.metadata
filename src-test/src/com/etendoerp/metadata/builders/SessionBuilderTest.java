package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.ATTRIBUTES;
import static com.etendoerp.metadata.MetadataTestConstants.ORGANIZATIONS;
import static com.etendoerp.metadata.MetadataTestConstants.ROLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.common.enterprise.OrgWarehouse;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.data.RequestVariables;

/**
 * Unit tests for SessionBuilder using simplified mocking approach.
 * This approach avoids complex object dependencies and focuses on testing the core logic.
 */
@ExtendWith(MockitoExtension.class)
class SessionBuilderTest {

  /**
   * Tests the toJSON method of SessionBuilder with a mocked OBContext and RequestContext.
   * This ensures that the method correctly retrieves user, role, organization, client, and warehouse information
   * and returns it in a JSON format.
   *
   * @throws Exception
   *     if any error occurs during the test execution
   */
  @Test
  void testToJSONSuccessWithCompleteHierarchy() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    RequestContext mockRequestContext = mock(RequestContext.class);
    RequestVariables mockRequestVariables = mock(RequestVariables.class);
    Language mockLanguage = mock(Language.class);

    User mockUser = mock(User.class);
    Role mockRole = mock(Role.class);
    Organization mockOrganization = mock(Organization.class);
    Client mockClient = mock(Client.class);
    Warehouse mockWarehouse = mock(Warehouse.class);
    UserRoles mockUserRoles = mock(UserRoles.class);
    RoleOrganization mockRoleOrganization = mock(RoleOrganization.class);
    OrgWarehouse mockOrgWarehouse = mock(OrgWarehouse.class);

    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put("testAttr", "testValue");

    when(mockContext.getUser()).thenReturn(mockUser);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
    when(mockContext.getCurrentClient()).thenReturn(mockClient);
    when(mockContext.getWarehouse()).thenReturn(mockWarehouse);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    when(mockRequestContext.getVariablesSecureApp()).thenReturn(mockRequestVariables);
    when(mockRequestVariables.getCasedSessionAttributes()).thenReturn(sessionAttributes);

    List<UserRoles> userRolesList = new ArrayList<>();
    userRolesList.add(mockUserRoles);
    when(mockUser.getADUserRolesList()).thenReturn(userRolesList);
    when(mockUserRoles.getRole()).thenReturn(mockRole);

    when(mockRole.getId()).thenReturn("role-123");
    when(mockRole.getClient()).thenReturn(mockClient);

    List<RoleOrganization> roleOrgList = new ArrayList<>();
    roleOrgList.add(mockRoleOrganization);
    when(mockRole.getADRoleOrganizationList()).thenReturn(roleOrgList);
    when(mockRoleOrganization.getOrganization()).thenReturn(mockOrganization);

    when(mockOrganization.getId()).thenReturn("org-456");

    List<OrgWarehouse> orgWarehouseList = new ArrayList<>();
    orgWarehouseList.add(mockOrgWarehouse);
    when(mockOrganization.getOrganizationWarehouseList()).thenReturn(orgWarehouseList);
    when(mockOrgWarehouse.getWarehouse()).thenReturn(mockWarehouse);

    when(mockWarehouse.getId()).thenReturn("warehouse-789");

    lenient().when(mockRole.get(anyString(), any(), anyString())).thenReturn("Test Role");
    lenient().when(mockClient.get(anyString(), any(), anyString())).thenReturn("Test Client");
    lenient().when(mockOrganization.get(anyString(), any(), anyString())).thenReturn("Test Organization");
    lenient().when(mockWarehouse.get(anyString(), any(), anyString())).thenReturn("Test Warehouse");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<RequestContext> mockedRequestContext = mockStatic(RequestContext.class);
         MockedConstruction<LanguageBuilder> ignored = mockConstruction(LanguageBuilder.class,
             (mock, context) -> {
               JSONObject languagesJson = new JSONObject();
               languagesJson.put("en_US", new JSONObject().put("name", "English"));
               when(mock.toJSON()).thenReturn(languagesJson);
             });
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> when(mock.toJsonObject(any(), any())).thenReturn(new JSONObject().put("id", "mock-id")))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedRequestContext.when(RequestContext::get).thenReturn(mockRequestContext);

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("user"));
      assertTrue(result.has("currentRole"));
      assertTrue(result.has("currentClient"));
      assertTrue(result.has("currentOrganization"));
      assertTrue(result.has("currentWarehouse"));
      assertTrue(result.has(ROLES));
      assertTrue(result.has(ATTRIBUTES));
      assertTrue(result.has("languages"));

      JSONArray roles = result.getJSONArray(ROLES);
      assertNotNull(roles);

      if (roles.length() > 0) {
        JSONObject roleJson = roles.getJSONObject(0);
        assertTrue(roleJson.has("id"));
        assertTrue(roleJson.has("name"));
        assertTrue(roleJson.has(ORGANIZATIONS));

        JSONArray organizations = roleJson.getJSONArray(ORGANIZATIONS);
        assertNotNull(organizations);

        if (organizations.length() > 0) {
          JSONObject orgJson = organizations.getJSONObject(0);
          assertTrue(orgJson.has("id"));
          assertTrue(orgJson.has("name"));
          assertTrue(orgJson.has("warehouses"));

          JSONArray warehouses = orgJson.getJSONArray("warehouses");
          assertNotNull(warehouses);

          if (warehouses.length() > 0) {
            JSONObject warehouseJson = warehouses.getJSONObject(0);
            assertTrue(warehouseJson.has("id"));
            assertTrue(warehouseJson.has("name"));
          }
        }
      }

      JSONObject attributes = result.getJSONObject(ATTRIBUTES);
      assertEquals("testValue", attributes.getString("testAttr"));
    }
  }

  /**
   * Tests the toJSON method of SessionBuilder when the user has no roles.
   * This ensures that the method can handle a scenario where the user has no roles assigned,
   * and still returns a valid JSON structure with an empty roles array.
   *
   * @throws Exception
   *     if any error occurs during the test execution
   */
  @Test
  void testToJSONWithEmptyUserRoles() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    RequestContext mockRequestContext = mock(RequestContext.class);
    RequestVariables mockRequestVariables = mock(RequestVariables.class);
    Language mockLanguage = mock(Language.class);

    User mockUser = mock(User.class);
    Role mockRole = mock(Role.class);
    Organization mockOrganization = mock(Organization.class);
    Client mockClient = mock(Client.class);
    Warehouse mockWarehouse = mock(Warehouse.class);

    when(mockContext.getUser()).thenReturn(mockUser);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
    when(mockContext.getCurrentClient()).thenReturn(mockClient);
    when(mockContext.getWarehouse()).thenReturn(mockWarehouse);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    when(mockRequestContext.getVariablesSecureApp()).thenReturn(mockRequestVariables);
    when(mockRequestVariables.getCasedSessionAttributes()).thenReturn(new HashMap<>());

    when(mockUser.getADUserRolesList()).thenReturn(new ArrayList<>());

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<RequestContext> mockedRequestContext = mockStatic(RequestContext.class);
         MockedConstruction<LanguageBuilder> ignored = mockConstruction(LanguageBuilder.class,
             (mock, context) -> when(mock.toJSON()).thenReturn(new JSONObject()));
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> when(mock.toJsonObject(any(), any())).thenReturn(new JSONObject()))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedRequestContext.when(RequestContext::get).thenReturn(mockRequestContext);

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(0, roles.length());
    }
  }

  /**
   * Tests the toJSON method of SessionBuilder when an exception is thrown in getRoles.
   * This ensures that if an exception occurs while retrieving roles for a user,
   * the method still returns a valid JSON structure with an empty roles array.
   *
   * @throws Exception
   *     if any error occurs during the test execution
   */
  @Test
  void testToJSONHandlesExceptionInGetRoles() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    RequestContext mockRequestContext = mock(RequestContext.class);
    RequestVariables mockRequestVariables = mock(RequestVariables.class);
    Language mockLanguage = mock(Language.class);

    User mockUser = mock(User.class);
    Role mockRole = mock(Role.class);
    Organization mockOrganization = mock(Organization.class);
    Client mockClient = mock(Client.class);
    Warehouse mockWarehouse = mock(Warehouse.class);

    when(mockContext.getUser()).thenReturn(mockUser);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
    when(mockContext.getCurrentClient()).thenReturn(mockClient);
    when(mockContext.getWarehouse()).thenReturn(mockWarehouse);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    when(mockRequestContext.getVariablesSecureApp()).thenReturn(mockRequestVariables);
    when(mockRequestVariables.getCasedSessionAttributes()).thenReturn(new HashMap<>());

    when(mockUser.getADUserRolesList()).thenThrow(new RuntimeException("Database error"));

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<RequestContext> mockedRequestContext = mockStatic(RequestContext.class);
         MockedConstruction<LanguageBuilder> ignored = mockConstruction(LanguageBuilder.class,
             (mock, context) -> when(mock.toJSON()).thenReturn(new JSONObject()));
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> when(mock.toJsonObject(any(), any())).thenReturn(new JSONObject()))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedRequestContext.when(RequestContext::get).thenReturn(mockRequestContext);

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(0, roles.length());
    }
  }

  /**
   * Tests the constructor of SessionBuilder when an exception is thrown during OBContext initialization.
   * This ensures that the constructor handles exceptions gracefully and does not leave the system in an inconsistent state.
   */
  @Test
  void testToJSONHandlesExceptionInConstructor() {
    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {

      mockedOBContext.when(OBContext::getOBContext).thenThrow(new RuntimeException("Context initialization failed"));

      assertThrows(RuntimeException.class, SessionBuilder::new);
    }
  }

  /**
   * Tests the toJSON method of SessionBuilder when an exception is thrown in getOrganizations.
   * This ensures that if an exception occurs while retrieving organizations for a role,
   * the method still returns a valid JSON structure with an empty organizations array for that role.
   *
   * @throws Exception
   *     if any error occurs during the test execution
   */
  @Test
  void testToJSONWithOrganizationException() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    RequestContext mockRequestContext = mock(RequestContext.class);
    RequestVariables mockRequestVariables = mock(RequestVariables.class);
    Language mockLanguage = mock(Language.class);

    User mockUser = mock(User.class);
    Role mockRole = mock(Role.class);
    UserRoles mockUserRoles = mock(UserRoles.class);
    Organization mockOrganization = mock(Organization.class);
    Client mockClient = mock(Client.class);
    Warehouse mockWarehouse = mock(Warehouse.class);

    when(mockContext.getUser()).thenReturn(mockUser);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
    when(mockContext.getCurrentClient()).thenReturn(mockClient);
    when(mockContext.getWarehouse()).thenReturn(mockWarehouse);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    when(mockRequestContext.getVariablesSecureApp()).thenReturn(mockRequestVariables);
    when(mockRequestVariables.getCasedSessionAttributes()).thenReturn(new HashMap<>());

    List<UserRoles> userRolesList = new ArrayList<>();
    userRolesList.add(mockUserRoles);
    when(mockUser.getADUserRolesList()).thenReturn(userRolesList);
    when(mockUserRoles.getRole()).thenReturn(mockRole);

    when(mockRole.getId()).thenReturn("role-id");
    when(mockRole.getClient()).thenReturn(mockClient);
    when(mockRole.getADRoleOrganizationList()).thenThrow(new RuntimeException("Organization access error"));

    lenient().when(mockRole.get(anyString(), any(), anyString())).thenReturn("Test Role");
    lenient().when(mockClient.get(anyString(), any(), anyString())).thenReturn("Test Client");

    // When
    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<RequestContext> mockedRequestContext = mockStatic(RequestContext.class);
         MockedConstruction<LanguageBuilder> ignored = mockConstruction(LanguageBuilder.class,
             (mock, context) -> when(mock.toJSON()).thenReturn(new JSONObject()));
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> when(mock.toJsonObject(any(), any())).thenReturn(new JSONObject()))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedRequestContext.when(RequestContext::get).thenReturn(mockRequestContext);

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(1, roles.length());

      JSONObject roleJson = roles.getJSONObject(0);
      assertEquals("role-id", roleJson.getString("id"));
      JSONArray organizations = roleJson.getJSONArray(ORGANIZATIONS);
      assertEquals(0, organizations.length());
    }
  }

  /**
   * Test toJSON method with minimal data setup.
   * This test ensures that the method can handle a scenario where only basic user and role information is available.
   */
  @Test
  void testToJSONWithMinimalData() {
    OBContext mockContext = mock(OBContext.class);
    RequestContext mockRequestContext = mock(RequestContext.class);
    RequestVariables mockRequestVariables = mock(RequestVariables.class);
    Language mockLanguage = mock(Language.class);

    User mockUser = mock(User.class);
    Role mockRole = mock(Role.class);
    Organization mockOrganization = mock(Organization.class);
    Client mockClient = mock(Client.class);
    Warehouse mockWarehouse = mock(Warehouse.class);

    when(mockContext.getUser()).thenReturn(mockUser);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
    when(mockContext.getCurrentClient()).thenReturn(mockClient);
    when(mockContext.getWarehouse()).thenReturn(mockWarehouse);
    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    when(mockRequestContext.getVariablesSecureApp()).thenReturn(mockRequestVariables);
    when(mockRequestVariables.getCasedSessionAttributes()).thenReturn(new HashMap<>());
    when(mockUser.getADUserRolesList()).thenReturn(new ArrayList<>());

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<RequestContext> mockedRequestContext = mockStatic(RequestContext.class);
         MockedConstruction<LanguageBuilder> ignored1 = mockConstruction(LanguageBuilder.class,
             (mock, context) -> when(mock.toJSON()).thenReturn(new JSONObject()));
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> when(mock.toJsonObject(any(), any())).thenReturn(new JSONObject()))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedRequestContext.when(RequestContext::get).thenReturn(mockRequestContext);

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("user"));
      assertTrue(result.has("currentRole"));
      assertTrue(result.has("currentClient"));
      assertTrue(result.has("currentOrganization"));
      assertTrue(result.has("currentWarehouse"));
      assertTrue(result.has(ROLES));
      assertTrue(result.has(ATTRIBUTES));
      assertTrue(result.has("languages"));
    }
  }
}

package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.CLIENT_ID;
import static com.etendoerp.metadata.MetadataTestConstants.CURRENT_CLIENT;
import static com.etendoerp.metadata.MetadataTestConstants.CURRENT_ORGANIZATION;
import static com.etendoerp.metadata.MetadataTestConstants.CURRENT_ROLE;
import static com.etendoerp.metadata.MetadataTestConstants.CURRENT_WAREHOUSE;
import static com.etendoerp.metadata.MetadataTestConstants.DATABASE_ERROR;
import static com.etendoerp.metadata.MetadataTestConstants.LANGUAGE_CODE;
import static com.etendoerp.metadata.MetadataTestConstants.ORGANIZATIONS;
import static com.etendoerp.metadata.MetadataTestConstants.ORG_ID;
import static com.etendoerp.metadata.MetadataTestConstants.ROLES;
import static com.etendoerp.metadata.MetadataTestConstants.ROLE_ID;
import static com.etendoerp.metadata.MetadataTestConstants.USER_ID;
import static com.etendoerp.metadata.MetadataTestConstants.WAREHOUSES;
import static com.etendoerp.metadata.MetadataTestConstants.WAREHOUSE_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

import com.etendoerp.metadata.utils.Utils;

/**
 * Test class for SessionBuilder.
 * This class tests the functionality of the SessionBuilder, ensuring it can build session
 * information correctly and handle various scenarios including error cases.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class SessionBuilderTest {

  @Mock
  private OBContext obContext;

  @Mock
  private User user;

  @Mock
  private Role role;

  @Mock
  private Organization organization;

  @Mock
  private Client client;

  @Mock
  private Warehouse warehouse;

  @Mock
  private Language language;

  @Mock
  private UserRoles userRole1;

  @Mock
  private UserRoles userRole2;

  @Mock
  private Role role1;

  @Mock
  private Role role2;

  @Mock
  private Client client1;

  @Mock
  private Client client2;

  @Mock
  private RoleOrganization roleOrg1;

  @Mock
  private RoleOrganization roleOrg2;

  @Mock
  private Organization org1;

  @Mock
  private Organization org2;

  @Mock
  private OrgWarehouse orgWarehouse1;

  @Mock
  private OrgWarehouse orgWarehouse2;

  @Mock
  private Warehouse warehouse1;

  @Mock
  private Warehouse warehouse2;

  /**
   * Sets up the necessary mocks and their behaviors before each test.
   */
  @BeforeEach
  void setUp() {
    // Setup basic context mocks
    when(obContext.getUser()).thenReturn(user);
    when(obContext.getRole()).thenReturn(role);
    when(obContext.getCurrentOrganization()).thenReturn(organization);
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getWarehouse()).thenReturn(warehouse);
    when(obContext.getLanguage()).thenReturn(language);

    // Setup basic entity properties
    when(user.getId()).thenReturn(USER_ID);
    when(role.getId()).thenReturn(ROLE_ID);
    when(organization.getId()).thenReturn(ORG_ID);
    when(client.getId()).thenReturn(CLIENT_ID);
    when(warehouse.getId()).thenReturn(WAREHOUSE_ID);
    when(language.getLanguage()).thenReturn(LANGUAGE_CODE);

    // Setup user roles
    when(user.getADUserRolesList()).thenReturn(Arrays.asList(userRole1, userRole2));
    when(userRole1.getRole()).thenReturn(role1);
    when(userRole2.getRole()).thenReturn(role2);

    // Setup roles
    when(role1.getId()).thenReturn("role1-id");
    when(role1.get(eq(Role.PROPERTY_NAME), any(), eq("role1-id"))).thenReturn("Role 1");
    when(role1.getClient()).thenReturn(client1);
    when(role1.getADRoleOrganizationList()).thenReturn(Arrays.asList(roleOrg1));

    when(role2.getId()).thenReturn("role2-id");
    when(role2.get(eq(Role.PROPERTY_NAME), any(), eq("role2-id"))).thenReturn("Role 2");
    when(role2.getClient()).thenReturn(client2);
    when(role2.getADRoleOrganizationList()).thenReturn(Arrays.asList(roleOrg2));

    // Setup clients
    when(client1.getId()).thenReturn("client1-id");
    when(client1.get(eq(Client.PROPERTY_NAME), any(), eq("client1-id"))).thenReturn("Client 1");
    when(client2.getId()).thenReturn("client2-id");
    when(client2.get(eq(Client.PROPERTY_NAME), any(), eq("client2-id"))).thenReturn("Client 2");

    // Setup role organizations
    when(roleOrg1.getOrganization()).thenReturn(org1);
    when(roleOrg2.getOrganization()).thenReturn(org2);

    // Setup organizations
    when(org1.getId()).thenReturn("org1-id");
    when(org1.get(eq(Organization.PROPERTY_NAME), any(), eq("org1-id"))).thenReturn("Organization 1");
    when(org1.getOrganizationWarehouseList()).thenReturn(Arrays.asList(orgWarehouse1));

    when(org2.getId()).thenReturn("org2-id");
    when(org2.get(eq(Organization.PROPERTY_NAME), any(), eq("org2-id"))).thenReturn("Organization 2");
    when(org2.getOrganizationWarehouseList()).thenReturn(Arrays.asList(orgWarehouse2));

    // Setup org warehouses
    when(orgWarehouse1.getWarehouse()).thenReturn(warehouse1);
    when(orgWarehouse2.getWarehouse()).thenReturn(warehouse2);

    // Setup warehouses
    when(warehouse1.getId()).thenReturn("warehouse1-id");
    when(warehouse1.get(eq(Warehouse.PROPERTY_NAME), any(), eq("warehouse1-id"))).thenReturn("Warehouse 1");
    when(warehouse2.getId()).thenReturn("warehouse2-id");
    when(warehouse2.get(eq(Warehouse.PROPERTY_NAME), any(), eq("warehouse2-id"))).thenReturn("Warehouse 2");
  }


  /**
   * Verifies that toJSON builds a complete session JSON using a fully populated OBContext.
   *
   * @throws JSONException if JSON operations fail during assertions or building
   */
  @Test
  void testToJSONSuccessful() throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);

      // Mock Utils.getJsonObject calls
      JSONObject userJson = new JSONObject().put("id", USER_ID);
      JSONObject roleJson = new JSONObject().put("id", ROLE_ID);
      JSONObject clientJson = new JSONObject().put("id", CLIENT_ID);
      JSONObject orgJson = new JSONObject().put("id", ORG_ID);
      JSONObject warehouseJson = new JSONObject().put("id", WAREHOUSE_ID);

      utilsStatic.when(() -> Utils.getJsonObject(user)).thenReturn(userJson);
      utilsStatic.when(() -> Utils.getJsonObject(role)).thenReturn(roleJson);
      utilsStatic.when(() -> Utils.getJsonObject(client)).thenReturn(clientJson);
      utilsStatic.when(() -> Utils.getJsonObject(organization)).thenReturn(orgJson);
      utilsStatic.when(() -> Utils.getJsonObject(warehouse)).thenReturn(warehouseJson);

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("user"));
      assertTrue(result.has(CURRENT_ROLE));
      assertTrue(result.has(CURRENT_CLIENT));
      assertTrue(result.has(CURRENT_ORGANIZATION));
      assertTrue(result.has(CURRENT_WAREHOUSE));
      assertTrue(result.has(ROLES));
      assertTrue(result.has("languages"));

      assertEquals(USER_ID, result.getJSONObject("user").getString("id"));
      assertEquals(ROLE_ID, result.getJSONObject(CURRENT_ROLE).getString("id"));
      assertEquals(CLIENT_ID, result.getJSONObject(CURRENT_CLIENT).getString("id"));
      assertEquals(ORG_ID, result.getJSONObject(CURRENT_ORGANIZATION).getString("id"));
      assertEquals(WAREHOUSE_ID, result.getJSONObject(CURRENT_WAREHOUSE).getString("id"));

      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(2, roles.length());
    }
  }


  /**
   * Verifies that toJSON handles a user without roles and returns an empty roles array.
   *
   * @throws JSONException if JSON operations fail during assertions or building
   */
  @Test
  void testToJSONWithEmptyUserRoles() throws JSONException {
    when(user.getADUserRolesList()).thenReturn(Collections.emptyList());

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);

      // Mock Utils.getJsonObject calls
      utilsStatic.when(() -> Utils.getJsonObject(any())).thenReturn(new JSONObject());

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(0, roles.length());
    }
  }

  /**
   * Test JSON generation with null context values.
   */
  @Test
  void testToJSONWithNullContextValues() {
    when(obContext.getUser()).thenReturn(null);
    when(obContext.getRole()).thenReturn(null);
    when(obContext.getCurrentOrganization()).thenReturn(null);
    when(obContext.getCurrentClient()).thenReturn(null);
    when(obContext.getWarehouse()).thenReturn(null);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      utilsStatic.when(() -> Utils.getJsonObject(null)).thenReturn(new JSONObject());

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("user"));
      assertTrue(result.has(CURRENT_ROLE));
      assertTrue(result.has(CURRENT_CLIENT));
      assertTrue(result.has(CURRENT_ORGANIZATION));
      assertTrue(result.has(CURRENT_WAREHOUSE));
      assertTrue(result.has(ROLES));
      assertTrue(result.has("languages"));
    }
  }

  /**
   * Ensures getRoles handles runtime exceptions (e.g., database failures) and returns an empty roles array instead of failing.
   *
   * @throws JSONException if JSON operations fail during assertions or building
   */
  @Test
  void testGetRolesHandlesExceptionGracefully() throws JSONException {
    when(user.getADUserRolesList()).thenThrow(new RuntimeException(DATABASE_ERROR));

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      utilsStatic.when(() -> Utils.getJsonObject(any())).thenReturn(new JSONObject());

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(0, roles.length());
    }
  }


  /**
   * Ensures organization retrieval per role handles exceptions and results in an empty organizations array for the affected role.
   *
   * @throws JSONException if JSON operations fail during assertions or building
   */
  @Test
  void testGetOrganizationsHandlesExceptionGracefully() throws JSONException {
    when(role1.getADRoleOrganizationList()).thenThrow(new RuntimeException(DATABASE_ERROR));

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      utilsStatic.when(() -> Utils.getJsonObject(any())).thenReturn(new JSONObject());

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(2, roles.length());

      // The role with exception should have empty organizations array
      boolean foundRoleWithEmptyOrgs = false;
      for (int i = 0; i < roles.length(); i++) {
        JSONObject roleObj = roles.getJSONObject(i);
        if (roleObj.has(ORGANIZATIONS) && roleObj.getJSONArray(ORGANIZATIONS).length() == 0) {
          foundRoleWithEmptyOrgs = true;
          break;
        }
      }
      assertTrue(foundRoleWithEmptyOrgs);
    }
  }


  /**
   * Ensures warehouse retrieval per organization handles exceptions and yields an empty warehouses array for the affected organization.
   *
   * @throws JSONException if JSON operations fail during assertions or building
   */
  @Test
  void testGetWarehousesHandlesExceptionGracefully() throws JSONException {
    when(org1.getOrganizationWarehouseList()).thenThrow(new RuntimeException(DATABASE_ERROR));

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      utilsStatic.when(() -> Utils.getJsonObject(any())).thenReturn(new JSONObject());

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      assertNotNull(result);
      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(2, roles.length());

      // Find the role with organization that has error and verify it has empty warehouses
      boolean foundOrgWithEmptyWarehouses = false;
      for (int i = 0; i < roles.length(); i++) {
        JSONObject roleObj = roles.getJSONObject(i);
        JSONArray orgs = roleObj.getJSONArray(ORGANIZATIONS);
        for (int j = 0; j < orgs.length(); j++) {
          JSONObject orgObj = orgs.getJSONObject(j);
          if (orgObj.has(WAREHOUSES) && orgObj.getJSONArray(WAREHOUSES).length() == 0) {
            foundOrgWithEmptyWarehouses = true;
            break;
          }
        }
        if (foundOrgWithEmptyWarehouses) break;
      }
      assertTrue(foundOrgWithEmptyWarehouses);
    }
  }

  /**
   * Test that SessionBuilder constructor works correctly.
   */
  @Test
  void testConstructorSuccessful() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);

      assertDoesNotThrow(SessionBuilder::new);
    }
  }


  /**
   * Validates the structural integrity of the roles JSON, including role, organization, and warehouse fields.
   *
   * @throws JSONException if JSON operations fail during assertions or building
   */
  @Test
  void testRolesStructure() throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      utilsStatic.when(() -> Utils.getJsonObject(any())).thenReturn(new JSONObject());

      SessionBuilder sessionBuilder = new SessionBuilder();
      JSONObject result = sessionBuilder.toJSON();

      JSONArray roles = result.getJSONArray(ROLES);
      assertEquals(2, roles.length());

      for (int i = 0; i < roles.length(); i++) {
        JSONObject roleObj = roles.getJSONObject(i);
        assertTrue(roleObj.has("id"));
        assertTrue(roleObj.has("name"));
        assertTrue(roleObj.has(ORGANIZATIONS));
        assertTrue(roleObj.has("client"));

        JSONArray orgs = roleObj.getJSONArray(ORGANIZATIONS);
        for (int j = 0; j < orgs.length(); j++) {
          JSONObject orgObj = orgs.getJSONObject(j);
          assertTrue(orgObj.has("id"));
          assertTrue(orgObj.has("name"));
          assertTrue(orgObj.has(WAREHOUSES));

          JSONArray warehouses = orgObj.getJSONArray(WAREHOUSES);
          for (int k = 0; k < warehouses.length(); k++) {
            JSONObject warehouseObj = warehouses.getJSONObject(k);
            assertTrue(warehouseObj.has("id"));
            assertTrue(warehouseObj.has("name"));
          }
        }
      }
    }
  }
}

package com.etendoerp.metadata.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.data.AuthData;

/**
 * Unit tests for the AuthData class.
 * <p>
 * This class tests the functionality of the AuthData class, which represents
 * authentication data for a user, including associated role, organization,
 * warehouse, and client information.
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthDataTest {

  @Mock
  private User mockUser;

  @Mock
  private Role mockRole;

  @Mock
  private Organization mockOrganization;

  @Mock
  private Warehouse mockWarehouse;

  @Mock
  private Client mockClient;

  private AuthData authData;

  /**
   * Sets up the test environment before each test execution.
   * Initialize mocks and test data.
   */
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  /**
   * Tests the constructor with all parameters.
   * Verifies that all fields are properly initialized.
   */
  @Test
  public void testConstructorWithAllParameters() {
    // WHEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);

    // THEN
    assertNotNull("AuthData should not be null", authData);
    assertEquals("User should be set correctly", mockUser, authData.getUser());
    assertEquals("Role should be set correctly", mockRole, authData.getRole());
    assertEquals("Organization should be set correctly", mockOrganization, authData.getOrg());
    assertEquals("Warehouse should be set correctly", mockWarehouse, authData.getWarehouse());
    assertEquals("Client should be set correctly", mockClient, authData.getClient());
  }

  /**
   * Tests the constructor with null parameters.
   * Verifies that null values are handled correctly.
   */
  @Test
  public void testConstructorWithNullParameters() {
    // WHEN
    authData = new AuthData(mockUser, null, null, null, null);

    // THEN
    assertNotNull("AuthData should not be null", authData);
    assertEquals("User should be set correctly", mockUser, authData.getUser());
    assertNull("Role should be null", authData.getRole());
    assertNull("Organization should be null", authData.getOrg());
    assertNull("Warehouse should be null", authData.getWarehouse());
    assertNull("Client should be null", authData.getClient());
  }

  /**
   * Tests the getUser method.
   * Verifies that the user is returned correctly.
   */
  @Test
  public void testGetUser() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);

    // WHEN
    User result = authData.getUser();

    // THEN
    assertEquals("Should return the correct user", mockUser, result);
  }

  /**
   * Tests the getRole and setRole methods.
   * Verifies that the role is set and retrieved correctly.
   */
  @Test
  public void testGetAndSetRole() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);
    Role newRole = mock(Role.class);

    // WHEN
    Role initialRole = authData.getRole();
    authData.setRole(newRole);
    Role updatedRole = authData.getRole();

    // THEN
    assertEquals("Initial role should be correct", mockRole, initialRole);
    assertEquals("Updated role should be correct", newRole, updatedRole);
  }

  /**
   * Tests setting role to null.
   * Verifies that null role is handled correctly.
   */
  @Test
  public void testSetRoleToNull() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);

    // WHEN
    authData.setRole(null);

    // THEN
    assertNull("Role should be null after setting to null", authData.getRole());
  }

  /**
   * Tests the getOrg and setOrg methods.
   * Verifies that the organization is set and retrieved correctly.
   */
  @Test
  public void testGetAndSetOrg() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);
    Organization newOrganization = mock(Organization.class);

    // WHEN
    Organization initialOrg = authData.getOrg();
    authData.setOrg(newOrganization);
    Organization updatedOrg = authData.getOrg();

    // THEN
    assertEquals("Initial organization should be correct", mockOrganization, initialOrg);
    assertEquals("Updated organization should be correct", newOrganization, updatedOrg);
  }

  /**
   * Tests setting organization to null.
   * Verifies that null organization is handled correctly.
   */
  @Test
  public void testSetOrgToNull() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);

    // WHEN
    authData.setOrg(null);

    // THEN
    assertNull("Organization should be null after setting to null", authData.getOrg());
  }

  /**
   * Tests the getWarehouse and setWarehouse methods.
   * Verifies that the warehouse is set and retrieved correctly.
   */
  @Test
  public void testGetAndSetWarehouse() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);
    Warehouse newWarehouse = mock(Warehouse.class);

    // WHEN
    Warehouse initialWarehouse = authData.getWarehouse();
    authData.setWarehouse(newWarehouse);
    Warehouse updatedWarehouse = authData.getWarehouse();

    // THEN
    assertEquals("Initial warehouse should be correct", mockWarehouse, initialWarehouse);
    assertEquals("Updated warehouse should be correct", newWarehouse, updatedWarehouse);
  }

  /**
   * Tests setting warehouse to null.
   * Verifies that null warehouse is handled correctly.
   */
  @Test
  public void testSetWarehouseToNull() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);

    // WHEN
    authData.setWarehouse(null);

    // THEN
    assertNull("Warehouse should be null after setting to null", authData.getWarehouse());
  }

  /**
   * Tests the getClient and setClient methods.
   * Verifies that the client is set and retrieved correctly.
   */
  @Test
  public void testGetAndSetClient() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);
    Client newClient = mock(Client.class);

    // WHEN
    Client initialClient = authData.getClient();
    authData.setClient(newClient);
    Client updatedClient = authData.getClient();

    // THEN
    assertEquals("Initial client should be correct", mockClient, initialClient);
    assertEquals("Updated client should be correct", newClient, updatedClient);
  }

  /**
   * Tests setting client to null.
   * Verifies that null client is handled correctly.
   */
  @Test
  public void testSetClientToNull() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);

    // WHEN
    authData.setClient(null);

    // THEN
    assertNull("Client should be null after setting to null", authData.getClient());
  }

  /**
   * Tests multiple field updates.
   * Verifies that multiple fields can be updated independently.
   */
  @Test
  public void testMultipleFieldUpdates() {
    // GIVEN
    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);
    Role newRole = mock(Role.class);
    Organization newOrganization = mock(Organization.class);
    Warehouse newWarehouse = mock(Warehouse.class);
    Client newClient = mock(Client.class);

    // WHEN
    authData.setRole(newRole);
    authData.setOrg(newOrganization);
    authData.setWarehouse(newWarehouse);
    authData.setClient(newClient);

    // THEN
    assertEquals("User should remain unchanged", mockUser, authData.getUser());
    assertEquals("Role should be updated", newRole, authData.getRole());
    assertEquals("Organization should be updated", newOrganization, authData.getOrg());
    assertEquals("Warehouse should be updated", newWarehouse, authData.getWarehouse());
    assertEquals("Client should be updated", newClient, authData.getClient());
  }

  /**
   * Tests that user field is immutable.
   * Verifies that the user field cannot be changed after construction.
   */
  @Test
  public void testUserFieldIsImmutable() {
    // GIVEN

    authData = new AuthData(mockUser, mockRole, mockOrganization, mockWarehouse, mockClient);

    // WHEN
    User userBeforeAnyOperation = authData.getUser();

    // Perform various operations that shouldn't affect the user
    authData.setRole(null);
    authData.setOrg(null);
    authData.setWarehouse(null);
    authData.setClient(null);

    User userAfterOperations = authData.getUser();

    // THEN
    assertEquals("User should be the same before operations", mockUser, userBeforeAnyOperation);
    assertEquals("User should remain unchanged after operations", mockUser, userAfterOperations);
    assertEquals("User should be consistent", userBeforeAnyOperation, userAfterOperations);
  }
}

package com.etendoerp.metadata.data;

import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

/**
 * Represents authentication data for a user, including associated role, organization,
 * warehouse, and client information.
 *
 * This class is used to encapsulate the details of an authenticated user and their
 * associated entities within the system.
 *
 * @author Etendo Software
 */
public class AuthData {

  /**
   * The authenticated user.
   */
  private final User user;

  /**
   * The role associated with the authenticated user.
   */
  private Role role;

  /**
   * The organization associated with the authenticated user.
   */
  private Organization org;

  /**
   * The warehouse associated with the authenticated user.
   */
  private Warehouse warehouse;

  /**
   * The client associated with the authenticated user.
   */
  private Client client;

  /**
   * Constructs an AuthData object with the specified user, role, organization,
   * warehouse, and client.
   *
   * @param user      The authenticated user.
   * @param role      The role associated with the user.
   * @param org       The organization associated with the user.
   * @param warehouse The warehouse associated with the user.
   * @param client    The client associated with the user.
   */
  public AuthData(User user, Role role, Organization org, Warehouse warehouse, Client client) {
    this.user = user;
    this.role = role;
    this.org = org;
    this.warehouse = warehouse;
    this.client = client;
  }

  /**
   * Retrieves the authenticated user.
   *
   * @return The authenticated user.
   */
  public User getUser() {
    return user;
  }

  /**
   * Retrieves the role associated with the authenticated user.
   *
   * @return The role associated with the user.
   */
  public Role getRole() {
    return role;
  }

  /**
   * Sets the role associated with the authenticated user.
   *
   * @param role The role to associate with the user.
   */
  public void setRole(Role role) {
    this.role = role;
  }

  /**
   * Retrieves the organization associated with the authenticated user.
   *
   * @return The organization associated with the user.
   */
  public Organization getOrg() {
    return org;
  }

  /**
   * Sets the organization associated with the authenticated user.
   *
   * @param org The organization to associate with the user.
   */
  public void setOrg(Organization org) {
    this.org = org;
  }

  /**
   * Retrieves the warehouse associated with the authenticated user.
   *
   * @return The warehouse associated with the user.
   */
  public Warehouse getWarehouse() {
    return warehouse;
  }

  /**
   * Sets the warehouse associated with the authenticated user.
   *
   * @param warehouse The warehouse to associate with the user.
   */
  public void setWarehouse(Warehouse warehouse) {
    this.warehouse = warehouse;
  }

  /**
   * Retrieves the client associated with the authenticated user.
   *
   * @return The client associated with the user.
   */
  public Client getClient() {
    return client;
  }

  /**
   * Sets the client associated with the authenticated user.
   *
   * @param client The client to associate with the user.
   */
  public void setClient(Client client) {
    this.client = client;
  }
}


package com.etendoerp.metadata.data;

import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

/**
 * @author luuchorocha
 */
public class AuthData {
  public final User user;
  public Role role;
  public Organization org;
  public Warehouse warehouse;

  public AuthData(User user, Role role, Organization org, Warehouse warehouse) {
    this.user = user;
    this.role = role;
    this.org = org;
    this.warehouse = warehouse;
  }
}

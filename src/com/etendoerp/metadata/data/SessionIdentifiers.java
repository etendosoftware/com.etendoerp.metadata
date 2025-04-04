package com.etendoerp.metadata.data;

import com.etendoerp.metadata.Utils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import java.util.Optional;

public class SessionIdentifiers {
    public final String userId;
    public final String languageId;
    public final String isRTL;
    public final String roleId;
    public final String clientId;
    public final String orgId;
    public final String warehouseId;

    public SessionIdentifiers(String userId, String languageId, String isRTL, String roleId, String clientId,
                              String orgId, String warehouseId) {
        this.userId = userId;
        this.languageId = languageId;
        this.isRTL = isRTL;
        this.roleId = roleId;
        this.clientId = clientId;
        this.orgId = orgId;
        this.warehouseId = warehouseId;
    }

    public SessionIdentifiers(OBContext context) {
        this(resolveUserId(context),
             resolveLanguageId(context),
             Utils.getValue(context.isRTL()),
             resolveRoleId(context),
             resolveClientId(context),
             resolveOrgId(context),
             resolveWarehouseId(context));
    }

    private static String resolveUserId(OBContext context) {
        return Optional.ofNullable(context.getUser()).map(User::getId).orElse("");
    }

    private static String resolveLanguageId(OBContext context) {
        return Optional.ofNullable(context.getLanguage())
                       .or(() -> Optional.ofNullable(context.getUser()).map(User::getDefaultLanguage))
                       .map(Language::getLanguage).orElse("");
    }

    private static String resolveRoleId(OBContext context) {
        return Optional.ofNullable(context.getRole())
                       .or(() -> Optional.ofNullable(context.getUser()).map(User::getDefaultRole)).map(Role::getId)
                       .orElse("");
    }

    private static String resolveClientId(OBContext context) {
        return Optional.ofNullable(context.getCurrentClient())
                       .or(() -> Optional.ofNullable(context.getUser()).map(User::getClient))
                       .or(() -> Optional.ofNullable(context.getUser()).map(User::getDefaultClient)).map(Client::getId)
                       .orElse("");
    }

    private static String resolveOrgId(OBContext context) {
        return Optional.ofNullable(context.getCurrentOrganization())
                       .or(() -> Optional.ofNullable(context.getUser()).map(User::getOrganization))
                       .or(() -> Optional.ofNullable(context.getUser()).map(User::getDefaultOrganization))
                       .map(Organization::getId).orElse("");
    }

    private static String resolveWarehouseId(OBContext context) {
        return Optional.ofNullable(context.getWarehouse())
                       .or(() -> Optional.ofNullable(context.getUser()).map(User::getDefaultWarehouse))
                       .map(Warehouse::getId).orElse("");
    }
}

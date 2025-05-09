package com.etendoerp.metadata.auth;

import static org.openbravo.base.secureApp.LoginUtils.fillSessionArguments;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.KernelServlet;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.metadata.data.RequestVariables;
import com.etendoerp.metadata.exceptions.InternalServerException;

/**
 * @author luuchorocha
 */
public class SessionManager {
    private final static Logger logger = LogManager.getLogger(SessionManager.class);

    public static RequestVariables initializeSession() {
        OBContext context = OBContext.getOBContext();

        if (context == null) {
            throw new InternalServerException("OBContext not initialized for this thread");
        }

        RequestContext requestContext = RequestContext.get();
        String userId = context.getUser().getId();
        Language language = context.getLanguage();
        String languageCode = language != null ? language.getLanguage() : "";
        String isRTL = context.isRTL() ? "Y" : "N";
        Client client = context.getCurrentClient();
        String clientId = client != null ? client.getId() : "";
        Role role = context.getRole();
        String roleId = role != null ? role.getId() : "";
        Organization organization = context.getCurrentOrganization();
        String orgId = organization != null ? organization.getId() : "";
        Warehouse warehouse = context.getWarehouse();
        String warehouseId = warehouse != null ? warehouse.getId() : "";

        try {
            fillSessionArguments(new DalConnectionProvider(), requestContext.getVariablesSecureApp(), userId,
                languageCode, isRTL, roleId, clientId, orgId, warehouseId);
            bypassCSRF(requestContext.getRequest(), userId);
        } catch (ServletException e) {
            throw new InternalServerException(e.getMessage());
        }

        return (RequestVariables) requestContext.getVariablesSecureApp();
    }

    public static void readNumberFormat(VariablesSecureApp vars) {
        if (KernelServlet.getGlobalParameters() != null) {
            LoginUtils.readNumberFormat(vars, KernelServlet.getGlobalParameters().getFormatPath());
        }
    }

    private static void bypassCSRF(HttpServletRequest request, String userId) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.setAttribute("#CSRF_TOKEN", userId);
            session.setAttribute("#CSRF_Token", userId);
        }
    }
}

package com.etendoerp.metadata.auth;

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

    public static RequestVariables initializeSession(HttpServletRequest request) {
        try {
            OBContext context = OBContext.getOBContext();

            if (context == null) {
                throw new InternalServerException("OBContext not initialized for this thread");
            }

            RequestVariables vars = new RequestVariables(request);
            DalConnectionProvider conn = new DalConnectionProvider();

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

            boolean sessionFilled = LoginUtils.fillSessionArguments(conn, vars, userId, languageCode, isRTL, roleId,
                clientId, orgId, warehouseId);

            if (sessionFilled) {
                bypassCSRF(request, userId);
                readNumberFormat(vars);
                setRequestContext(request, vars);

                return vars;
            } else {
                throw new InternalServerException("Could not initialize a session");
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException(e.getMessage());
        }
    }

    private static void setRequestContext(HttpServletRequest request, RequestVariables vars) {
        RequestContext requestContext = RequestContext.get();
        requestContext.setRequest(request);
        requestContext.setVariableSecureApp(vars);
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

package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.InternalServerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.service.db.DalConnectionProvider;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SessionUtils {
    private static final Logger logger = LogManager.getLogger(SessionUtils.class);

    public static void initializeSession(HttpServletRequest request, HttpServletResponse response) {
        try {
            OBContext context = OBContext.getOBContext();
            VariablesSecureApp vars = new VariablesSecureApp(request, false);

            String userId = context.getUser().getId();
            String language = context.getLanguage().getLanguage();
            String isRTL = context.isRTL() ? "Y" : "N";
            String roleId = context.getRole().getId();
            String clientId = context.getCurrentClient().getId();
            String orgId = context.getCurrentOrganization().getId();
            String warehouseId = context.getWarehouse() != null ? context.getWarehouse().getId() : "";
            ConnectionProvider conn = new DalConnectionProvider(false);

            boolean sessionFilled = LoginUtils.fillSessionArguments(conn,
                                                                    vars,
                                                                    userId,
                                                                    language,
                                                                    isRTL,
                                                                    roleId,
                                                                    clientId,
                                                                    orgId,
                                                                    warehouseId);

            if (sessionFilled) {
                logger.debug("Session filled {}", request.getSession(false));
                request.getSession(false).setAttribute("#CSRF_TOKEN", OBContext.getOBContext().getUser().getId());
            } else {
                throw new InternalServerException("Could not initialize a session");
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException(e.getMessage());
        }
    }
}

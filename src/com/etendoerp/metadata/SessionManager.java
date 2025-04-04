package com.etendoerp.metadata;

import com.etendoerp.metadata.data.RequestVariables;
import com.etendoerp.metadata.data.SessionIdentifiers;
import com.etendoerp.metadata.exceptions.InternalServerException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.openbravo.base.ConfigParameters;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.db.DalConnectionProvider;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class SessionManager {

    private final static Logger logger = LogManager.getLogger(SessionManager.class);

    public static RequestVariables initializeSession(HttpServletRequest request, boolean createSession) {
        try {
            OBContext context = OBContext.getOBContext();
            RequestVariables vars = createSession ? new RequestVariables(request, true) : new RequestVariables(request);
            SessionIdentifiers ids = new SessionIdentifiers(context);

            boolean sessionFilled = LoginUtils.fillSessionArguments(new DalConnectionProvider(),
                                                                    vars,
                                                                    ids.userId,
                                                                    ids.languageId,
                                                                    ids.isRTL,
                                                                    ids.roleId,
                                                                    ids.clientId,
                                                                    ids.orgId,
                                                                    ids.warehouseId);

            if (sessionFilled) {
                readNumberFormat(request, vars);
                bypassCSRF(request, ids.userId);
                setRequestContext(request, vars);

                return vars;
            } else {
                throw new InternalServerException("Could not initialize a session");
            }

        } catch (Exception e) {
            logger.error("Error initializing session: " + e.getMessage(), e);
            throw new InternalServerException(e.getMessage());
        }
    }

    /**
     * Ensures the OBContext is set in a thread-safe way.
     * This method should be used only when operating outside of the standard KernelServlet flow,
     * where the OBContext is not automatically set.
     */
    private static OBContext getOBContextThreadSafe(HttpServletRequest request) {
        OBContext context = OBContext.getOBContext();
        if (context == null) {
            synchronized (SessionManager.class) {
                context = OBContext.getOBContext();
                if (context == null) {
                    OBContext.setOBContext(request);
                    context = OBContext.getOBContext();
                }
            }
        }
        return context;
    }

    private static void setRequestContext(HttpServletRequest request, RequestVariables vars) {
        RequestContext requestContext = RequestContext.get();
        requestContext.setRequest(request);
        requestContext.setVariableSecureApp(vars);
    }

    private static void bypassCSRF(HttpServletRequest request, String userId) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("#CSRF_TOKEN", userId);
            session.setAttribute("#CSRF_Token", userId);
        }
    }

    private static void readNumberFormat(HttpServletRequest request, VariablesSecureApp vars) {
        String formatPath = getFormatPath(request);
        if (formatPath != null && !formatPath.isBlank()) {
            LoginUtils.readNumberFormat(vars, formatPath);
        }
    }

    private static String getFormatPath(HttpServletRequest request) {
        return ConfigParameters.retrieveFrom(request.getServletContext()).getFormatPath();
    }
}

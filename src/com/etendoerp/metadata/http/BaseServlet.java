package com.etendoerp.metadata.http;

import static org.openbravo.base.secureApp.LoginUtils.fillSessionArguments;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.KernelServlet;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.metadata.data.RequestVariables;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.utils.Utils;

/**
 * @author luuchorocha
 */
public class BaseServlet extends HttpSecureAppServlet {
    private static AuthenticationManager authenticationManager = null;

    private void bypassCSRF(HttpServletRequest request, String userId) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.setAttribute("#CSRF_TOKEN", userId);
            session.setAttribute("#CSRF_Token", userId);
        }
    }

    public void initializeSession() {
        OBContext context = OBContext.getOBContext();

        if (context == null) {
            throw new InternalServerException("OBContext not initialized for this thread");
        }

        initializeSession(context);
    }

    private void initializeSession(OBContext context) {
        RequestContext requestContext = RequestContext.get();
        HttpServletRequest request = requestContext.getRequest();
        RequestVariables vars = (RequestVariables) requestContext.getVariablesSecureApp();
        ConnectionProvider conn = new DalConnectionProvider();
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
            fillSessionArguments(conn, vars, userId, languageCode, isRTL, roleId, clientId, orgId, warehouseId);
            readNumberFormat(vars, KernelServlet.getGlobalParameters().getFormatPath());
            readProperties(vars);
            bypassCSRF(request, userId);
        } catch (ServletException e) {
            throw new InternalServerException(e.getMessage());
        }
    }

    @Override
    public void init(ServletConfig config) {
        super.init(config);

        if (KernelServlet.getGlobalParameters() == null) {
            WeldUtils.getInstanceFromStaticBeanManager(KernelServlet.class).init(config);
        }

        authenticationManager = AuthenticationManager.getAuthenticationManager(this);
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        service(req, res, true, true);
    }

    public void service(HttpServletRequest req, HttpServletResponse res, boolean callSuper,
        boolean initializeSession) throws IOException {
        try {
            HttpServletRequestWrapper request = HttpServletRequestWrapper.wrap(req);
            AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, res);
            RequestVariables vars = new RequestVariables(request);
            RequestContext requestContext = RequestContext.get();
            requestContext.setRequest(request);
            requestContext.setVariableSecureApp(vars);
            requestContext.setResponse(res);
            authenticationManager.authenticate(request, res);

            if (initializeSession) {
                initializeSession();
            }

            if (callSuper) {
                super.serviceInitialized(request, res);
            }
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);

            if (!res.isCommitted()) {
                res.setStatus(Utils.getHttpStatusFor(e));
                res.getWriter().write(Utils.convertToJson(e).toString());
            }
        }
    }
}

/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Utils.setContext;
import static org.openbravo.base.secureApp.LoginUtils.fillSessionArguments;
import static org.openbravo.base.secureApp.LoginUtils.log4j;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
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

    public static void initializeSession() {
        OBContext context = OBContext.getOBContext();

        if (context == null) {
            throw new InternalServerException("OBContext not initialized for this thread");
        }

        initializeSession(context);
    }

    private static void initializeSession(OBContext context) {
        RequestContext requestContext = RequestContext.get();
        VariablesSecureApp vars = requestContext.getVariablesSecureApp();
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
        service(HttpServletRequestWrapper.wrap(req), res, true, true);
    }

    public void service(HttpServletRequest req, HttpServletResponse res, boolean callSuper,
        boolean initializeSession) throws IOException {
        try {
            req = HttpServletRequestWrapper.wrap(req);
            doOptions(req, res);

            if (req.getMethod().equalsIgnoreCase("options")) {
                return;
            }

            RequestVariables vars = new RequestVariables(req);
            RequestContext requestContext = RequestContext.get();
            requestContext.setRequest(req);
            requestContext.setVariableSecureApp(vars);
            requestContext.setResponse(res);
            setContext(req);
            String userId = authenticationManager.authenticate(req, res);

            setupLocaleFromContext();

            if (initializeSession) {
                initializeSession();
                readNumberFormat(vars, KernelServlet.getGlobalParameters().getFormatPath());
                readProperties(vars);
                bypassCSRF(req, userId);
            }

            if (callSuper) {
                super.serviceInitialized(req, res);
            }
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);

            if (!res.isCommitted()) {
                res.setStatus(Utils.getHttpStatusFor(e));
                res.getWriter().write(Utils.convertToJson(e).toString());
            }
        }
    }

    @Override
    public final void doOptions(HttpServletRequest req, HttpServletResponse res) {
        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(req, res);
    }

    private void setupLocaleFromContext() {
        try {
            OBContext context = OBContext.getOBContext();

            if (context != null && context.getLanguage() != null) {
                Locale contextLocale = Locale.forLanguageTag(context.getLanguage().getLanguage());
                if (contextLocale != null) {
                    Locale.setDefault(contextLocale);
                } else {
                    Locale.setDefault(Locale.US);
                }
            } else {
                Locale.setDefault(Locale.US);
            }

        } catch (Exception e) {
            Locale.setDefault(Locale.US);
        }
    }

}




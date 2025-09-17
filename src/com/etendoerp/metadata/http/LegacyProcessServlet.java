package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Constants.HEAD_CLOSE_TAG;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.RequestVariables;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;

/**
 * Minimal servlet for handling legacy processes running in iframes.
 * This servlet avoids all session handling to prevent NullPointerException issues
 * with the custom session system.
 */
public class LegacyProcessServlet extends HttpSecureAppServlet {
    private static final Logger log4j = LogManager.getLogger(LegacyProcessServlet.class);

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            String path = req.getPathInfo();
            if (isLegacyRequest(path)) {
                handleLegacyRequest(req, res, path, res);
            } else {
                super.service(req, res);
            }
        } catch (Exception e) {
            log4j.error("Error processing legacy request: " + e.getMessage(), e);
            throw e;
        }
    }

    private void handleLegacyRequest(HttpServletRequest req, HttpServletResponse res, String path,
                                     HttpServletResponse response) throws ServletException, IOException {
        var responseWrapper = new HttpServletResponseLegacyWrapper(res);
        HttpServletRequestWrapper request = new HttpServletRequestWrapper(req);

        handleTokenConsistency(req, request);

        handleRecordIdentifier(request);

        handleRequestContext(res, request);

        // Preflight: for legacy HTML, check that the expected WAD servlet class exists
        maybeValidateLegacyClass(request.getPathInfo());

        request.getRequestDispatcher(request.getPathInfo()).include(request, responseWrapper);

        String output = responseWrapper.getCapturedOutputAsString();

        output = getInjectedContent(path, output);

        response.setContentType(responseWrapper.getContentType());
        response.setStatus(responseWrapper.getStatus());
        response.getWriter().write(output);
        response.getWriter().flush();
    }

    /**
     * Check if request is for legacy HTML files.
     */
    private boolean isLegacyRequest(String path) {
        return path != null && path.toLowerCase().endsWith(".html");
    }

    /**
     * Validate that the expected WAD servlet class exists.
     */
    private void maybeValidateLegacyClass(String pathInfo) {
        if (!isLegacyRequest(pathInfo)) {
            return;
        }
        try {
            String expected = deriveLegacyClass(pathInfo);
            if (expected != null) {
                try {
                    Class.forName(expected);
                } catch (ClassNotFoundException e) {
                    throw new OBException("Legacy WAD servlet not found: " + expected
                            + ". Please run './gradlew wad compile.complete' and redeploy.");
                }
            }
        } catch (Exception ignore) {
            // Do not block request if we cannot derive class name
        }
    }

    /**
     * Derive expected WAD servlet class name from path.
     */
    private String deriveLegacyClass(String pathInfo) {
        String tail = pathInfo;
        if (tail.startsWith("/")) {
            tail = tail.substring(1);
        }
        String[] parts = tail.split("/");
        if (parts.length < 2) return null;
        String window = parts[0];
        String page = parts[1];
        if (page.endsWith(".html")) page = page.substring(0, page.length() - 5);
        String base = page.contains("_") ? page.substring(0, page.indexOf('_')) : page;
        return "org.openbravo.erpWindows." + window + "." + base;
    }

    private static void handleTokenConsistency(HttpServletRequest req, HttpServletRequestWrapper request) {
        String token = req.getParameter("token");
        if (token != null) {
            req.getSession().setAttribute("#JWT_TOKEN", token);
        } else {
            Object sessionToken = req.getSession().getAttribute("#JWT_TOKEN");
            if (sessionToken != null) {
                try {
                    DecodedJWT decodedJWT = SecureWebServicesUtils.decodeToken(sessionToken.toString());
                    request.setSessionId(decodedJWT.getClaims().get("jti").asString());
                } catch (Exception e) {
                    throw new OBException("Error decoding token", e);
                }
            }
        }
    }

    private static void handleRecordIdentifier(HttpServletRequestWrapper request) {
        String inpKeyId = request.getParameter("inpKey");
        String inpWindowId = request.getParameter("inpwindowId");
        String inpKeyColumnId = request.getParameter("inpkeyColumnId");
        if (StringUtils.isNoneEmpty(inpKeyId, inpWindowId, inpKeyColumnId)) {
            request.getSession().setAttribute(inpWindowId + "|" + inpKeyColumnId.toUpperCase(), inpKeyId);
        }
    }

    private static void handleRequestContext(HttpServletResponse res, HttpServletRequestWrapper request) {
        RequestVariables vars = new RequestVariables(request);
        RequestContext requestContext = RequestContext.get();
        requestContext.setRequest(request);
        requestContext.setVariableSecureApp(vars);
        requestContext.setResponse(res);
        OBContext.setOBContext(request);
    }

    /**
     * Inject basic iframe communication scripts.
     */
    private String getInjectedContent(String path, String output) {
        try {
            // Just inject basic postMessage script
            String postMessageScript = "<script>" +
                    "const sendMessage = (action) => {" +
                    "  if (window.parent) {" +
                    "    window.parent.postMessage({" +
                    "      type: \"fromForm\"," +
                    "      action: action" +
                    "    }, \"*\");" +
                    "  }" +
                    "};" +
                    "</script>";

            return output.replace(HEAD_CLOSE_TAG, postMessageScript + HEAD_CLOSE_TAG);

        } catch (Exception e) {
            log4j.warn("Error injecting content: " + e.getMessage());
            return output;
        }
    }
}
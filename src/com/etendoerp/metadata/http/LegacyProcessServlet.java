package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Constants.HEAD_CLOSE_TAG;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;

/**
 * Legacy process servlet that extends BaseServlet to get proper session handling.
 */
public class LegacyProcessServlet extends BaseServlet {
    private static final Logger log4j = LogManager.getLogger(LegacyProcessServlet.class);

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        String path = req.getPathInfo();

        if (isLegacyRequest(path)) {
            // Use BaseServlet to properly initialize session and context
            super.service(req, res, false, true); // don't call super servlet logic, but do initialize session

            // Then handle legacy-specific processing
            handleLegacyRequest(req, res, path);
        } else {
            super.service(req, res, true, true);
        }
    }

    private void handleLegacyRequest(HttpServletRequest req, HttpServletResponse res, String path)
            throws ServletException, IOException {

        var responseWrapper = new HttpServletResponseLegacyWrapper(res);

        // Create wrapper that modifies path for legacy processing
        HttpServletRequestWrapper request = new HttpServletRequestWrapper(req) {
            @Override
            public String getPathInfo() {
                return path;
            }
        };

        // Validate WAD servlet class exists
        maybeValidateLegacyClass(path);

        // Forward to the actual legacy servlet
        request.getRequestDispatcher(path).include(request, responseWrapper);

        String output = responseWrapper.getCapturedOutputAsString();

        // Inject basic iframe communication scripts
        output = getInjectedContent(path, output);

        res.setContentType(responseWrapper.getContentType());
        res.setStatus(responseWrapper.getStatus());
        res.getWriter().write(output);
        res.getWriter().flush();
    }

    private boolean isLegacyRequest(String path) {
        return path != null && path.toLowerCase().endsWith(".html");
    }

    private void maybeValidateLegacyClass(String pathInfo) {
        if (pathInfo == null || !isLegacyRequest(pathInfo)) {
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

    private String getInjectedContent(String path, String output) {
        try {
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
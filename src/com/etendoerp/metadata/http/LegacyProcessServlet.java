package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Constants.HEAD_CLOSE_TAG;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

/**
 * Legacy servlet that uses existing HttpServletRequestWrapper infrastructure
 */
public class LegacyProcessServlet extends HttpSecureAppServlet {
    private static final Logger log4j = LogManager.getLogger(LegacyProcessServlet.class);

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        String path = req.getPathInfo();

        if (isLegacyRequest(path)) {
            processLegacyRequest(req, res, path);
        } else if (isLegacyFollowupRequest(req)) {
            // Handle follow-up requests from legacy processes (like form submissions)
            processLegacyFollowupRequest(req, res);
        } else {
            super.service(req, res);
        }
    }

    private boolean isLegacyFollowupRequest(HttpServletRequest req) {
        // Detect follow-up requests from legacy processes
        String command = req.getParameter("Command");
        return command != null && command.startsWith("BUTTON");
    }

    private void processLegacyFollowupRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // Get token from session (should have been stored during initial request)
        String token = (String) req.getSession(false).getAttribute("LEGACY_TOKEN");
        String servletDir = (String) req.getSession(false).getAttribute("LEGACY_SERVLET_DIR");

        if (token == null) {
            log4j.error("No token found in session for legacy follow-up request");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No authentication token for follow-up request");
            return;
        }

        log4j.debug("Processing legacy follow-up request with token from session");

        // Create wrapper similar to main legacy request
        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(req) {
            @Override
            public String getHeader(String name) {
                if ("Authorization".equalsIgnoreCase(name)) {
                    return "Bearer " + token;
                }
                return super.getHeader(name);
            }

            @Override
            public javax.servlet.http.HttpSession getSession() {
                return req.getSession(true);
            }

            @Override
            public javax.servlet.http.HttpSession getSession(boolean create) {
                return req.getSession(create);
            }
        };

        try {
            String targetPath = null;
            String pathInfo = req.getPathInfo();

            // Case 1: Direct .html request like /Header_Edition.html
            if (pathInfo != null && pathInfo.endsWith(".html")) {
                if (servletDir != null) {
                    targetPath = servletDir + pathInfo;
                } else {
                    // Fallback: try to determine from referer
                    targetPath = extractTargetPathFromReferer(req.getHeader("Referer"));
                    if (targetPath == null) {
                        targetPath = pathInfo; // Last resort
                    }
                }
            }

            // Case 2: Command request - extract from referer
            if (targetPath == null) {
                targetPath = extractTargetPathFromReferer(req.getHeader("Referer"));
            }

            if (targetPath != null) {
                log4j.debug("Forwarding follow-up request to: {}", targetPath);
                wrappedRequest.getRequestDispatcher(targetPath).forward(wrappedRequest, res);
            } else {
                log4j.error("Could not determine target path for follow-up request. PathInfo: {}, ServletDir: {}",
                        pathInfo, servletDir);
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not determine target servlet");
            }

        } catch (Exception e) {
            log4j.error("Error processing legacy follow-up request: {}", e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing follow-up request");
        }
    }

    private String extractTargetPathFromReferer(String referer) {
        if (referer == null) return null;

        try {
            // Extract path from referer URL
            // Example: http://localhost:8080/etendo/meta/legacy/SalesOrder/Header_Edition.html?...
            // Should return: /SalesOrder/Header_Edition.html

            int legacyIndex = referer.indexOf("/meta/legacy/");
            if (legacyIndex != -1) {
                String afterLegacy = referer.substring(legacyIndex + "/meta/legacy".length());
                int queryIndex = afterLegacy.indexOf('?');
                if (queryIndex != -1) {
                    afterLegacy = afterLegacy.substring(0, queryIndex);
                }
                return afterLegacy;
            }

            // Fallback: if no /legacy/ in referer, try to extract from /meta/
            int metaIndex = referer.indexOf("/meta/");
            if (metaIndex != -1) {
                String afterMeta = referer.substring(metaIndex + "/meta".length());
                int queryIndex = afterMeta.indexOf('?');
                if (queryIndex != -1) {
                    afterMeta = afterMeta.substring(0, queryIndex);
                }
                // If it's a direct .html file, assume it's in the same servlet directory
                if (afterMeta.endsWith(".html") && !afterMeta.contains("/")) {
                    // Try to get the servlet directory from session or use a default
                    return "/SalesOrder" + afterMeta; // You might need to make this more dynamic
                }
                return afterMeta;
            }

            return null;

        } catch (Exception e) {
            log4j.warn("Error extracting target path from referer: {}", referer, e);
            return null;
        }
    }

    private void processLegacyRequest(HttpServletRequest req, HttpServletResponse res, String path)
            throws ServletException, IOException {

        // Debug: Check if token is present in original request
        String token = req.getParameter("token");
        log4j.debug("Original request has token parameter: {}", token != null ? "yes" : "no");

        // Ensure session exists - this is critical for VariablesSecureApp
        req.getSession(true);

        // Store token and servlet path in session for follow-up requests
        if (token != null) {
            req.getSession().setAttribute("LEGACY_TOKEN", token);
        }

        // Store the servlet directory for follow-up requests
        if (path != null && path.contains("/")) {
            String servletDir = path.substring(0, path.lastIndexOf("/"));
            req.getSession().setAttribute("LEGACY_SERVLET_DIR", servletDir);
            log4j.debug("Stored servlet directory: {}", servletDir);
        }

        // Create a wrapper that preserves the token parameter AND adds Authorization header
        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(req) {
            @Override
            public String getPathInfo() {
                return path;
            }

            @Override
            public String getParameter(String name) {
                // Ensure token parameter is always available
                if ("token".equals(name) && token != null) {
                    return token;
                }
                return super.getParameter(name);
            }

            @Override
            public String getHeader(String name) {
                // Ensure Authorization header is always available
                if ("Authorization".equalsIgnoreCase(name) && token != null) {
                    return "Bearer " + token;
                }
                return super.getHeader(name);
            }

            @Override
            public java.util.Enumeration<String> getHeaders(String name) {
                if ("Authorization".equalsIgnoreCase(name) && token != null) {
                    return java.util.Collections.enumeration(
                            java.util.Arrays.asList("Bearer " + token)
                    );
                }
                return super.getHeaders(name);
            }

            @Override
            public javax.servlet.http.HttpSession getSession() {
                return req.getSession(true);
            }

            @Override
            public javax.servlet.http.HttpSession getSession(boolean create) {
                return req.getSession(create);
            }
        };

        var responseWrapper = new HttpServletResponseLegacyWrapper(res);

        try {
            log4j.debug("Processing legacy request: {}", path);
            log4j.debug("Wrapped request has token parameter: {}",
                    wrappedRequest.getParameter("token") != null ? "yes" : "no");
            log4j.debug("Wrapped request has Authorization header: {}",
                    wrappedRequest.getHeader("Authorization") != null ? "yes" : "no");
            log4j.debug("Wrapped request has session: {}",
                    wrappedRequest.getSession(false) != null ? "yes" : "no");

            wrappedRequest.getRequestDispatcher(path).include(wrappedRequest, responseWrapper);

            String output = responseWrapper.getCapturedOutputAsString();
            output = getInjectedContent(path, output);

            res.setContentType(responseWrapper.getContentType());
            res.setStatus(responseWrapper.getStatus());
            res.getWriter().write(output);
            res.getWriter().flush();

        } catch (Exception e) {
            log4j.error("Error processing legacy request {}: {}", path, e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing legacy request: " + e.getMessage());
        }
    }

    private boolean isLegacyRequest(String path) {
        return path != null && path.toLowerCase().endsWith(".html");
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
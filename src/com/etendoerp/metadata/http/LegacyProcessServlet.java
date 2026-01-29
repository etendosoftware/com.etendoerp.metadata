package com.etendoerp.metadata.http;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.RequestVariables;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.HttpBaseUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.security.SessionLogin;
import org.openbravo.model.ad.access.User;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.etendoerp.metadata.utils.Constants.FORM_CLOSE_TAG;
import static com.etendoerp.metadata.utils.Constants.FRAMESET_CLOSE_TAG;
import static com.etendoerp.metadata.utils.Constants.HEAD_CLOSE_TAG;
import static com.etendoerp.metadata.utils.Constants.PUBLIC_JS_PATH;
import static com.etendoerp.metadata.utils.LegacyPaths.ABOUT_MODAL;
import static com.etendoerp.metadata.utils.LegacyPaths.MANUAL_PROCESS;

/**
 * Legacy servlet that uses existing HttpServletRequestWrapper infrastructure
 * to handle legacy HTML pages and their follow-up requests.
 * It provides compatibility for WAD-generated windows and processes by wrapping
 * requests and responses to inject necessary scripts and handle authentication.
 */
public class LegacyProcessServlet extends HttpSecureAppServlet {
    private static final Logger log = LogManager.getLogger();
    private static final String JWT_TOKEN = "#JWT_TOKEN";
    private static final String HTML_EXTENSION = ".html";
    private static final String JS_EXTENSION = ".js";
    private static final String TOKEN_PARAM = "token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String META_LEGACY_PATH = "/meta/legacy";
    private static final String BASE_PATH = "/etendo";
    private static final String WEB_PATH = "/web/";
    private static final String SRC_REPLACE_STRING = "src=\"";

    private static final String RECEIVE_AND_POST_MESSAGE_SCRIPT =
            "<script>window.addEventListener(\"message\", (event) => {" +
                    "if (event.data?.type === \"fromForm\" && window.parent) {" +
                    "window.parent.postMessage({ type: \"fromIframe\", action: event.data.action }, \"*\");" +
                    "}});</script>";

    private static final String POST_MESSAGE_SCRIPT =
            "<script>const sendMessage = (action) => {" +
                    "if (window.parent) {" +
                    "window.parent.postMessage({ type: \"fromForm\", action: action, }, \"*\");" +
                    "}}</script>";
    public static final String SET_COOKIE = "Set-Cookie";
    public static final String ERROR_SENDING_ERROR_RESPONSE = "Error sending error response: {}";

    /**
     * Sets the JSESSIONID cookie in the response if it hasn't been set yet.
     * It ensures that the cookie is configured with proper Path, Domain, HttpOnly, and SameSite attributes.
     *
     * @param res the HttpServletResponse where the cookie will be added
     * @param sessionId the session identifier to be stored in the cookie
     */
    private void setSessionCookie(HttpServletResponse res, String sessionId) {
        if (res.getHeaders(SET_COOKIE) != null &&
                res.getHeaders(SET_COOKIE).stream().anyMatch(h -> h.contains("JSESSIONID"))) {
            // Session cookie already set
            log.info("JSESSIONID cookie already set in response headers.");
            return;
        }
        String host = OBPropertiesProvider.getInstance()
                .getOpenbravoProperties()
                .getProperty("CLASSIC_URL");

        if (StringUtils.isEmpty(host)) {
            host = "localhost";
        }

        if (host.contains("://")) host = host.split("://")[1];
        if (host.contains("/")) host = host.split("/")[0];
        if (host.contains(":")) host = host.split(":")[0];

        Cookie cookie = new Cookie("JSESSIONID", sessionId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);

        boolean isProduction = !host.equalsIgnoreCase("localhost") && !host.startsWith("127.");
        cookie.setSecure(isProduction);

        res.addHeader(SET_COOKIE,
                String.format("JSESSIONID=%s; Path=/; Domain=%s; HttpOnly; %s; SameSite=None",
                        sessionId,
                        host,
                        isProduction ? "Secure" : ""
                )
        );
    }

    /**
     * Handles the entry point for all requests to this servlet.
     * Routes requests based on whether they are legacy HTML requests, JavaScript requests,
     * follow-up actions, or redirect requests.
     *
     * @param req the HttpServletRequest
     * @param res the HttpServletResponse
     * @throws IOException if an input or output error is detected when the servlet handles the request
     * @throws ServletException if the request could not be handled
     */
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        try {
            String path = req.getPathInfo();
            HttpSession session = req.getSession(true);

            setSessionCookie(res, session.getId());

            if (isLegacyRequest(path)) {
                processLegacyRequest(req, res, path);
            } else if (isJavaScriptRequest(path)) {
                processJavaScriptRequest(req, res, path);
            } else if (isLegacyFollowupRequest(req)) {
                processLegacyFollowupRequest(req, res);
            } else if (isRedirectRequest(path)) {
                processRedirectRequest(req, res, path);
            } else {
                super.service(req, res);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServletException(e);
        }
    }


    /**
     * Processes a redirect request, validating a JWT token and establishing the session context.
     * It handles user authentication based on the token and redirects to the specified location.
     *
     * @param req the HttpServletRequest
     * @param res the HttpServletResponse
     * @param path the request path
     */
    private void processRedirectRequest(HttpServletRequest req, HttpServletResponse res,
                                        String path) {
        String token = req.getParameter(TOKEN_PARAM);
        if (token != null) {
            try {
                authenticateWithToken(req, token);
            } catch (Exception e) {
                log.error("Invalid token provided for redirect request", e);
                sendErrorResponse(res, HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid token provided for redirect request");
                return;
            }
        }

        String location = req.getParameter("location");
        if (!isValidLocation(location)) {
            sendErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid or missing location parameter");
            return;
        }

        try {
            String html = getHtmlRedirect(location, false);
            sendHtmlResponse(res, html);
        } catch (IOException e) {
            log.error("Error processing redirect request {}: {}", path, e.getMessage(), e);
            sendErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error processing redirect request: " + e.getMessage());
        }
    }

    /**
     * Authenticates the user based on a JWT token and sets up the session context.
     *
     * @param req the HttpServletRequest
     * @param token the JWT token
     * @throws OBException if token decoding or session creation fails
     */
    private void authenticateWithToken(HttpServletRequest req, String token) {
        try {
            DecodedJWT decodedJWT = SecureWebServicesUtils.decodeToken(token);
            String userId = decodedJWT.getClaim("user").asString();
            String roleId = decodedJWT.getClaim("role").asString();
            String clientId = decodedJWT.getClaim("client").asString();
            String orgId = decodedJWT.getClaim("organization").asString();
            String warehouseId = decodedJWT.getClaim("warehouse") != null ?
                    decodedJWT.getClaim("warehouse").asString() : null;

            OBContext.setOBContext(userId, roleId, clientId, orgId, null, warehouseId);
            OBContext.setOBContextInSession(req, OBContext.getOBContext());

            User user = OBDal.getInstance().get(User.class, userId);
            if (user == null) {
                throw new OBException("User not found: " + userId);
            }
            String sessionId = createDBSession(req, user.getUsername(), userId);

            VariablesSecureApp vars = new VariablesSecureApp(req);
            vars.setSessionValue("#AD_User_ID", userId);
            vars.setSessionValue("#AD_SESSION_ID", sessionId);
            vars.setSessionValue("#LogginIn", "Y");
            req.getSession(true).setAttribute("#Authenticated_user", userId);

            try {
                OBContext.setAdminMode(true);
                SecureWebServicesUtils.fillSessionVariables(req);
            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            throw new OBException("Error authenticating with token", e);
        }
    }

    /**
     * Validates if the provided redirect location is safe and not null.
     *
     * @param location the location string to validate
     * @return true if the location is valid, false otherwise
     */
    private boolean isValidLocation(String location) {
        if (location == null) {
            log.error("No location parameter provided for redirect request");
            return false;
        }
        if (location.contains("://") || location.startsWith("//")) {
            log.error("Invalid location parameter provided for redirect request: {}", location);
            return false;
        }
        return true;
    }

    /**
     * Sends an error response to the client.
     *
     * @param res the HttpServletResponse
     * @param statusCode the HTTP status code
     * @param message the error message
     */
    private void sendErrorResponse(HttpServletResponse res, int statusCode, String message) {
        try {
            res.sendError(statusCode, message);
        } catch (IOException e) {
            log.error(ERROR_SENDING_ERROR_RESPONSE, e.getMessage(), e);
        }
    }

    /**
     * Sends a successful HTML response to the client.
     *
     * @param res the HttpServletResponse
     * @param html the HTML content to send
     * @throws IOException if an error occurs while writing the response
     */
    private void sendHtmlResponse(HttpServletResponse res, String html) throws IOException {
        res.setContentType("text/html; charset=UTF-8");
        res.setStatus(HttpServletResponse.SC_OK);
        res.getWriter().write(html);
        res.getWriter().flush();
    }


    private boolean isRedirectRequest(String path) {
        return path != null && path.toLowerCase().endsWith("/redirect");
    }

    private boolean isLegacyRequest(String path) {
        return path != null && path.toLowerCase().endsWith(HTML_EXTENSION);
    }

    private boolean isJavaScriptRequest(String path) {
        return path != null && path.toLowerCase().endsWith(JS_EXTENSION);
    }

    private boolean isLegacyFollowupRequest(HttpServletRequest req) {
        String command = req.getParameter("Command");
        return command != null && command.startsWith("BUTTON");
    }

    /**
     * Processes a request for a legacy HTML page.
     * Wraps the request and response to capture output and inject necessary compatibility scripts.
     *
     * @param req the HttpServletRequest
     * @param res the HttpServletResponse
     * @param path the path to the legacy resource
     * @throws IOException if an error occurs during processing
     */
    private void processLegacyRequest(HttpServletRequest req, HttpServletResponse res, String path) throws IOException {

        try {
            String token = req.getParameter(TOKEN_PARAM);
            HttpServletRequestWrapper wrappedRequest = buildWrappedRequest(req, path);
            HttpServletResponseLegacyWrapper responseWrapper = new HttpServletResponseLegacyWrapper(res);

            prepareSessionAttributes(req, path);
            if (token != null) {
                authenticateWithToken(wrappedRequest, token);
            }
            preprocessRequest(req, wrappedRequest);

            wrappedRequest.getRequestDispatcher(path).include(wrappedRequest, responseWrapper);

            handleResponse(res, responseWrapper, path);

        } catch (Exception e) {
            log.error("Error processing legacy request {}: {}", path, e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error processing legacy request: " + e.getMessage());
        }
    }
    
    /**
     * Processes requests for JavaScript files, ensuring they are from authorized paths
     * and applying any necessary transformations.
     *
     * @param req the HttpServletRequest
     * @param res the HttpServletResponse
     * @param path the path to the JavaScript file
     * @throws IOException if an error occurs during processing
     */
    private void processJavaScriptRequest(HttpServletRequest req, HttpServletResponse res, String path)
            throws IOException {
        String validatedPath = java.nio.file.Paths.get(path).normalize().toString();
        if (!validatedPath.startsWith(PUBLIC_JS_PATH)) {
            log.warn("Attempted access to unauthorized path: {}", validatedPath);
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access to this resource is forbidden.");
            return;
        }
        try (InputStream inputStream = req.getServletContext().getResourceAsStream(validatedPath)) {
            if (inputStream == null) {
                log.warn("JavaScript file not found: {}", validatedPath);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "JavaScript file not found: " + validatedPath);
                return;
            }

            String jsContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String processedContent = transformJavaScriptContent(jsContent);

            res.setContentType("application/javascript; charset=UTF-8");
            res.setCharacterEncoding("UTF-8");
            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write(processedContent);
            res.getWriter().flush();

        } catch (Exception e) {
            log.error("Error processing JavaScript request {}: {}", validatedPath, e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error processing JavaScript request: " + e.getMessage());
        }
    }

    /**
     * Transform JavaScript content based on the file path.
     * Applies path-specific transformations similar to getInjectedContent.
     *
     * @param content the original JavaScript content
     * @return the transformed JavaScript content
     */
    private String transformJavaScriptContent(String content) {
        return content;
    }

    /**
     * Prepares session attributes such as tokens and directory paths for legacy requests.
     *
     * @param req the HttpServletRequest
     * @param path the resource path
     */
    private void prepareSessionAttributes(HttpServletRequest req, String path) {
        String token = req.getParameter(TOKEN_PARAM);

        HttpSession session = req.getSession(true);
        if (token != null) {
            session.setAttribute("LEGACY_TOKEN", token);
        }

        if (path != null && path.contains("/")) {
            String dir = path.substring(0, path.lastIndexOf("/"));
            session.setAttribute("LEGACY_SERVLET_DIR", dir);
        }
    }

    private void preprocessRequest(HttpServletRequest req, HttpServletRequestWrapper wrappedRequest) {
        handleTokenConsistency(req, wrappedRequest);
        handleRecordIdentifier(wrappedRequest);
        handleRequestContext(null, wrappedRequest);
        maybeValidateLegacyClass(wrappedRequest.getPathInfo());
    }

    private HttpServletRequestWrapper buildWrappedRequest(HttpServletRequest req, String path) {
        String token = req.getParameter(TOKEN_PARAM);

        return new HttpServletRequestWrapper(req) {

            @Override
            public String getPathInfo() {
                return path;
            }

            @Override
            public String getParameter(String name) {
                if (TOKEN_PARAM.equals(name) && token != null) {
                    return token;
                }
                return super.getParameter(name);
            }

            @Override
            public String getHeader(String name) {
                if (AUTHORIZATION_HEADER.equalsIgnoreCase(name) && token != null) {
                    return BEARER_PREFIX + token;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (AUTHORIZATION_HEADER.equalsIgnoreCase(name) && token != null) {
                    return Collections.enumeration(Collections.singletonList(BEARER_PREFIX + token));
                }
                return super.getHeaders(name);
            }

            @Override
            public HttpSession getSession() {
                return req.getSession(true);
            }

            @Override
            public HttpSession getSession(boolean create) {
                return req.getSession(create);
            }
        };
    }

    /**
     * Handles the captured response by potentially injecting content or performing redirects.
     *
     * @param res the HttpServletResponse
     * @param wrapper the response wrapper containing captured output
     * @param path the resource path
     * @throws IOException if an error occurs while writing the response
     */
    private void handleResponse(HttpServletResponse res,
                                HttpServletResponseLegacyWrapper wrapper,
                                String path) throws IOException {

        String output = wrapper.getCapturedOutputAsString();

        if (wrapper.isRedirected()) {
            writeRedirect(res, wrapper);
            return;
        }

        output = getInjectedContent(path, output);

        writeFinalResponse(res, wrapper, path, output);
    }

    private void writeRedirect(HttpServletResponse res,
                               HttpServletResponseLegacyWrapper wrapper) throws IOException {

        String location = wrapper.getRedirectLocation();
        String html = getHtmlRedirect(location, true);

        res.setContentType(wrapper.getContentType());
        res.setStatus(wrapper.getStatus());
        res.getWriter().write(html);
        res.getWriter().flush();
    }

    private void writeFinalResponse(HttpServletResponse res,
                                    HttpServletResponseLegacyWrapper wrapper,
                                    String path,
                                    String output) throws IOException {

        if (ABOUT_MODAL.equals(path) || MANUAL_PROCESS.equals(path)) {
            res.setContentType("text/html; charset=UTF-8");
            res.setCharacterEncoding("UTF-8");
        } else {
            res.setContentType(wrapper.getContentType());
        }

        res.setStatus(wrapper.getStatus());
        res.getWriter().write(output);
        res.getWriter().flush();
    }

    /**
     * Creates a new session in the database (AD_Session table).
     * This method is a duplication of the logic found in
     * {@link org.openbravo.authentication.AuthenticationManager#createDBSession(HttpServletRequest, String, String, String)}.
     *
     * The duplication is necessary because the original method in AuthenticationManager is 'protected'
     * and cannot be accessed directly from this context, even though we need to manually establish
     * a session during the JWT-based redirect login flow.
     *
     * @param req The current HTTP request.
     * @param strUser The username for the session.
     * @param strUserAuth The user ID (AD_User_ID).
     * @return The ID of the created session.
     */
    protected final String createDBSession(HttpServletRequest req, String strUser, String strUserAuth) {
        return createDBSession(req, strUser, strUserAuth, "S");
    }

    /**
     * Internal implementation for creating a database session.
     * Duplicated from {@link org.openbravo.authentication.AuthenticationManager}.
     *
     * @param req The current HTTP request.
     * @param strUser The username for the session.
     * @param strUserAuth The user ID (AD_User_ID).
     * @param successSessionType The status type for the session (e.g., "S" for success).
     * @return The ID of the created session.
     */
    protected final String createDBSession(HttpServletRequest req, String strUser, String strUserAuth,
                                           String successSessionType) {
        try {
            if (strUserAuth == null && StringUtils.isEmpty(strUser)) {
                return null;
            }

            if (req == null) {
                throw new OBException("Request object is null, cannot create DB session");
            }

            String usr = strUserAuth == null ? "0" : strUserAuth;

            final SessionLogin sl = new SessionLogin(req, "0", "0", usr);

            if (strUserAuth == null) {
                sl.setStatus("F");
            } else {
                sl.setStatus(successSessionType);
            }

            sl.setUserName(strUser);
            if (req != null) {
                sl.setServerUrl(HttpBaseUtils.getLocalAddress(req));
            }
            sl.save();
            return sl.getSessionID();
        } catch (Exception e) {
            log.error("Error creating DB session", e);
            return null;
        }
    }



    /**
     * Processes follow-up requests triggered by buttons or actions within legacy pages.
     * Restores authentication context and forwards the request to the appropriate legacy servlet.
     *
     * @param req the HttpServletRequest
     * @param res the HttpServletResponse
     * @throws IOException if an error occurs during processing
     */
    private void processLegacyFollowupRequest(HttpServletRequest req, HttpServletResponse res)
            throws IOException {

        String token = (String) (req.getSession(false) != null ? req.getSession(false).getAttribute("LEGACY_TOKEN") : null);
        String servletDir = (String) req.getSession(false).getAttribute("LEGACY_SERVLET_DIR");

        if (token == null) {
            log.error("No token found in session for legacy follow-up request");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No authentication token for follow-up request");
            return;
        }

        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(req) {
            @Override
            public String getHeader(String name) {
                if (AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
                    return BEARER_PREFIX + token;
                }
                return super.getHeader(name);
            }

            @Override
            public HttpSession getSession() {
                return req.getSession(true);
            }

            @Override
            public HttpSession getSession(boolean create) {
                return req.getSession(create);
            }
        };

        try {
            handleTokenConsistency(req, wrappedRequest);
            handleRecordIdentifier(wrappedRequest);
            handleRequestContext(res, wrappedRequest);

            String targetPath = extractTargetPath(req, servletDir);

            if (targetPath != null) {
                log.debug("Forwarding follow-up request to: {}", targetPath);
                wrappedRequest.getRequestDispatcher(targetPath).forward(wrappedRequest, res);
            } else {
                log.error("Could not determine target path for follow-up request. PathInfo: {}, ServletDir: {}",
                        req.getPathInfo(), servletDir);
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not determine target servlet");
            }

        } catch (Exception e) {
            log.error("Error processing legacy follow-up request: {}", e.getMessage(), e);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing follow-up request");
        }
    }

    private String extractTargetPath(HttpServletRequest req, String servletDir) {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.endsWith(HTML_EXTENSION)) {
            if (servletDir != null) {
                return servletDir + pathInfo;
            } else {
                String refererTargetPath = extractTargetPathFromReferer(req.getHeader("Referer"));
                return (refererTargetPath != null) ? refererTargetPath : pathInfo;
            }
        }
        return extractTargetPathFromReferer(req.getHeader("Referer"));
    }

    private String extractTargetPathFromReferer(String referer) {
        if (referer == null) return null;
        try {
            int legacyIndex = referer.indexOf(META_LEGACY_PATH + "/");
            if (legacyIndex != -1) {
                String afterLegacy = referer.substring(legacyIndex + META_LEGACY_PATH.length());
                int queryIndex = afterLegacy.indexOf('?');
                if (queryIndex != -1) {
                    afterLegacy = afterLegacy.substring(0, queryIndex);
                }
                return afterLegacy;
            }
            int metaIndex = referer.indexOf("/meta/");
            if (metaIndex != -1) {
                String afterMeta = referer.substring(metaIndex + "/meta".length());
                int queryIndex = afterMeta.indexOf('?');
                if (queryIndex != -1) {
                    afterMeta = afterMeta.substring(0, queryIndex);
                }
                if (afterMeta.endsWith(HTML_EXTENSION) && !afterMeta.contains("/")) {
                    return "/SalesOrder" + afterMeta;
                }
                return afterMeta;
            }
            return null;
        } catch (Exception e) {
            log.warn("Error extracting target path from referer: {}", referer, e);
            return null;
        }
    }

    private void handleTokenConsistency(HttpServletRequest req, HttpServletRequestWrapper wrappedRequest) {
        String token = req.getParameter(TOKEN_PARAM);
        if (token != null) {
            req.getSession().setAttribute(JWT_TOKEN, token);
        } else if (wrappedRequest != null) {
            Object sessionToken = req.getSession().getAttribute(JWT_TOKEN);
            if (sessionToken != null) {
                try {
                    DecodedJWT decodedJWT = SecureWebServicesUtils.decodeToken(sessionToken.toString());
                    Claim jtiClaim = decodedJWT.getClaims().get("jti");
                    if (jtiClaim != null) {
                        wrappedRequest.setSessionId(jtiClaim.asString());
                    } else {
                        log.warn("JWT token in session does not contain 'jti' claim.");
                    }
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

    private void maybeValidateLegacyClass(String pathInfo) {
        if (pathInfo == null || !isLegacyRequest(pathInfo)) {
            return;
        }
        try {
            String expected = deriveLegacyClass(pathInfo);
            if (expected != null) {
                validateLegacyClassExists(expected);
            }
        } catch (Exception e) {
            log.debug("Legacy class validation failed: {}", e.getMessage());
        }
    }

    private void validateLegacyClassExists(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new OBException("Legacy WAD servlet not found: " + className
                    + ". Please run './gradlew wad compile.complete' and redeploy.");
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
        if (page.endsWith(HTML_EXTENSION)) {
            page = page.substring(0, page.length() - HTML_EXTENSION.length());
        }
        String base = page.contains("_") ? page.substring(0, page.indexOf('_')) : page;
        return "org.openbravo.erpWindows." + window + "." + base;
    }

    /**
     * Generates an HTML redirect page that automatically redirects the browser to a modified URL.
     * The method inserts the "/meta/forward" path into the redirect location and creates
     * a simple HTML page with a meta refresh tag.
     *
     * @param redirectLocation the original redirect URL to be modified
     * @return an HTML string containing the redirect page with meta refresh
     */
    private static String getHtmlRedirect(String redirectLocation, boolean replacePath) {
        String forwardedUrl;
        if (replacePath) {
            forwardedUrl = redirectLocation.replace(BASE_PATH, BASE_PATH + META_LEGACY_PATH);
        } else {
            forwardedUrl = redirectLocation;
        }
        return String.format(
                "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "    <head>\n" +
                        "        <meta charset='UTF-8'/>\n" +
                        "        <meta http-equiv=\"refresh\" content=\"0; url='%s'\"/>\n" +
                        "    </head>\n" +
                        "</html>",
                forwardedUrl
        );
    }

    /**
     * Injects compatibility scripts and adjusts paths in the HTML response content.
     * This ensures that legacy pages can communicate with the modern frontend
     * and that all resource links work correctly.
     *
     * @param path the resource path
     * @param responseString the original HTML content
     * @return the HTML content with injected scripts and adjusted paths
     */
    private String getInjectedContent(String path, String responseString) {
        HttpServletRequest req = RequestContext.get().getRequest();
        String contextPath = req.getContextPath();

        log.info("===== Context path from request: {}", contextPath);

        responseString = responseString
                .replace(META_LEGACY_PATH, META_LEGACY_PATH + path)
                // Custom JS files that need custom export
                .replace(SRC_REPLACE_STRING + "../utility/DynamicJS.js", SRC_REPLACE_STRING + contextPath + "/utility/DynamicJS.js")
                .replace(SRC_REPLACE_STRING + "../org.openbravo.client.kernel/",  SRC_REPLACE_STRING + contextPath + "/org.openbravo.client.kernel/")
                .replace(SRC_REPLACE_STRING + "../web/",  SRC_REPLACE_STRING+ contextPath + WEB_PATH)
                .replace("href=\"../web/", "href=\"" + contextPath + WEB_PATH);

        if (responseString.contains(FRAMESET_CLOSE_TAG)) {
            return responseString.replace(HEAD_CLOSE_TAG, RECEIVE_AND_POST_MESSAGE_SCRIPT.concat(HEAD_CLOSE_TAG));
        }

        if (responseString.contains(FORM_CLOSE_TAG)) {
            String resWithNewScript = responseString.replace(FORM_CLOSE_TAG, FORM_CLOSE_TAG.concat(POST_MESSAGE_SCRIPT));
            resWithNewScript = resWithNewScript.replace(SRC_REPLACE_STRING + "../web/", SRC_REPLACE_STRING + contextPath + WEB_PATH);
            resWithNewScript = resWithNewScript.replace("href=\"../web/", "href=\"" + contextPath + WEB_PATH);

            return injectCodeAfterFunctionCall(
                    injectCodeAfterFunctionCall(resWithNewScript, "submitThisPage\\(([^)]+)\\);", "sendMessage('processOrder');", true),
                    "closeThisPage();",
                    "sendMessage('closeModal');",
                    false
            );
        }

        return responseString;
    }

    private String injectCodeAfterFunctionCall(String originalRes, String originalFunctionCall, String newFunctionCall, boolean isRegex) {
        Pattern pattern = isRegex ? Pattern.compile(originalFunctionCall) : Pattern.compile(Pattern.quote(originalFunctionCall));
        Matcher matcher = pattern.matcher(originalRes);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String originalSubmitButtonAction = matcher.group(0);
            String modifiedSubmitButtonAction = originalSubmitButtonAction + newFunctionCall;
            matcher.appendReplacement(result, Matcher.quoteReplacement(modifiedSubmitButtonAction));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

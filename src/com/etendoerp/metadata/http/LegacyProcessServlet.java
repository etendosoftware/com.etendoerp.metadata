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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.http;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.RequestVariables;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
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
    private static final String ACTION_JSON_SEPARATOR = "',action:";
    private static final String HTML_UTF8_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String UTF8_CHARSET = "UTF-8";
    private static final String TOKEN_PARAM = "token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String META_LEGACY_PATH = "/meta/legacy";
    private static final String WEB_PATH = "/web/";
    private static final String SRC_REPLACE_STRING = "src=\"";

    private static final String RECEIVE_AND_POST_MESSAGE_SCRIPT = "<script>window.addEventListener(\"message\", (event) => {"
            +
            "if (event.data?.type === \"" + LegacyMessageProtocol.MESSAGE_TYPE + "\" && window.parent) {" +
            "window.parent.postMessage({ type: \"fromIframe\", action: event.data.action }, \"*\");" +
            "}});</script>";

    /**
     * Script injected into legacy form pages to expose {@code window.sendMessage}
     * and to guarantee that the parent always receives a final notification even
     * when the page is unloaded mid-flight (redirect, navigation, browser-tab
     * change). Tracks whether a "final" message ({@code showProcessMessage} or
     * {@code closeModal}) was emitted; if not, on {@code pagehide}/{@code beforeunload}
     * it sends an {@code iframeUnloaded} action so the new UI can show the
     * fallback warning instead of leaving the user without feedback.
     */
    private static final String POST_MESSAGE_SCRIPT = "<script>(function(){" +
            "var sent=false;" +
            "window.sendMessage=function(action){" +
            "if(!window.parent)return;" +
            "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
            + "action},'*');" +
            "if(action==='" + LegacyMessageProtocol.ACTION_SHOW_PROCESS_MESSAGE + "'||action==='"
            + LegacyMessageProtocol.ACTION_CLOSE_MODAL + "'){sent=true;window.__etendoMessageSent=true;}" +
            "};" +
            "function notifyUnload(){" +
            "if(sent||window.__etendoMessageSent)return;" +
            "if(!window.parent)return;" +
            "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
            + "'" + LegacyMessageProtocol.ACTION_IFRAME_UNLOADED + "'},'*');" +
            "sent=true;window.__etendoMessageSent=true;" +
            "}" +
            "window.addEventListener('pagehide',notifyUnload);" +
            "window.addEventListener('beforeunload',notifyUnload);" +
            "})();</script>";

    /**
     * Marker used to detect Openbravo classic "popup message" response pages
     * (error/success/info/warning dialogs emitted by action button servlets).
     * These pages share the template: id="messageBoxIDTitle" + id="messageBoxIDMessage"
     * + id="paramTipo" class="MessageBox{TYPE}".
     */
    private static final String POPUP_MESSAGE_MARKER = "id=\"messageBoxIDMessage\"";

    /**
     * Marker matching the CSS class the Openbravo error popup template writes.
     * When present in the captured body the DAL session is almost certainly
     * dirty (the WAD servlet caught its own OBException without rolling back),
     * so we must rollback before the filter chain tries to commit.
     */
    private static final String ERROR_POPUP_CLASS_MARKER = "MessageBoxERROR";

    /**
     * Script injected into popup-message pages. Extracts the title/text/type from
     * the DOM and forwards them to the parent window so the new UI can render the
     * dialog with its own styles, then closes the iframe modal.
     *
     * <p>Delays {@code closeModal} by 150 ms so the parent processes
     * {@code showProcessMessage} and captures the payload before the iframe is
     * unmounted (otherwise the React state update is discarded).
     */
    private static final String SHOW_PROCESS_MESSAGE_SCRIPT = "<script>(function(){" +
            "function forward(){" +
            "var t=document.getElementById('messageBoxIDTitle');" +
            "var m=document.getElementById('messageBoxIDMessage');" +
            "var p=document.getElementById('paramTipo');" +
            "if(!t||!m||!window.parent)return;" +
            "var cls=(p&&p.className)||'';" +
            "var type='info';" +
            "if(cls.indexOf('ERROR')>=0)type='error';" +
            "else if(cls.indexOf('SUCCESS')>=0)type='success';" +
            "else if(cls.indexOf('WARNING')>=0)type='warning';" +
            "var payload={type:type,title:(t.textContent||'').trim(),text:(m.textContent||'').trim()};" +
            "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
            + "'" + LegacyMessageProtocol.ACTION_SHOW_PROCESS_MESSAGE + "',payload:payload},'*');" +
            "window.__etendoMessageSent=true;" +
            "setTimeout(function(){" +
            "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
            + "'" + LegacyMessageProtocol.ACTION_CLOSE_MODAL + "'},'*');" +
            "},150);" +
            "}" +
            "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',forward);" +
            "else forward();" +
            "})();</script>";

    /**
     * Request parameter name and canonical value prefix used by Openbravo
     * action-button servlets to signal "execute the action". Matching variants
     * like {@code PROCESSDEFAULT} requires a {@code startsWith} check rather than
     * equality.
     */
    private static final String COMMAND_PARAM = "Command";
    private static final String PROCESS_COMMAND_PREFIX = "PROCESS";

    /**
     * Patterns used to extract the popup title, message and message-type class
     * from the captured legacy HTML. The popup template is stable across
     * Openbravo versions and modules.
     */
    private static final Pattern POPUP_TITLE_PATTERN = Pattern.compile(
            "id=\"messageBoxIDTitle\"[^>]*>([^<]*)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern POPUP_MESSAGE_PATTERN = Pattern.compile(
            "id=\"messageBoxIDMessage\"[^>]*>([^<]*)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern POPUP_TYPE_PATTERN = Pattern.compile(
            "id=\"paramTipo\"[^>]*class=\"MessageBox([A-Z]+)\"", Pattern.CASE_INSENSITIVE);

    /**
     * Minimal self-contained HTML served for {@code Command=PROCESS} popups.
     * Bypasses the legacy HTML pipeline entirely: a tiny (~500 B) response
     * cannot trigger the chunked-encoding race that breaks the full popup path.
     * The payload placeholder is a JSON object with {@code type}/{@code title}/{@code text}.
     */
    private static final String MINIMAL_FORWARDER_HTML =
            "<!DOCTYPE html>\n<html><head><meta charset=\"" + UTF8_CHARSET + "\"><script>" +
                    "(function(){if(!window.parent)return;" +
                    "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
                    + "'" + LegacyMessageProtocol.ACTION_SHOW_PROCESS_MESSAGE + "',payload:%s},'*');" +
                    "window.__etendoMessageSent=true;" +
                    "})();</script></head><body></body></html>";

    /**
     * Self-contained HTML served when the legacy iframe pipeline cannot complete the
     * request (e.g. unhandled exception, missing follow-up token, unresolvable target
     * path). Posts a {@code requestFailed} action to the parent window so the new UI
     * renders its own user-facing copy ({@code process.requestFailed.*}) instead of
     * leaving the iframe on Tomcat's error page. The HTTP status is 200 so the body
     * is loaded and the script runs.
     */
    private static final String REQUEST_FAILED_FORWARDER_HTML =
            "<!DOCTYPE html>\n<html><head><meta charset=\"" + UTF8_CHARSET + "\"><script>" +
                    "(function(){if(!window.parent)return;" +
                    "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
                    + "'" + LegacyMessageProtocol.ACTION_REQUEST_FAILED + "'},'*');" +
                    "window.__etendoMessageSent=true;" +
                    "})();</script></head><body></body></html>";

    public static final String SET_COOKIE = "Set-Cookie";
    public static final String ERROR_SENDING_ERROR_RESPONSE = "Error sending error response: {}";

    /**
     * Sets the JSESSIONID cookie in the response if it hasn't been set yet.
     * It ensures that the cookie is configured with proper Path, Domain, HttpOnly,
     * and SameSite attributes.
     *
     * @param res       the HttpServletResponse where the cookie will be added
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

        if (host.contains("://"))
            host = host.split("://")[1];
        if (host.contains("/"))
            host = host.split("/")[0];
        if (host.contains(":"))
            host = host.split(":")[0];

        Cookie cookie = new Cookie("JSESSIONID", sessionId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);

        boolean isProduction = !host.equalsIgnoreCase("localhost") && !host.startsWith("127.");
        cookie.setSecure(isProduction);

        res.addHeader(SET_COOKIE,
                String.format("JSESSIONID=%s; Path=/; Domain=%s; HttpOnly; %s; SameSite=None",
                        sessionId,
                        host,
                        isProduction ? "Secure" : ""));
    }

    /**
     * Handles the entry point for all requests to this servlet.
     * Routes requests based on whether they are legacy HTML requests, JavaScript
     * requests,
     * follow-up actions, or redirect requests.
     *
     * @param req the HttpServletRequest
     * @param res the HttpServletResponse
     * @throws IOException      if an input or output error is detected when the
     *                          servlet handles the request
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
     * Processes a redirect request, validating a JWT token and establishing the
     * session context.
     * It handles user authentication based on the token and redirects to the
     * specified location.
     *
     * @param req  the HttpServletRequest
     * @param res  the HttpServletResponse
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
            String contextPath = req.getContextPath();
            String html = getHtmlRedirect(location, false, contextPath);
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
     * @param req   the HttpServletRequest
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
            String warehouseId = decodedJWT.getClaim("warehouse") != null ? decodedJWT.getClaim("warehouse").asString()
                    : null;

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
            vars.setSessionValue("#AD_Role_ID", roleId);
            vars.setSessionValue("#AD_Client_ID", clientId);
            vars.setSessionValue("#AD_Org_ID", orgId);
            req.getSession(true).setAttribute("#Authenticated_user", userId);

            try {
                OBContext.setAdminMode(true);
                SecureWebServicesUtils.fillSessionVariables(req);
                readProperties(vars);
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
     * @param res        the HttpServletResponse
     * @param statusCode the HTTP status code
     * @param message    the error message
     */
    private void sendErrorResponse(HttpServletResponse res, int statusCode, String message) {
        try {
            res.sendError(statusCode, message);
        } catch (IOException e) {
            log.error(ERROR_SENDING_ERROR_RESPONSE, e.getMessage(), e);
        }
    }

    /**
     * Writes a minimal HTML response that postMessages a {@code requestFailed} action
     * to the parent window. Used when the legacy iframe pipeline aborts and we need
     * the new UI to surface a friendly error overlay instead of letting the iframe
     * load Tomcat's default error page.
     *
     * @param res   the HttpServletResponse to write the forwarder HTML into
     * @param cause the original exception (logged for diagnostics; not exposed to the client)
     */
    private void writeRequestFailedForwarder(HttpServletResponse res, Exception cause) {
        log.error("Forwarding request failure to iframe parent", cause);
        try {
            res.setContentType(HTML_UTF8_CONTENT_TYPE);
            res.setCharacterEncoding(UTF8_CHARSET);
            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write(REQUEST_FAILED_FORWARDER_HTML);
            res.getWriter().flush();
        } catch (IOException e) {
            log.error("Failed to write requestFailed forwarder: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends a successful HTML response to the client.
     *
     * @param res  the HttpServletResponse
     * @param html the HTML content to send
     * @throws IOException if an error occurs while writing the response
     */
    private void sendHtmlResponse(HttpServletResponse res, String html) throws IOException {
        res.setContentType(HTML_UTF8_CONTENT_TYPE);
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
     * Wraps the request and response to capture output and inject necessary
     * compatibility scripts.
     *
     * @param req  the HttpServletRequest
     * @param res  the HttpServletResponse
     * @param path the path to the legacy resource
     * @throws IOException if an error occurs during processing
     */
    private void processLegacyRequest(HttpServletRequest req, HttpServletResponse res, String path) throws IOException {

        try {
            String token = req.getParameter(TOKEN_PARAM);
            String contextPath = req.getContextPath();
            HttpServletRequestWrapper wrappedRequest = buildWrappedRequest(req, path);
            HttpServletResponseLegacyWrapper responseWrapper = new HttpServletResponseLegacyWrapper(res);

            prepareSessionAttributes(req, path);
            if (token != null) {
                authenticateWithToken(wrappedRequest, token);
            }
            preprocessRequest(req, wrappedRequest);

            wrappedRequest.getRequestDispatcher(path).include(wrappedRequest, responseWrapper);

            rollbackDalSessionIfErrorPopup(responseWrapper);

            handleResponse(res, responseWrapper, path, contextPath);

        } catch (Exception e) {
            log.error("Error processing legacy request {}: {}", path, e.getMessage(), e);
            writeRequestFailedForwarder(res, e);
        }
    }

    /**
     * Processes requests for JavaScript files, ensuring they are from authorized
     * paths
     * and applying any necessary transformations.
     *
     * @param req  the HttpServletRequest
     * @param res  the HttpServletResponse
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

            res.setContentType("application/javascript; charset=" + UTF8_CHARSET);
            res.setCharacterEncoding(UTF8_CHARSET);
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
     * Prepares session attributes such as tokens and directory paths for legacy
     * requests.
     *
     * @param req  the HttpServletRequest
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
     * Handles the captured response by potentially injecting content or performing
     * redirects.
     *
     * @param res     the HttpServletResponse
     * @param wrapper the response wrapper containing captured output
     * @param path    the resource path
     * @throws IOException if an error occurs while writing the response
     */
    private void handleResponse(HttpServletResponse res,
            HttpServletResponseLegacyWrapper wrapper,
            String path, String contextPath) throws IOException {

        String output = wrapper.getCapturedOutputAsString();

        if (wrapper.isRedirected()) {
            writeRedirect(res, wrapper, contextPath);
            return;
        }

        output = getInjectedContent(path, output);

        writeFinalResponse(res, wrapper, output);
    }

    private void writeRedirect(HttpServletResponse res,
            HttpServletResponseLegacyWrapper wrapper, String contextPath) throws IOException {

        String location = wrapper.getRedirectLocation();
        String html = getHtmlRedirect(location, true, contextPath);

        res.setContentType(wrapper.getContentType());
        res.setStatus(wrapper.getStatus());
        res.getWriter().write(html);
        res.getWriter().flush();
    }

    private void writeFinalResponse(HttpServletResponse res,
            HttpServletResponseLegacyWrapper wrapper,
            String output) throws IOException {

        HttpServletRequest req = RequestContext.get().getRequest();
        if (isProcessCommandPopup(req, output)) {
            writeProcessCommandForwarder(res, output);
            return;
        }

        res.setContentType(HTML_UTF8_CONTENT_TYPE);
        res.setCharacterEncoding(UTF8_CHARSET);
        res.setStatus(wrapper.getStatus());
        res.getWriter().write(output);
        res.getWriter().flush();
    }

    /**
     * Detects whether the captured response is the popup emitted by an action
     * button servlet in response to {@code Command=PROCESS}. Combines two
     * independent signals — the request parameter and a marker in the body —
     * so GRID/SAVE/DEFAULT requests that happen to contain the marker (highly
     * unlikely) are not accidentally rerouted.
     *
     * @param req  the original request
     * @param body the captured legacy HTML
     * @return {@code true} when the short-circuit path should serve the response
     */
    private boolean isProcessCommandPopup(HttpServletRequest req, String body) {
        if (body == null || !body.contains(POPUP_MESSAGE_MARKER)) {
            return false;
        }
        String command = req.getParameter(COMMAND_PARAM);
        if (command == null) {
            return false;
        }
        return command.toUpperCase().startsWith(PROCESS_COMMAND_PREFIX);
    }

    /**
     * Returns the first captured group for {@code pattern} in {@code source},
     * or {@code fallback} when no match is found. Trims the match to drop the
     * whitespace Openbravo templates leave around the title/message text.
     */
    private String extractFirstGroup(Pattern pattern, String source, String fallback) {
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallback;
    }

    /**
     * Maps the Openbravo {@code MessageBoxXXX} CSS class suffix to the type
     * string the new UI overlay expects.
     */
    private String mapMessageType(String classSuffix) {
        String upper = classSuffix.toUpperCase();
        if (upper.contains("ERROR")) return "error";
        if (upper.contains("SUCCESS")) return "success";
        if (upper.contains("WARNING")) return "warning";
        return "info";
    }

    /**
     * Writes the short-circuit response for {@code Command=PROCESS} popups: a
     * tiny, self-contained HTML that dispatches {@code showProcessMessage} and
     * {@code closeModal} to the parent window. Because the body is small and
     * written in one go, Tomcat does not trigger chunked encoding, avoiding
     * the {@code ERR_INCOMPLETE_CHUNKED_ENCODING} race that affected the full
     * popup path.
     *
     * @param res           the real response
     * @param capturedHtml  the original popup HTML captured by the wrapper
     * @throws IOException if the response writer fails
     */
    private void writeProcessCommandForwarder(HttpServletResponse res, String capturedHtml) throws IOException {
        String title = extractFirstGroup(POPUP_TITLE_PATTERN, capturedHtml, "");
        String text = extractFirstGroup(POPUP_MESSAGE_PATTERN, capturedHtml, "");
        String typeClass = extractFirstGroup(POPUP_TYPE_PATTERN, capturedHtml, "INFO");

        String payload = buildForwarderPayload(mapMessageType(typeClass), title, text);
        String html = String.format(MINIMAL_FORWARDER_HTML, payload);

        res.setContentType(HTML_UTF8_CONTENT_TYPE);
        res.setCharacterEncoding(UTF8_CHARSET);
        res.setStatus(HttpServletResponse.SC_OK);
        res.getWriter().write(html);
        res.getWriter().flush();
    }

    private String buildForwarderPayload(String type, String title, String text) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", type);
            payload.put("title", title);
            payload.put("text", text);
            return payload.toString();
        } catch (JSONException e) {
            log.warn("Could not build forwarder payload, using fallback: {}", e.getMessage());
            return "{\"type\":\"info\",\"title\":\"\",\"text\":\"\"}";
        }
    }

    /**
     * Rolls back the DAL session when the captured response is an Openbravo
     * error popup. WAD action-button servlets (e.g. Reconciliation) catch their
     * own {@code OBException} to render the popup, but they leave the Hibernate
     * session with stale entities. Without this rollback, the post-request
     * commit in the filter chain throws {@code StaleStateException}, which
     * aborts the HTTP connection mid-stream and produces
     * {@code ERR_INCOMPLETE_CHUNKED_ENCODING} in the browser.
     *
     * @param wrapper the capturing wrapper holding the legacy response body
     */
    private void rollbackDalSessionIfErrorPopup(HttpServletResponseLegacyWrapper wrapper) {
        try {
            String body = wrapper.getCapturedOutputAsString();
            if (body == null || !body.contains(ERROR_POPUP_CLASS_MARKER)) {
                return;
            }
            OBDal.getInstance().rollbackAndClose();
            log.debug("Rolled back DAL session after detecting error popup in legacy response");
        } catch (Exception e) {
            log.warn("Could not rollback DAL session after error popup: {}", e.getMessage());
        }
    }

    /**
     * Creates a new session in the database (AD_Session table).
     * This method is a duplication of the logic found in
     * {@link org.openbravo.authentication.AuthenticationManager#createDBSession(HttpServletRequest, String, String, String)}.
     *
     * The duplication is necessary because the original method in
     * AuthenticationManager is 'protected'
     * and cannot be accessed directly from this context, even though we need to
     * manually establish
     * a session during the JWT-based redirect login flow.
     *
     * @param req         The current HTTP request.
     * @param strUser     The username for the session.
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
     * @param req                The current HTTP request.
     * @param strUser            The username for the session.
     * @param strUserAuth        The user ID (AD_User_ID).
     * @param successSessionType The status type for the session (e.g., "S" for
     *                           success).
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
     * Processes follow-up requests triggered by buttons or actions within legacy
     * pages.
     * Restores authentication context and forwards the request to the appropriate
     * legacy servlet.
     *
     * @param req the HttpServletRequest
     * @param res the HttpServletResponse
     * @throws IOException if an error occurs during processing
     */
    private void processLegacyFollowupRequest(HttpServletRequest req, HttpServletResponse res)
            throws IOException {

        String token = (String) (req.getSession(false) != null ? req.getSession(false).getAttribute("LEGACY_TOKEN")
                : null);
        String servletDir = (String) req.getSession(false).getAttribute("LEGACY_SERVLET_DIR");

        if (token == null) {
            writeRequestFailedForwarder(res, new OBException("No authentication token for follow-up request"));
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
                writeRequestFailedForwarder(res, new OBException(String.format(
                        "Could not determine target path for follow-up request. PathInfo: %s, ServletDir: %s",
                        req.getPathInfo(), servletDir)));
            }

        } catch (Exception e) {
            writeRequestFailedForwarder(res, e);
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

    /**
     * Best-effort extraction of the legacy target servlet path from the {@code Referer}
     * header. Used as fallback when {@code LEGACY_SERVLET_DIR} is not yet present in
     * the session (e.g. a follow-up request that races with the first servlet hit).
     *
     * <p>Returns {@code null} when the path cannot be inferred — the caller
     * ({@link #processLegacyFollowupRequest}) treats {@code null} as "give up" and
     * surfaces a {@code requestFailed} forwarder so the iframe shows the proper
     * error overlay instead of Tomcat's default page.
     */
    private String extractTargetPathFromReferer(String referer) {
        if (referer == null)
            return null;
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
        if (parts.length < 2)
            return null;
        String window = parts[0];
        String page = parts[1];
        if (page.endsWith(HTML_EXTENSION)) {
            page = page.substring(0, page.length() - HTML_EXTENSION.length());
        }
        String base = page.contains("_") ? page.substring(0, page.indexOf('_')) : page;
        return "org.openbravo.erpWindows." + window + "." + base;
    }

    /**
     * Generates an HTML redirect page that automatically redirects the browser to a
     * modified URL.
     * The method inserts the "/meta/forward" path into the redirect location and
     * creates
     * a simple HTML page with a meta refresh tag.
     *
     * @param redirectLocation the original redirect URL to be modified
     * @return an HTML string containing the redirect page with meta refresh
     */
    private static String getHtmlRedirect(String redirectLocation, boolean replacePath, String contextPath) {
        String forwardedUrl;
        if (replacePath) {
            forwardedUrl = redirectLocation.replace(contextPath, contextPath + META_LEGACY_PATH);
        } else {
            forwardedUrl = redirectLocation;
        }
        return String.format(
                "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "    <head>\n" +
                        "        <meta charset='" + UTF8_CHARSET + "'/>\n" +
                        "        <meta http-equiv=\"refresh\" content=\"0; url='%s'\"/>\n" +
                        "    </head>\n" +
                        "</html>",
                forwardedUrl);
    }

    private String buildFrameMenuShim() {
        String decSep = ".";
        String groupSep = ",";
        String maskNum = "#,##0.00";
        try {
            HttpServletRequest req = RequestContext.get().getRequest();
            VariablesSecureApp vars = new VariablesSecureApp(req);
            decSep = StringUtils.defaultIfBlank(vars.getSessionValue("#DECIMALSEPARATOR|QTYEDITION"), decSep);
            groupSep = StringUtils.defaultIfBlank(vars.getSessionValue("#GROUPSEPARATOR|QTYEDITION"), groupSep);
            maskNum = StringUtils.defaultIfBlank(vars.getSessionValue("#FORMATOUTPUT|QTYEDITION"), maskNum);
        } catch (Exception e) {
            log.warn("Could not read locale session values for frameMenu shim, using defaults: {}", e.getMessage());
        }
        return buildShimScript(escapeJs(decSep), escapeJs(groupSep), escapeJs(maskNum));
    }

    private static String buildShimScript(String decSep, String groupSep, String maskNum) {
        return "<script>(function(){" +
                "var m={" +
                "decSeparator_global:'" + decSep + "'," +
                "groupSeparator_global:'" + groupSep + "'," +
                "groupInterval_global:'3'," +
                "maskNumeric_default:'" + maskNum + "'," +
                "autosave:false," +
                "arrMessages:[]," +
                "arrTypes:[]," +
                "F:{formats:[],getFormat:function(n){return '" + maskNum + "';}}," +
                "getAppUrlFromMenu:function(){return '';}," +
                "focus:function(){}," +
                "document:window.document" +
                "};" +
                "window.frameMenu=m;" +
                "var _shimGetFrame=function(name){" +
                "if(name==='frameMenu')return window.frameMenu;" +
                "try{" +
                "if(window.parent&&window.parent.frames&&window.parent.frames[name])return window.parent.frames[name];" +
                "return null;" +
                "}catch(e){return null;}" +
                "};" +
                "window.getFrame=_shimGetFrame;" +
                "window._shimGetFrame=_shimGetFrame;" +
                "})();</script>";
    }

    private static String buildFrameMenuPatchScript() {
        return "<script>(function(){" +
                "if(typeof getFrame==='function'&&typeof window.frameMenu!=='undefined'&&window.getFrame!==window._shimGetFrame){" +
                "var _messagesGetFrame=getFrame;" +
                "window.getFrame=function(name){" +
                "if(name==='frameMenu')return window.frameMenu;" +
                "try{return _messagesGetFrame(name);}catch(e){return null;}" +
                "};" +
                "}" +
                "})();</script>";
    }

    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String injectFrameMenuShim(String responseString) {
        // Inject shim immediately after <head> (or <HEAD>) opening tag
        // This ensures it runs BEFORE other scripts in <head>
        Pattern headOpenPattern = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = headOpenPattern.matcher(responseString);
        if (matcher.find()) {
            String headTag = matcher.group(0);
            String shimScript = buildFrameMenuShim();
            responseString = responseString.replace(headTag, headTag + shimScript);
        }

        // Inject patch script before </HEAD> to wrap messages.js's getFrame
        // This is necessary because function declarations bypass Object.defineProperty setters
        if (responseString.contains(HEAD_CLOSE_TAG)) {
            String patchScript = buildFrameMenuPatchScript();
            return responseString.replace(HEAD_CLOSE_TAG, patchScript.concat(HEAD_CLOSE_TAG));
        }

        return responseString;
    }

    /**
     * Injects compatibility scripts and adjusts paths in the HTML response content.
     * This ensures that legacy pages can communicate with the modern frontend
     * and that all resource links work correctly.
     *
     * @param path           the resource path
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
                .replace(SRC_REPLACE_STRING + "../utility/DynamicJS.js",
                        SRC_REPLACE_STRING + contextPath + "/utility/DynamicJS.js")
                .replace(SRC_REPLACE_STRING + "../org.openbravo.client.kernel/",
                        SRC_REPLACE_STRING + contextPath + "/org.openbravo.client.kernel/")
                .replace(SRC_REPLACE_STRING + "../web/", SRC_REPLACE_STRING + contextPath + WEB_PATH)
                .replace("href=\"../web/", "href=\"" + contextPath + WEB_PATH);

        responseString = injectFrameMenuShim(responseString);

        if (responseString.contains(FRAMESET_CLOSE_TAG)) {
            return responseString.replace(HEAD_CLOSE_TAG, RECEIVE_AND_POST_MESSAGE_SCRIPT.concat(HEAD_CLOSE_TAG));
        }

        if (responseString.contains(FORM_CLOSE_TAG)) {
            String resWithNewScript = responseString.replace(FORM_CLOSE_TAG,
                    FORM_CLOSE_TAG.concat(POST_MESSAGE_SCRIPT));
            resWithNewScript = resWithNewScript.replace(SRC_REPLACE_STRING + "../web/",
                    SRC_REPLACE_STRING + contextPath + WEB_PATH);
            resWithNewScript = resWithNewScript.replace("href=\"../web/", "href=\"" + contextPath + WEB_PATH);

            return injectCodeAfterFunctionCall(
                    injectCodeBeforeFunctionCall(
                            resWithNewScript,
                            "submitThisPage\\(([^)]+)\\);",
                            "sendMessage('" + LegacyMessageProtocol.ACTION_PROCESS_ORDER + "');",
                            true),
                    "close(This)?Page\\(\\);",
                    "sendMessage('" + LegacyMessageProtocol.ACTION_CLOSE_MODAL + "');",
                    true);
        }

        return injectPopupMessageForwarder(responseString);
    }

    /**
     * Injects the {@link #SHOW_PROCESS_MESSAGE_SCRIPT} into Openbravo classic
     * popup-message pages (identified by {@link #POPUP_MESSAGE_MARKER}) so the
     * title/text/type of the dialog is forwarded to the parent window via
     * {@code postMessage} and the iframe is closed afterwards. Pages that don't
     * match the popup template are returned unchanged.
     *
     * @param responseString the HTML response after all previous injections
     * @return the HTML with the forwarder script injected before {@code </HEAD>},
     *         or the original HTML when the popup marker or close-tag is absent
     */
    private String injectPopupMessageForwarder(String responseString) {
        if (!responseString.contains(POPUP_MESSAGE_MARKER) || !responseString.contains(HEAD_CLOSE_TAG)) {
            return responseString;
        }
        return responseString.replace(HEAD_CLOSE_TAG, SHOW_PROCESS_MESSAGE_SCRIPT.concat(HEAD_CLOSE_TAG));
    }

    private String injectCodeAfterFunctionCall(String originalRes, String originalFunctionCall, String newFunctionCall,
            boolean isRegex) {
        Pattern pattern = isRegex ? Pattern.compile(originalFunctionCall)
                : Pattern.compile(Pattern.quote(originalFunctionCall));
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

    /**
     * Same as {@link #injectCodeAfterFunctionCall} but inserts {@code newFunctionCall}
     * BEFORE the matched call. Used when the call itself triggers navigation
     * (e.g. {@code submitThisPage}) and we need the side-effect (e.g. postMessage)
     * to fire before the page can unload.
     */
    private String injectCodeBeforeFunctionCall(String originalRes, String originalFunctionCall,
            String newFunctionCall, boolean isRegex) {
        Pattern pattern = isRegex ? Pattern.compile(originalFunctionCall)
                : Pattern.compile(Pattern.quote(originalFunctionCall));
        Matcher matcher = pattern.matcher(originalRes);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String original = matcher.group(0);
            String modified = newFunctionCall + original;
            matcher.appendReplacement(result, Matcher.quoteReplacement(modified));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

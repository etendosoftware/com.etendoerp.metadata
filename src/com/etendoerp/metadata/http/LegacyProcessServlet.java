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
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.utils.LegacyPaths;
import com.etendoerp.metadata.utils.LegacyUtils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
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
import org.openbravo.model.financialmgmt.accounting.coa.AcctSchema;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
@SuppressWarnings("java:S1075")
public class LegacyProcessServlet extends HttpSecureAppServlet {
    private static final Logger log = LogManager.getLogger();
    private static final String JWT_TOKEN = "#JWT_TOKEN";
    private static final String HTML_EXTENSION = ".html";
    private static final String JS_EXTENSION = ".js";
    private static final String ACTION_JSON_SEPARATOR = "',action:";
    private static final String HTML_UTF8_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String UTF8_CHARSET = "UTF-8";
    private static final String JSON_CONTENT_TYPE_PREFIX = "application/json";
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
     * JS-array literal listing the {@code Command} prefixes the legacy framework uses
     * for form refreshes (combo onChange, response page reload, comparative-mode
     * checkbox) — i.e. submissions that unload the iframe but do <strong>not</strong>
     * represent a process execution. When {@code document.forms[0].Command.value}
     * matches any of these prefixes at unload time, the iframe suppresses
     * {@link LegacyMessageProtocol#ACTION_IFRAME_UNLOADED} so the new UI does not
     * arm its fallback-warning countdown for a non-process navigation. Counterpart
     * of {@link #PROCESS_COMMAND_PREFIX}; values audited against
     * {@code erp/src/org/openbravo/erpCommon/ad_actionButton/} (27 templates) and
     * {@code vars.commandIn(...)} call sites in the related Java servlets.
     */
    private static final String REFRESH_COMMAND_PREFIXES_JS = "['FIND','REFRESH','DEFAULT']";

    /**
     * Script injected into legacy form pages to expose {@code window.sendMessage}
     * and to guarantee that the parent always receives a final notification even
     * when the page is unloaded mid-flight (redirect, navigation, browser-tab
     * change). Tracks whether a "final" message ({@code showProcessMessage} or
     * {@code closeModal}) was emitted; if not, on {@code pagehide}/{@code beforeunload}
     * it sends an {@code iframeUnloaded} action so the new UI can show the
     * fallback warning instead of leaving the user without feedback.
     *
     * <p>The {@code notifyUnload} guard reads the form's {@code Command} value
     * before posting and short-circuits when it matches a refresh prefix from
     * {@link #REFRESH_COMMAND_PREFIXES_JS} — this prevents the false warning that
     * would otherwise appear when the user refreshes the popup contents (combo
     * onChange firing {@code submitCommandForm('FIND_PO',...)} and similar).
     */
    private static final String POST_MESSAGE_SCRIPT = "<script>(function(){" +
            "var sent=false;" +
            "var REFRESH_COMMAND_PREFIXES=" + REFRESH_COMMAND_PREFIXES_JS + ";" +
            "function getSubmittedCommand(){" +
            "var f=document.forms&&document.forms[0];" +
            "if(!f||!f.Command)return null;" +
            "return f.Command.value||null;" +
            "}" +
            "function isRefreshCommand(cmd){" +
            "if(!cmd)return false;" +
            "var upper=String(cmd).toUpperCase();" +
            "for(var i=0;i<REFRESH_COMMAND_PREFIXES.length;i++){" +
            "if(upper.indexOf(REFRESH_COMMAND_PREFIXES[i])===0)return true;" +
            "}" +
            "return false;" +
            "}" +
            "window.sendMessage=function(action){" +
            "if(!window.parent)return;" +
            "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
            + "action},'*');" +
            "if(action==='" + LegacyMessageProtocol.ACTION_SHOW_PROCESS_MESSAGE + "'||action==='"
            + LegacyMessageProtocol.ACTION_CLOSE_MODAL + "'){sent=true;window.__etendoMessageSent=true;}" +
            "};" +
            "function notifyUnload(){" +
            "if(sent||window.__etendoMessageSent)return;" +
            "if(isRefreshCommand(getSubmittedCommand()))return;" +
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
     * dialog with its own styles.
     *
     * <p>The modal close is intentionally NOT triggered from here: the React shell
     * governs the lifecycle ({@code success} auto-closes after its 3 s progress
     * timer; {@code error}/{@code warning}/{@code info} stay open until the user
     * dismisses them). This mirrors {@link #MINIMAL_FORWARDER_HTML}, which also
     * relies on the parent for the close decision.
     */
    private static final String SHOW_PROCESS_MESSAGE_SCRIPT = "<script>(function(){" +
            "function forward(){" +
            "var t=document.getElementById('messageBoxIDTitle');" +
            "var m=document.getElementById('messageBoxIDMessage');" +
            "if(!t||!m||!window.parent)return;" +
            "var typeEl=document.querySelector('.MessageBoxERROR,.MessageBoxSUCCESS,.MessageBoxWARNING,.MessageBoxINFO');" +
            "var cls=(typeEl&&typeEl.className)||'';" +
            "var type='info';" +
            "if(cls.indexOf('ERROR')>=0)type='error';" +
            "else if(cls.indexOf('SUCCESS')>=0)type='success';" +
            "else if(cls.indexOf('WARNING')>=0)type='warning';" +
            "var payload={type:type,title:(t.textContent||'').trim(),text:(m.textContent||'').trim()};" +
            "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
            + "'" + LegacyMessageProtocol.ACTION_SHOW_PROCESS_MESSAGE + "',payload:payload},'*');" +
            "window.__etendoMessageSent=true;" +
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
     * from the captured legacy HTML. The {@code messageBoxIDTitle} and
     * {@code messageBoxIDMessage} ids are stable across all Openbravo classic
     * templates; the message-type signal is matched on the CSS class
     * ({@code MessageBoxERROR|SUCCESS|WARNING|INFO}) because the id of the
     * carrier table is inconsistent — most templates use {@code paramTipo}
     * (Spanish) but {@code AdvisePopUpRefresh.html} uses {@code paramType}
     * (English). The class is the canonical signal in every template.
     */
    private static final Pattern POPUP_TITLE_PATTERN = Pattern.compile(
            "id=\"messageBoxIDTitle\"[^>]*>([^<]*)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern POPUP_MESSAGE_PATTERN = Pattern.compile(
            "id=\"messageBoxIDMessage\"[^>]*>([^<]*)<", Pattern.CASE_INSENSITIVE);
    private static final Pattern POPUP_TYPE_PATTERN = Pattern.compile(
            "class=\"MessageBox(ERROR|SUCCESS|WARNING|INFO)\"", Pattern.CASE_INSENSITIVE);

    /** Shared HTML preamble for all postMessage forwarder pages. */
    private static final String HTML_DOCTYPE_HEAD =
            "<!DOCTYPE html>\n<html><head><meta charset=\"" + UTF8_CHARSET + "\"><script>";

    /**
     * Minimal self-contained HTML served for {@code Command=PROCESS} popups.
     * Bypasses the legacy HTML pipeline entirely: a tiny (~500 B) response
     * cannot trigger the chunked-encoding race that breaks the full popup path.
     * The payload placeholder is a JSON object with {@code type}/{@code title}/{@code text}.
     */
    private static final String MINIMAL_FORWARDER_HTML =
            HTML_DOCTYPE_HEAD +
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
            HTML_DOCTYPE_HEAD +
                    "(function(){if(!window.parent)return;" +
                    "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
                    + "'" + LegacyMessageProtocol.ACTION_REQUEST_FAILED + "'},'*');" +
                    "window.__etendoMessageSent=true;" +
                    "})();</script></head><body></body></html>";

    /**
     * Matches the {@code submitThisPage('<url>');} call emitted by the
     * {@code PopUp_Response.html} template (rendered by
     * {@code HttpSecureAppServlet.printPageClosePopUp(response, vars, path, ...)}).
     * The captured group is any URL-shaped argument — relative path
     * ({@code /...}) or absolute http(s) URL. This deliberately excludes:
     * <ul>
     *   <li>the no-URL branch ({@code submitThisPage(null);}) — no quotes;</li>
     *   <li>action codes used by the form-render HTML
     *       ({@code 'SAVE'}, {@code 'OK'}, {@code 'EDIT'}, {@code 'REFRESH'}, ...)
     *       which are passed to the same JS helper but identify a button command,
     *       not a URL.</li>
     * </ul>
     * Without this URL-shape check, the very first GET on an action button
     * (which renders the confirmation form) would short-circuit before the user
     * even submits.
     * <p>
     * Whether the matched URL must trigger an {@code openLegacyReport} forwarder
     * is decided separately by {@link #REPORT_PATH_MARKER}: the regex on its own
     * does NOT imply "is a report" — many callers of {@code printPageClosePopUp}
     * pass parent-tab URLs built from {@code Utility.getTabURL(...)} that just
     * refresh the source tab in the classic UI.
     */
    private static final Pattern SUBMIT_THIS_PAGE_HREF =
            Pattern.compile("submitThisPage\\(\\s*'(/[^']+|https?://[^']+)'\\s*\\);");

    /**
     * Path marker that identifies a classic Openbravo AD report URL. Posted
     * ({@code org.openbravo.erpCommon.ad_actionButton.Posted}) is the only
     * confirmed action button that returns a report — its URL is built from
     * {@code AcctServer.getDocumentPaths()} and always contains
     * {@code /ad_reports/}. All other callers of
     * {@code printPageClosePopUp(response, vars, path, ...)} (Reactivate via
     * {@code ProcessInvoice}, the {@code CopyFrom*} family, {@code ProjectClose},
     * etc.) pass window URLs from {@code Utility.getTabURL(strTabId, "R", true)}
     * — those must NOT short-circuit the pipeline; the existing
     * {@code processOrder}/{@code showProcessMessage} flow already handles them
     * (success/error message + parent table refresh).
     */
    private static final String REPORT_PATH_MARKER = "/ad_reports/";

    /**
     * Path of the General Ledger Journal report — the only confirmed AD report
     * that the classic UI explodes into N popups when the posted invoice has
     * multiple accounting schemas. The expansion logic lives in the report's
     * own JS ({@code openTabWhenPost} in
     * {@code ReportGeneralLedgerJournal.html}) and depends on the SmartClient
     * shell ({@code LayoutMDI}, {@code OB.Layout.ViewManager}) which the new
     * UI does not provide. We replicate it server-side so each schema gets
     * its own browser popup driven by the {@code openLegacyReport} forwarder.
     */
    private static final String JOURNAL_ENTRIES_REPORT_PATH = "/ad_reports/ReportGeneralLedgerJournal.html";

    private static final String INP_ACCSCHEMAS_KEY = "inpAccSchemas";
    private static final String INP_PARAMSCHEMAS_KEY = "inpParamschemas";
    private static final String POSTED_FLAG_KEY = "posted";
    private static final String POSTED_FLAG_YES = "Y";
    private static final String COMMAND_KEY = "Command";
    private static final String COMMAND_DIRECT = "DIRECT";
    private static final String INP_TABLE_KEY = "inpTable";
    private static final String INP_RECORD_KEY = "inpRecord";
    private static final String INP_ORG_KEY = "inpOrg";
    private static final String TAB_TITLE_SEPARATOR = " - ";

    /**
     * Captures the {@code tabTitle} value embedded in the {@code newTabParams}
     * JSON literal that {@code printPageClosePopUp(response, vars, path, title)}
     * writes to the popup body. The classic UI uses this title for the new
     * SmartClient tab; the new UI forwards it as {@code obManualURL} bookmark
     * metadata. Tolerates JSON-style escaped quotes ({@code \"}).
     */
    private static final Pattern TAB_TITLE_PATTERN =
            Pattern.compile("\"tabTitle\"\\s*+:\\s*+\"((?:[^\"\\\\]|\\\\.)*+)\"");

    /**
     * Self-contained HTML served when the captured legacy response is a
     * {@code PopUp_Response} with one or more report URLs. Posts an
     * {@code openLegacyReport} action with a payload of the shape
     * {@code {reports: [{processUrl, tabTitle, params}, ...]}} to the parent
     * window so the new UI can rebuild each Etendo Classic bookmark URL (via
     * {@code buildEtendoClassicBookmarkUrl}) and open it in a browser popup,
     * then close the iframe modal. The placeholder is the JSON payload.
     */
    private static final String OPEN_LEGACY_REPORT_FORWARDER_HTML =
            HTML_DOCTYPE_HEAD +
                    "(function(){if(!window.parent)return;" +
                    "window.parent.postMessage({type:'" + LegacyMessageProtocol.MESSAGE_TYPE + ACTION_JSON_SEPARATOR
                    + "'" + LegacyMessageProtocol.ACTION_OPEN_LEGACY_REPORT + "',payload:%s},'*');" +
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
            handleCreateFromSession(req, path, req.getSession(true));

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

        List<ReportInfo> reports = extractReportsFromBody(output, contextPath);
        if (!reports.isEmpty()) {
            writeOpenLegacyReportForwarder(res, reports);
            return;
        }

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

        applyFinalContentType(res, wrapper);
        res.setStatus(wrapper.getStatus());
        res.getWriter().write(output);
        res.getWriter().flush();
    }

    /**
     * Preserves the Content-Type declared by the wrapped legacy servlet when it
     * is a JSON response (e.g. {@code UsedByLink} answering {@code JSONCategory}
     * or {@code JSONLinkedItem}). For every other case the legacy WAD pipeline
     * produces HTML, so the historical default is kept.
     */
    private void applyFinalContentType(HttpServletResponse res,
            HttpServletResponseLegacyWrapper wrapper) {
        String wrappedContentType = wrapper.getContentType();
        if (wrappedContentType != null
                && wrappedContentType.toLowerCase().contains(JSON_CONTENT_TYPE_PREFIX)) {
            res.setContentType(wrappedContentType);
            return;
        }
        res.setContentType(HTML_UTF8_CONTENT_TYPE);
        res.setCharacterEncoding(UTF8_CHARSET);
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
     * Immutable triple describing a report ready to be opened by the new UI as
     * an Etendo Classic bookmark popup. The {@link #processUrl} is the
     * context-relative path of the report (e.g. {@code /ad_reports/Report.html})
     * that lands in {@code obManualURL}; {@link #params} is the query string
     * from the original URL ({@code Command=DIRECT&inpRecord=...}, without the
     * leading {@code ?}); {@link #tabTitle} is the title the popup tab will
     * display, extracted from the {@code newTabParams} literal in the popup
     * body.
     */
    static final class ReportInfo {
        final String processUrl;
        final String tabTitle;
        final String params;

        ReportInfo(String processUrl, String tabTitle, String params) {
            this.processUrl = processUrl;
            this.tabTitle = tabTitle;
            this.params = params;
        }
    }

    /**
     * Scans the captured legacy HTML for every {@code submitThisPage('<url>');}
     * call emitted by {@code PopUp_Response.html} and returns one
     * {@link ReportInfo} per URL that points at a real report (filtered by
     * {@link #REPORT_PATH_MARKER}). An empty list means the response is not a
     * popup that should open browser-level reports — the regular injection
     * pipeline handles everything else.
     *
     * <p>For each match the URL is split into its context-relative path and
     * its query string; the host (when present) and {@code contextPath} (when
     * matching) are stripped. The {@code tabTitle} is read once from the
     * shared {@code newTabParams} literal and applied to every report in the
     * body — {@code printPageClosePopUp(response, vars, path, title)} only
     * emits a single title per response.
     *
     * @param body        the captured legacy response body, may be {@code null}
     * @param contextPath the servlet context path (e.g. {@code /etendo}); {@code null} or empty disables the strip
     * @return the list of {@link ReportInfo}, never {@code null}
     */
    private List<ReportInfo> extractReportsFromBody(String body, String contextPath) {
        if (body == null) {
            return Collections.emptyList();
        }
        Matcher matcher = SUBMIT_THIS_PAGE_HREF.matcher(body);
        List<String> rawUrls = new ArrayList<>();
        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.contains(REPORT_PATH_MARKER)) {
                rawUrls.add(url);
            }
        }
        if (rawUrls.isEmpty()) {
            return Collections.emptyList();
        }
        String tabTitle = extractTabTitle(body);
        List<ReportInfo> reports = new ArrayList<>(rawUrls.size());
        for (String rawUrl : rawUrls) {
            String[] split = splitReportUrl(rawUrl, contextPath);
            reports.add(new ReportInfo(split[0], tabTitle, split[1]));
        }
        return expandMultiSchemaReports(reports, this::resolveAcctSchemaName);
    }

    /**
     * Returns the {@code tabTitle} value from the {@code newTabParams} JSON
     * literal that {@code printPageClosePopUp} writes to the popup body, or
     * an empty string when the literal is absent (defensive — {@code
     * printPageClosePopUp(response, vars, url, title)} always emits it).
     */
    private String extractTabTitle(String body) {
        Matcher m = TAB_TITLE_PATTERN.matcher(body);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * Strips the host (when the URL is absolute) and the context path (when it
     * matches), then splits the remainder on the first {@code ?}. Returns a
     * two-element array: {@code [processUrl, params]}. {@code params} is empty
     * when the URL has no query string.
     */
    private String[] splitReportUrl(String rawUrl, String contextPath) {
        String pathAndQuery = stripHost(rawUrl);
        String pathAndQueryNoContext = stripContextPath(pathAndQuery, contextPath);
        int q = pathAndQueryNoContext.indexOf('?');
        if (q < 0) {
            return new String[] { pathAndQueryNoContext, "" };
        }
        return new String[] {
                pathAndQueryNoContext.substring(0, q),
                pathAndQueryNoContext.substring(q + 1)
        };
    }

    private String stripHost(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int firstSlash = url.indexOf('/', scheme + 3);
        if (firstSlash < 0) {
            return "/";
        }
        return url.substring(firstSlash);
    }

    private String stripContextPath(String path, String contextPath) {
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return path;
        }
        if (path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    /**
     * Replicates the behaviour of {@code openTabWhenPost} in
     * {@code ReportGeneralLedgerJournal.html} server-side: when a report URL
     * targets the General Ledger Journal report, carries {@code posted=Y} and
     * a CSV {@code inpAccSchemas} with multiple values, the original
     * {@link ReportInfo} is followed by one extra entry per additional schema.
     * Each extra entry uses {@code inpParamschemas=<single_id>} (the same
     * shape the classic JS feeds {@code OB.Layout.ViewManager.openView}) and
     * keeps the invoice filters explicit so the popups don't rely on session
     * state shared with the first popup (they open in parallel from the new UI).
     *
     * @param reports         the reports as extracted from the popup body
     * @param schemaNameResolver function that maps an accounting-schema id to its
     *                        display name; tests inject a stub to avoid hitting DAL
     * @return the (possibly expanded) report list, in the order they will be opened
     */
    List<ReportInfo> expandMultiSchemaReports(List<ReportInfo> reports,
            Function<String, String> schemaNameResolver) {
        List<ReportInfo> out = new ArrayList<>(reports.size());
        for (ReportInfo r : reports) {
            if (!shouldExpandMultiSchema(r)) {
                out.add(r);
                continue;
            }
            out.addAll(expandJournalEntriesReport(r, schemaNameResolver));
        }
        return out;
    }

    private boolean shouldExpandMultiSchema(ReportInfo r) {
        if (!JOURNAL_ENTRIES_REPORT_PATH.equals(r.processUrl)) {
            return false;
        }
        Map<String, String> kv = parseQueryParams(r.params);
        if (!POSTED_FLAG_YES.equals(kv.get(POSTED_FLAG_KEY))) {
            return false;
        }
        String csv = kv.get(INP_ACCSCHEMAS_KEY);
        return csv != null && csv.contains(",");
    }

    private List<ReportInfo> expandJournalEntriesReport(ReportInfo original,
            Function<String, String> schemaNameResolver) {
        Map<String, String> kv = parseQueryParams(original.params);
        String[] schemas = kv.get(INP_ACCSCHEMAS_KEY).split(",");
        String table = kv.getOrDefault(INP_TABLE_KEY, "");
        String currentRecord = kv.getOrDefault(INP_RECORD_KEY, "");
        String org = kv.getOrDefault(INP_ORG_KEY, "");
        String titlePrefix = extractTitlePrefix(original.tabTitle);

        List<ReportInfo> result = new ArrayList<>();
        // Entry 0: clone of the original with `posted=Y` stripped. The popup's
        // openTabWhenPost JS only spawns extra in-popup Smartclient tabs when
        // the flag is present; with N additional popups already opened from
        // the parent UI, that in-popup spawn would override the first popup's
        // own content and make every popup display the same schema.
        String firstParams = removePostedFlag(original.params);
        result.add(new ReportInfo(original.processUrl, original.tabTitle, firstParams));
        for (int i = 1; i < schemas.length; i++) {
            String schemaId = schemas[i].trim();
            if (schemaId.isEmpty()) {
                continue;
            }
            String params = buildSingleSchemaParams(table, currentRecord, org, schemaId);
            String title = buildSchemaTabTitle(titlePrefix, schemaId, schemaNameResolver);
            result.add(new ReportInfo(original.processUrl, title, params));
        }
        return result;
    }

    /**
     * Decodes a query string ({@code k1=v1&k2=v2}) into a Map preserving the
     * insertion order. Each pair is URL-decoded so we recover the original
     * value (the source string was extracted from a quoted URL inside the
     * popup body and may have been percent-encoded). Malformed pairs (no
     * {@code =} or empty key) are skipped.
     */
    Map<String, String> parseQueryParams(String params) {
        Map<String, String> map = new LinkedHashMap<>();
        if (params == null || params.isEmpty()) {
            return map;
        }
        for (String pair : params.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            String val = pair.substring(eq + 1);
            try {
                key = URLDecoder.decode(key, UTF8_CHARSET);
                val = URLDecoder.decode(val, UTF8_CHARSET);
            } catch (Exception ignore) {
                // Keep raw values when decoding fails — defensive, should not
                // happen with well-formed legacy URLs.
            }
            map.put(key, val);
        }
        return map;
    }

    /**
     * Strips the {@code posted=<value>} pair from a query string, preserving
     * the order and decoding of the remaining pairs. Applied to the first
     * {@link ReportInfo} of a multi-schema expansion so the popup's
     * {@code openTabWhenPost} JS (in {@code ReportGeneralLedgerJournal.html})
     * does NOT detect the flag and call {@code OB.Layout.ViewManager.openView}
     * inside the popup's own SmartClient — the parent UI already opens
     * separate popups for the additional schemas, so the in-popup tab spawn
     * would override the first popup's own content.
     */
    private String removePostedFlag(String params) {
        if (params == null || params.isEmpty()) {
            return params;
        }
        StringBuilder sb = new StringBuilder();
        for (String pair : params.split("&")) {
            if (pair.startsWith(POSTED_FLAG_KEY + "=")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(pair);
        }
        return sb.toString();
    }

    /**
     * Builds the query string for a multi-schema follow-up popup. Order matters:
     * {@code Command=DIRECT} comes first so it wins over the {@code Command=DEFAULT}
     * that {@code OBClassicWindow} appends at the end of the iframe URL —
     * {@code vars.getCommand()} reads the first occurrence.
     */
    private String buildSingleSchemaParams(String table, String currentRecord, String org, String schemaId) {
        StringBuilder sb = new StringBuilder();
        sb.append(COMMAND_KEY).append('=').append(COMMAND_DIRECT);
        if (!table.isEmpty()) {
            sb.append('&').append(INP_TABLE_KEY).append('=').append(table);
        }
        if (!currentRecord.isEmpty()) {
            sb.append('&').append(INP_RECORD_KEY).append('=').append(currentRecord);
        }
        if (!org.isEmpty()) {
            sb.append('&').append(INP_ORG_KEY).append('=').append(org);
        }
        sb.append('&').append(INP_PARAMSCHEMAS_KEY).append('=').append(schemaId);
        return sb.toString();
    }

    /**
     * Returns the prefix portion of a tab title (everything before the last
     * {@value #TAB_TITLE_SEPARATOR}). The classic flow renders
     * {@code "Journal Entries Report - <firstSchemaName>"} as the popup title;
     * we strip the schema-name suffix so the additional popups can build their
     * own title with the same prefix and their own schema name.
     */
    String extractTitlePrefix(String tabTitle) {
        if (tabTitle == null || tabTitle.isEmpty()) {
            return "";
        }
        int sep = tabTitle.lastIndexOf(TAB_TITLE_SEPARATOR);
        if (sep < 0) {
            return tabTitle;
        }
        return tabTitle.substring(0, sep);
    }

    private String buildSchemaTabTitle(String prefix, String schemaId,
            Function<String, String> schemaNameResolver) {
        String name = schemaNameResolver.apply(schemaId);
        if (prefix.isEmpty()) {
            return name;
        }
        return prefix + TAB_TITLE_SEPARATOR + name;
    }

    /**
     * Resolves the display name of an accounting schema via the DAL. Falls
     * back to the raw {@code schemaId} when the entity is missing or the
     * lookup throws — defensive, the report popup is still openable with the
     * UUID in the title (better than a 500 swallowed by the forwarder).
     * Uses {@code setAdminMode} because additional schemas may belong to
     * organisations the current user cannot see directly.
     */
    private String resolveAcctSchemaName(String schemaId) {
        try {
            OBContext.setAdminMode(true);
            try {
                AcctSchema schema = OBDal.getInstance().get(AcctSchema.class, schemaId);
                if (schema != null && schema.getName() != null) {
                    return schema.getName();
                }
            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            log.warn("Could not resolve AcctSchema name for id {}: {}", schemaId, e.getMessage());
        }
        return schemaId;
    }

    /**
     * Writes the short-circuit response for legacy popups that carry one or more
     * report URLs (e.g. the page rendered by {@code printPageClosePopUp(res, vars,
     * url, title)} after a {@code Posted} action succeeds). Like
     * {@link #writeProcessCommandForwarder(HttpServletResponse, String)}, the
     * body is a tiny self-contained HTML that posts the {@code openLegacyReport}
     * action to the parent window with the {@link ReportInfo} list as payload.
     *
     * @param res     the real response
     * @param reports the reports extracted by {@link #extractReportsFromBody(String, String)}
     * @throws IOException if the response writer fails
     */
    private void writeOpenLegacyReportForwarder(HttpServletResponse res, List<ReportInfo> reports) throws IOException {
        String payload = buildOpenLegacyReportPayload(reports);
        String html = String.format(OPEN_LEGACY_REPORT_FORWARDER_HTML, payload);

        res.setContentType(HTML_UTF8_CONTENT_TYPE);
        res.setCharacterEncoding(UTF8_CHARSET);
        res.setStatus(HttpServletResponse.SC_OK);
        res.getWriter().write(html);
        res.getWriter().flush();
    }

    private String buildOpenLegacyReportPayload(List<ReportInfo> reports) {
        try {
            JSONArray reportsJson = new JSONArray();
            for (ReportInfo r : reports) {
                JSONObject obj = new JSONObject();
                obj.put("processUrl", r.processUrl);
                obj.put("tabTitle", r.tabTitle);
                obj.put("params", r.params);
                reportsJson.put(obj);
            }
            JSONObject payload = new JSONObject();
            payload.put("reports", reportsJson);
            return payload.toString();
        } catch (JSONException e) {
            log.warn("Could not build open-legacy-report payload, using fallback: {}", e.getMessage());
            return "{\"reports\":[]}";
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
     * {@link org.openbravo.authentication.AuthenticationManager# createDBSession(HttpServletRequest, String, String, String)}.
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
    
    private static void handleCreateFromSession(HttpServletRequest req, String path, HttpSession session) {
        if (LegacyPaths.CREATE_FROM_HTML.equals(path) && "SAVE".equals(req.getParameter("Command"))) {
            String windowId = req.getParameter("inpWindowId");
            String tableId = req.getParameter("inpTableId");
            String sessionKey = "CREATEFROM|TABID";

            if (!LegacyUtils.isMutableSessionAttribute(sessionKey)) {
                throw new InternalServerException("Attempt to set forbidden session key: " + sessionKey);
            }

            String tabId = LegacyUtils.findTabIdByWindowAndTable(windowId, tableId);
            if (tabId != null) {
                session.setAttribute(sessionKey, tabId);
            } else {
                log.warn("Could not resolve tabId for windowId={}, tableId={}. " +
                         "CreateFrom|tabId will not be set in session.", windowId, tableId);
            }
        }
    }
}

package com.etendoerp.metadata.http;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.RequestVariables;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBConfigFileProvider;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.etendoerp.metadata.utils.Constants.FORM_CLOSE_TAG;
import static com.etendoerp.metadata.utils.Constants.FRAMESET_CLOSE_TAG;
import static com.etendoerp.metadata.utils.Constants.HEAD_CLOSE_TAG;

/**
 * Legacy servlet that uses existing HttpServletRequestWrapper infrastructure
 * to handle legacy HTML pages and their follow-up requests.
 */
public class LegacyProcessServlet extends HttpSecureAppServlet {
    private static final Logger log4j = LogManager.getLogger(LegacyProcessServlet.class);
    private static final String JWT_TOKEN = "#JWT_TOKEN";
    private static final String HTML_EXTENSION = ".html";
    private static final String TOKEN_PARAM = "token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String META_LEGACY_PATH = "/meta/legacy";
    private static final String BASE_PATH = "/etendo";

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

    private void setSessionCookie(HttpServletResponse res, String sessionId) {
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

        res.setHeader("Set-Cookie",
                String.format("JSESSIONID=%s; Path=/; Domain=%s; HttpOnly; %s; SameSite=None",
                        sessionId,
                        host,
                        isProduction ? "Secure" : ""
                )
        );
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        try {
            String path = req.getPathInfo();
            HttpSession session = req.getSession(true);

            setSessionCookie(res, session.getId());

            if (isLegacyRequest(path)) {
                processLegacyRequest(req, res, path);
            } else if (isLegacyFollowupRequest(req)) {
                processLegacyFollowupRequest(req, res);
            } else {
                super.service(req, res);
            }
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            throw new ServletException(e);
        }
    }


    private boolean isLegacyRequest(String path) {
        return path != null && path.toLowerCase().endsWith(HTML_EXTENSION);
    }

    private boolean isLegacyFollowupRequest(HttpServletRequest req) {
        String command = req.getParameter("Command");
        return command != null && command.startsWith("BUTTON");
    }

    private void processLegacyRequest(HttpServletRequest req, HttpServletResponse res, String path)
            throws IOException {
        String token = req.getParameter(TOKEN_PARAM);
        req.getSession(true);
        if (token != null) {
            req.getSession().setAttribute("LEGACY_TOKEN", token);
        }
        if (path != null && path.contains("/")) {
            String servletDir = path.substring(0, path.lastIndexOf("/"));
            req.getSession().setAttribute("LEGACY_SERVLET_DIR", servletDir);
        }

        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(req) {
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
                    return Collections.enumeration(
                            Arrays.asList(BEARER_PREFIX + token)
                    );
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

        var responseWrapper = new HttpServletResponseLegacyWrapper(res);

        try {
            handleTokenConsistency(req, wrappedRequest);
            handleRecordIdentifier(wrappedRequest);
            handleRequestContext(res, wrappedRequest);
            maybeValidateLegacyClass(wrappedRequest.getPathInfo());

            wrappedRequest.getRequestDispatcher(path).include(wrappedRequest, responseWrapper);

            String output = responseWrapper.getCapturedOutputAsString();
            if (responseWrapper.isRedirected()) {
                String redirectLocation = responseWrapper.getRedirectLocation();
                String htmlRedirect = getHtmlRedirect(redirectLocation);

                res.setContentType(responseWrapper.getContentType());
                res.setStatus(responseWrapper.getStatus());
                res.getWriter().write(htmlRedirect);
                res.getWriter().flush();
                return;
            }
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

    private void processLegacyFollowupRequest(HttpServletRequest req, HttpServletResponse res)
            throws IOException {

        String token = (String) (req.getSession(false) != null ? req.getSession(false).getAttribute("LEGACY_TOKEN") : null);
        String servletDir = (String) req.getSession(false).getAttribute("LEGACY_SERVLET_DIR");

        if (token == null) {
            log4j.error("No token found in session for legacy follow-up request");
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
                log4j.debug("Forwarding follow-up request to: {}", targetPath);
                wrappedRequest.getRequestDispatcher(targetPath).forward(wrappedRequest, res);
            } else {
                log4j.error("Could not determine target path for follow-up request. PathInfo: {}, ServletDir: {}",
                        req.getPathInfo(), servletDir);
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not determine target servlet");
            }

        } catch (Exception e) {
            log4j.error("Error processing legacy follow-up request: {}", e.getMessage(), e);
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
            log4j.warn("Error extracting target path from referer: {}", referer, e);
            return null;
        }
    }

    private void handleTokenConsistency(HttpServletRequest req, HttpServletRequestWrapper request) {
        String token = req.getParameter(TOKEN_PARAM);
        if (token != null) {
            req.getSession().setAttribute(JWT_TOKEN, token);
        } else {
            Object sessionToken = req.getSession().getAttribute(JWT_TOKEN);
            if (sessionToken != null) {
                try {
                    DecodedJWT decodedJWT = SecureWebServicesUtils.decodeToken(sessionToken.toString());
                    Claim jtiClaim = decodedJWT.getClaims().get("jti");
                    if (jtiClaim != null) {
                        request.setSessionId(jtiClaim.asString());
                    } else {
                        log4j.warn("JWT token in session does not contain 'jti' claim.");
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
            log4j.debug("Legacy class validation failed: {}", e.getMessage());
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
    private static String getHtmlRedirect(String redirectLocation) {
        String forwardedUrl = redirectLocation.replace(BASE_PATH, BASE_PATH + META_LEGACY_PATH);
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

    private String getInjectedContent(String path, String responseString) {
        String publicHost = OBPropertiesProvider.getInstance()
                .getOpenbravoProperties()
                .getProperty("next.public.etendo.classic.host");

        if (StringUtils.isEmpty(publicHost)) {
            publicHost = OBPropertiesProvider.getInstance()
                    .getOpenbravoProperties()
                    .getProperty("CLASSIC_URL", "");
        }

        String webResourceUrl = publicHost + "/web/";

        responseString = responseString
                .replace(META_LEGACY_PATH, META_LEGACY_PATH + path)
                .replace("src=\"../web/", "src=\"" + webResourceUrl)
                .replace("href=\"../web/", "href=\"" + webResourceUrl);

        if (responseString.contains(FRAMESET_CLOSE_TAG)) {
            return responseString.replace(HEAD_CLOSE_TAG, RECEIVE_AND_POST_MESSAGE_SCRIPT.concat(HEAD_CLOSE_TAG));
        }

        if (responseString.contains(FORM_CLOSE_TAG)) {
            String resWithNewScript = responseString.replace(FORM_CLOSE_TAG, FORM_CLOSE_TAG.concat(POST_MESSAGE_SCRIPT));
            resWithNewScript = resWithNewScript.replace("src=\"../web/", "src=\"" + webResourceUrl);
            resWithNewScript = resWithNewScript.replace("href=\"../web/", "href=\"" + webResourceUrl);
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
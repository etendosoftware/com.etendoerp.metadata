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

import static com.etendoerp.metadata.utils.Constants.FORM_CLOSE_TAG;
import static com.etendoerp.metadata.utils.Constants.FRAMESET_CLOSE_TAG;
import static com.etendoerp.metadata.utils.Constants.HEAD_CLOSE_TAG;
import static com.etendoerp.metadata.utils.ServletRegistry.getDelegatedServlet;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.RequestVariables;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.client.kernel.RequestContext;

import org.openbravo.dal.core.OBContext;

/**
 * Servlet that forwards incoming requests to specific delegated servlets based on the path.
 * Handles both legacy and modern requests, managing session tokens and injecting scripts
 * into HTML responses as needed.
 *
 * <p>
 * For legacy requests (ending with .html), it wraps the response, manages JWT tokens,
 * sets up the request context, and injects custom scripts into the HTML output.
 * For other requests, it delegates to the appropriate servlet.
 * </p>
 *
 */
public class ForwarderServlet extends BaseServlet {
    /** Session attribute key for JWT token */
    private static final String JWT_TOKEN = "#JWT_TOKEN";

    /**
     * Main entry point for HTTP requests. Determines if the request is legacy or not,
     * and processes accordingly.
     *
     * @param req  the HttpServletRequest object
     * @param res  the HttpServletResponse object
     * @throws IOException if an input or output error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            //HttpServletRequest request = RequestContext.get().getRequest();
            //HttpServletResponse response = RequestContext.get().getResponse();
            String path = req.getPathInfo();
            if (isLegacyRequest(path)) {
              handleLegacyRequest(req, res, path, res);
            } else {
                //super.service(req, res, false, true);
                processForwardRequest(path, req, res);
            }
        } catch (IOException | ServletException e) {
            log4j.error(e.getMessage(), e);

            throw e;
        }
    }

    /**
     * Handles legacy requests (typically .html files).
     * Wraps the response, manages JWT tokens, sets up the request context,
     * and injects scripts into the HTML output.
     *
     * @param req      the original HttpServletRequest
     * @param res      the original HttpServletResponse
     * @param path     the request path
     * @param response the response object from the RequestContext
     * @throws ServletException if a servlet error occurs
     * @throws IOException if an input or output error occurs
     */
    private void handleLegacyRequest(HttpServletRequest req, HttpServletResponse res, String path,
        HttpServletResponse response) throws ServletException, IOException {
      var responseWrapper = new HttpServletResponseLegacyWrapper(res);
      HttpServletRequestWrapper request = new HttpServletRequestWrapper(req);

      handleTokenConsistency(req, request);

      handleRecordIdentifier(request);

      handleRequestContext(res, request);

      request.getRequestDispatcher(request.getPathInfo()).include(request, responseWrapper);

      String output = responseWrapper.getCapturedOutputAsString();

      output = getInjectedContent(path, output);

      response.setContentType(responseWrapper.getContentType());
      response.setStatus(responseWrapper.getStatus());
      response.getWriter().write(output);
      response.getWriter().flush();
    }

    /**
     * Sets up the request context and OBContext for the current request.
     *
     * @param res     the HttpServletResponse
     * @param request the wrapped HttpServletRequest
     */
    private static void handleRequestContext(HttpServletResponse res, HttpServletRequestWrapper request) {
      RequestVariables vars = new RequestVariables(request);
      RequestContext requestContext = RequestContext.get();
      requestContext.setRequest(request);
      requestContext.setVariableSecureApp(vars);
      requestContext.setResponse(res);
      OBContext.setOBContext(request);
    }

    /**
     * Stores a record identifier in the session if all required parameters are present.
     *
     * @param request the wrapped HttpServletRequest
     */
    private static void handleRecordIdentifier(HttpServletRequestWrapper request) {
      String inpKeyId = request.getParameter("inpKey");
      String inpWindowId = request.getParameter("inpwindowId");
      String inpKeyColumnId = request.getParameter("inpkeyColumnId");
      if (StringUtils.isNoneEmpty(inpKeyId, inpWindowId, inpKeyColumnId)) {
          request.getSession().setAttribute(inpWindowId + "|" + inpKeyColumnId.toUpperCase(), inpKeyId);
      }
    }

    /**
     * Ensures the JWT token is consistent between the request and the session.
     * If a token is present in the request, it is stored in the session.
     * If not, attempts to decode the token from the session and set the session ID.
     *
     * @param req     the original HttpServletRequest
     * @param request the wrapped HttpServletRequest
     */
    private static void handleTokenConsistency(HttpServletRequest req, HttpServletRequestWrapper request) {
      String token = req.getParameter("token");
      if (token != null) {
          req.getSession().setAttribute(JWT_TOKEN, token);
      } else {
          Object sessionToken = req.getSession().getAttribute(JWT_TOKEN);
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

    /**
     * Injects custom scripts and modifies resource paths in the HTML response as needed.
     * Adds scripts for message passing and button handling depending on the HTML structure.
     *
     * @param path           the request path
     * @param responseString the original HTML response
     * @return the modified HTML response with scripts injected
     */
    private String getInjectedContent(String path, String responseString) {
        responseString = responseString
          .replace("/meta/forward", "/meta/forward" + path)
          .replace("src=\"../web/", "src=\"../../../web/")
          .replace("href=\"../web/", "href=\"../../../web/");

        if (responseString.contains(FRAMESET_CLOSE_TAG)) {
            // Script to manage the messages between the client and the form's html
            return responseString.replace(HEAD_CLOSE_TAG, (generateReceiveAndPostMessageScript()).concat(HEAD_CLOSE_TAG));
        }
        if (responseString.contains(FORM_CLOSE_TAG)) {
            // Script to manage the buttons from the form
            String resWithNewScript = responseString.replace(FORM_CLOSE_TAG, FORM_CLOSE_TAG.concat(generatePostMessageScript()));

            // Changes on script calls to find the correct paths
            resWithNewScript = resWithNewScript.replace("src=\"../web/", "src=\"../../../web/");
            resWithNewScript = resWithNewScript.replace("href=\"../web/", "href=\"../../../web/");

            // Modified js code to add a call to the new function from the script added
            return injectCodeAfterFunctionCall(
                    injectCodeAfterFunctionCall(resWithNewScript, "submitThisPage\\(([^)]+)\\);", "sendMessage('processOrder');", true),
                    "closeThisPage();",
                    "sendMessage('closeModal');",
                    false
            );
        }
        return responseString;
      }

    /**
     * Generates a script for receiving and posting messages between the form and its parent window.
     *
     * @return the script as a String
     */
    private String generateReceiveAndPostMessageScript() {
        return "<script>window.addEventListener(\"message\", (event) => {if (event.data?.type === \"fromForm\" && window.parent) {window.parent.postMessage({ type: \"fromIframe\", action: event.data.action }, \"*\");}});</script>";
    }

    /**
     * Generates a script for sending messages from the form to its parent window.
     *
     * @return the script as a String
     */
    private String generatePostMessageScript() {
        return "<script>const sendMessage = (action) => {if (window.parent) {window.parent.postMessage({ type: \"fromForm\", action: action, }, \"*\");}}</script>";
    }

    /**
     * Injects a new function call after a specific function call in the HTML/JS code.
     *
     * @param originalRes            the original response string
     * @param originalFunctionCall   the function call to search for (regex or plain text)
     * @param newFunctionCall        the function call to inject after the original
     * @param isRegex                whether to treat originalFunctionCall as a regex
     * @return the modified response string
     */
    private String injectCodeAfterFunctionCall(String originalRes, String originalFunctionCall, String newFunctionCall, Boolean isRegex) {
        Pattern pattern = isRegex
                ? Pattern.compile(originalFunctionCall)
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
     * Delegates the request to the appropriate servlet based on the path.
     *
     * @param path     the request path
     * @param request  the HttpServletRequest
     * @param response the HttpServletResponse
     * @throws IOException if an input or output error occurs
     * @throws ServletException if a servlet error occurs
     */
    private void processForwardRequest(String path, HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        // Delegate the request to the target servlet
        HttpSecureAppServlet servlet = getDelegatedServlet(this, path);
        HttpSession session = request.getSession(false);
        if (session != null) {
          Object userId = request.getSession().getAttribute("#Authenticated_user");
          if (userId != null) {
            session.setAttribute("#CSRF_TOKEN", userId.toString());
            session.setAttribute("#Csrf_Token", userId.toString());
          }
        }
        servlet.service(request, response);
    }

    /**
     * Determines if the request is a legacy request (ends with .html).
     *
     * @param path the request path
     * @return true if the request is legacy, false otherwise
     */
    private boolean isLegacyRequest(String path) {
        path = path.toLowerCase();

        return path.endsWith(".html");
    }

}

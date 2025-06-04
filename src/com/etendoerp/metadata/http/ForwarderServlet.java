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

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.data.ContentCaptureWrapper;

/**
 * Servlet that forwards incoming requests to specific delegated servlets based on the path.
 * This is useful for handling dynamic endpoints or custom APIs.
 *
 * @author luuchorocha
 */
public class ForwarderServlet extends BaseServlet {
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            // Call the base service method to handle pre-processing and set RequestContext
            super.service(req, res, false, true);

            // Use the wrapped request and response
            HttpServletRequest request = RequestContext.get().getRequest();
            HttpServletResponse response = RequestContext.get().getResponse();

            // Find the target servlet based on the request path
            String path = req.getPathInfo();

            if (isLegacyRequest(path)) {
                ContentCaptureWrapper wrappedResponse = new ContentCaptureWrapper(response);
                request.getRequestDispatcher(path).forward(request, wrappedResponse);
                String resultContent = getInjectedContent(wrappedResponse);
                response.getWriter().write(resultContent);
            } else if (!isAssetRequest(path)) {
                processForwardRequest(path, request, response);
            }
        } catch (IOException | ServletException e) {
            log4j.error(e.getMessage(), e);

            throw e;
        }
    }

    private String getInjectedContent(ContentCaptureWrapper wrappedResponse) {
        String responseString = wrappedResponse.getCapturedContent();
        if (responseString.contains(FRAMESET_CLOSE_TAG)) {
            // Script to manage the messages between the client and the form's html
            return responseString.replace(HEAD_CLOSE_TAG, (generateReceiveAndPostMessageScript()).concat(HEAD_CLOSE_TAG));
        }
        if (responseString.contains(FORM_CLOSE_TAG)) {
            // Script to manage the buttons from the form
            String resWithNewScript = responseString.replace(FORM_CLOSE_TAG, FORM_CLOSE_TAG.concat(generatePostMessageScript()));

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

    private String generateReceiveAndPostMessageScript() {
        return "<script>window.addEventListener(\"message\", (event) => {if (event.data?.type === \"fromForm\" && window.parent) {window.parent.postMessage({ type: \"fromIframe\", action: event.data.action }, \"*\");}});</script>";
    }

    private String generatePostMessageScript() {
        return "<script>const sendMessage = (action) => {if (window.parent) {window.parent.postMessage({ type: \"fromForm\", action: action, }, \"*\");}}</script>";
    }

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

    private void processForwardRequest(String path, HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        // Delegate the request to the target servlet
        HttpSecureAppServlet servlet = getDelegatedServlet(this, path);
        servlet.service(request, response);
    }

    private boolean isLegacyRequest(String path) {
        path = path.toLowerCase();

        return path.endsWith(".html");
    }

    private boolean isAssetRequest(String path) {
        path = path.toLowerCase();

        return path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".gif");
    }
}

package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.ServletRegistry.getDelegatedServlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.client.kernel.RequestContext;

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
                request.getRequestDispatcher(path).forward(request, response);
            } else if (!isAssetRequest(path)) {
                processForwardRequest(path, request, response);
            }
        } catch (IOException | ServletException e) {
            log4j.error(e.getMessage(), e);

            throw e;
        }
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

package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.ServletRegistry.getDelegatedServlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
 * This is useful for handling dynamic endpoints or custom APIs.
 *
 * @author luuchorocha
 */
public class ForwarderServlet extends BaseServlet {
    private static final String JWT_TOKEN = "#JWT_TOKEN";

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            // Call the base service method to handle pre-processing and set RequestContext

            // Use the wrapped request and response
            HttpServletResponse response = RequestContext.get().getResponse();
            HttpServletRequest originalReq = req;
            req = HttpServletRequestWrapper.wrap(req);

            // Find the target servlet based on the request path
           String path = req.getPathInfo();
            if(!StringUtils.endsWith(path, ".html")) {
                HttpServletRequest request = RequestContext.get().getRequest();

                super.service(req, response, false, true);
                HttpSecureAppServlet servlet = getDelegatedServlet(this, path);
                servlet.service(req, response);
            } else {
                // Legacy mode
                var responseWrapper = new HttpServletResponseLegacyWrapper(res);
                HttpServletRequestWrapper request = (HttpServletRequestWrapper) req;
                if(originalReq.getParameter("token") != null) {
                    originalReq.getSession().setAttribute(JWT_TOKEN, originalReq.getParameter("token"));
                } else {
                    if(originalReq.getSession().getAttribute(JWT_TOKEN) != null) {
                        String token = originalReq.getSession().getAttribute(JWT_TOKEN).toString();
                        DecodedJWT decodedJWT;
                        try {
                            decodedJWT = SecureWebServicesUtils.decodeToken(token);
                            request.setSessionId(decodedJWT.getClaims().get("jti").asString());
                        } catch (Exception e) {
                            throw new OBException("Error decoding token", e);
                        }
                    }
                }
                RequestVariables vars = new RequestVariables(request);
                RequestContext requestContext = RequestContext.get();
                requestContext.setRequest(request);
                requestContext.setVariableSecureApp(vars);
                requestContext.setResponse(res);
                OBContext.setOBContext(request);
                request.getRequestDispatcher(request.getPathInfo()).include(request, responseWrapper);
                String output = responseWrapper.getCapturedOutputAsString();
                output = output.replace("/meta/forward", "/meta/forward" + path);
                output = output.replace("src=\"../web/", "src=\"../../../web/");
                output = output.replace("href=\"../web/", "href=\"../../../web/");
                response.setContentType(responseWrapper.getContentType());
                response.setStatus(responseWrapper.getStatus());
                response.getWriter().write(output);
                response.getWriter().flush();
            }
            // Delegate the request to the target servlet
        } catch (IOException | ServletException e) {
            log4j.error(e.getMessage(), e);

            throw e;
        }
    }
}

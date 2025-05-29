package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Constants.FRAMESET_CLOSE_TAG;
import static com.etendoerp.metadata.utils.ServletRegistry.getDelegatedServlet;

import java.io.IOException;

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
      String path = request.getPathInfo();

      if (isLegacyRequest(path)) {
        ContentCaptureWrapper wrappedResponse = new ContentCaptureWrapper(response);
        request.getRequestDispatcher(path).forward(request, wrappedResponse);
        String content = wrappedResponse.getCapturedContent();
        String replacement = getEventEmitterCode().concat(FRAMESET_CLOSE_TAG);
        String injectedContent = content.replace(FRAMESET_CLOSE_TAG, replacement);
        response.getWriter().write(injectedContent);
      } else {
        // Delegate the request to the target servlet
        HttpSecureAppServlet servlet = getDelegatedServlet(this, path);
        servlet.service(request, response);
      }
    } catch (IOException | ServletException e) {
      log4j.error(e.getMessage(), e);

      throw e;
    }
  }

  private String getEventEmitterCode() {
    return "<script>window.parent.postMessage({ action: \"ALERT\", data: \"Some process message\" });</script>";
  }

  private boolean isLegacyRequest(String path) {
    return path != null && path.toLowerCase().endsWith(".html");
  }
}

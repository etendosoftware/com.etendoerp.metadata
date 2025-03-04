package com.etendoerp.metadata;

import com.etendoerp.metadata.builders.LanguageBuilder;
import com.etendoerp.metadata.builders.MenuBuilder;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.requests.DelegatedRequest;
import com.etendoerp.metadata.requests.WindowRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.etendoerp.metadata.SessionManager.initializeSession;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    private static final Logger logger = LogManager.getLogger(MetadataServlet.class);

    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        if (path.startsWith(Constants.WINDOW_PATH)) {
            new WindowRequest(request, response).process();
        } else if (path.equals(Constants.MENU_PATH)) {
            handleMenuRequest(request, response);
        } else if (path.equals(Constants.SESSION_PATH)) {
            handleSessionRequest(request, response);
        } else if (path.startsWith(Constants.TOOLBAR_PATH)) {
            handleToolbarRequest(request, response);
        } else if (path.startsWith(Constants.DELEGATED_SERVLET_PATH)) {
            new DelegatedRequest(request, response, this.getServletConfig()).process();
        } else if (path.startsWith(Constants.LANGUAGE_PATH)) {
            handleLanguageRequest(request, response);
        } else {
            throw new NotFoundException();
        }
    }

    private void handleMenuRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(fetchMenu().toString());
    }

    private void handleSessionRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        initializeSession(this.getServletConfig(), this, request);
        response.getWriter().write(fetchSession().toString());
    }

    private void handleToolbarRequest(HttpServletRequest request, HttpServletResponse response) {
    }

    private void handleLanguageRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(fetchLanguages().toString());
    }

    private JSONArray fetchMenu() {
        return new MenuBuilder().toJSON();
    }

    private JSONObject fetchSession() {
        return new SessionBuilder().toJSON();
    }

    private JSONObject fetchLanguages() {
        return new LanguageBuilder().toJSON();
    }
}
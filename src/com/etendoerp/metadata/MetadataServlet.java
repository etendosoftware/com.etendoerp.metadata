package com.etendoerp.metadata;

import com.etendoerp.metadata.builders.MenuBuilder;
import com.etendoerp.metadata.builders.WindowBuilder;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, JSONException {
        try {
            OBContext.setAdminMode();
            handleRequest(request, response);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding("UTF-8");

        if (path.startsWith("/window/")) {
            response.getWriter().write(this.fetchWindow(request.getPathInfo().substring(8)).toString());
        } else if (path.equals("/menu")) {
            response.getWriter().write(this.fetchMenu().toString());
        } else {
            throw new NotFoundException("Not found");
        }
    }

    private JSONObject fetchWindow(String id) {
        return new WindowBuilder(id).toJSON();
    }

    private JSONArray fetchMenu() {
        return new MenuBuilder().toJSON();
    }
}

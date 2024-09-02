package com.etendo.metadata;

import com.etendo.metadata.builders.MenuBuilder;
import com.etendo.metadata.builders.WindowBuilder;
import com.etendo.metadata.exceptions.MethodNotAllowedException;
import com.etendo.metadata.exceptions.NotFoundException;
import com.etendo.metadata.exceptions.UnauthorizedException;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.DataToJsonConverter;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    private static final DataToJsonConverter converter = new DataToJsonConverter();

    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, JSONException {
        try {
            OBContext.setAdminMode();
            String path = request.getPathInfo() != null ? request.getPathInfo() : "";

            response.setContentType(APPLICATION_JSON);
            response.setCharacterEncoding("UTF-8");

            if (path.startsWith("/window/")) {
                this.fetchWindow(request, response);
            } else if (path.equals("/translations")) {
                this.fetchTranslations(response);
            } else if (path.equals("/menu")) {
                this.fetchMenu(response);
            } else {
                throw new NotFoundException("Not found");
            }
        } catch (UnauthorizedException e) {
            logger.warn(e.getMessage());
            response.setStatus(401);
            response.getWriter().write(new JSONObject(JsonUtils.convertExceptionToJson(e)).toString());
        } catch (MethodNotAllowedException e) {
            logger.warn(e.getMessage());
            response.setStatus(405);
            response.getWriter().write(new JSONObject(JsonUtils.convertExceptionToJson(e)).toString());
        } catch (Exception e) {
            logger.warn(e.getMessage());
            response.setStatus(500);
            response.getWriter().write(new JSONObject(JsonUtils.convertExceptionToJson(e)).toString());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void fetchTranslations(HttpServletResponse response) {
        response.setStatus(200);
    }

    private void fetchWindow(HttpServletRequest request, HttpServletResponse response) throws IOException, JSONException {
        JSONObject payload = getBody(request);
        String id = request.getPathInfo().substring(8);
        String language = payload.getString("language") != null ? payload.getString("language") : "en_US";
        Writer writer = response.getWriter();
        writer.write(new WindowBuilder(id, language).toJSON().toString());
        writer.close();
    }

    private void fetchMenu(HttpServletResponse response) throws IOException {
        Writer writer = response.getWriter();
        writer.write(new MenuBuilder().toJSON().toString());
        writer.close();
    }
}

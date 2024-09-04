package com.etendo.metadata;

import com.etendo.metadata.builders.MenuBuilder;
import com.etendo.metadata.builders.WindowBuilder;
import com.etendo.metadata.exceptions.MethodNotAllowedException;
import com.etendo.metadata.exceptions.NotFoundException;
import com.etendo.metadata.exceptions.UnauthorizedException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

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
            setContext(request);
            handleRequest(request, response);
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

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException, JSONException {
        String path = request.getPathInfo();
        JSONObject json = new JSONObject();

        if (path.startsWith("/window/")) {
            json.put("data", this.fetchWindow(request.getPathInfo().substring(8)));
        } else if (path.equals("/menu")) {
            json.put("data", this.fetchMenu());
        } else {
            throw new NotFoundException("Not found");
        }

        json.put("ok", true);
        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.toString());
    }

    private void setContext(HttpServletRequest request) {
        try {
            OBContext.setAdminMode();
            JSONObject payload = getBody(request);
            String language = payload.getString("language") != null ? payload.getString("language") : "";
            OBCriteria<Language> languageCriteria = OBDal.getInstance().createCriteria(Language.class);
            languageCriteria.add(Restrictions.eq(Language.PROPERTY_LANGUAGE, language));
            Language currentLanguage = (Language) languageCriteria.uniqueResult();

            if (currentLanguage.isSystemLanguage()) {
                OBContext.getOBContext().setLanguage(currentLanguage);
            } else {
                logger.warn("The provided language is not a valid system language");
            }
        } catch (JSONException e) {
            logger.warn(e.getMessage());
        }
    }

    private JSONObject fetchWindow(String id) throws JSONException {
        return new WindowBuilder(id).toJSON();
    }

    private JSONArray fetchMenu() {
        return new MenuBuilder().toJSON();
    }
}

package com.etendoerp.metadata.http;

import com.etendoerp.metadata.utils.Utils;
import com.etendoerp.metadata.service.MetadataService;
import com.etendoerp.metadata.service.ServiceFactory;
import org.apache.http.entity.ContentType;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.utils.Utils.getLanguage;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpSecureAppServlet {
    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            OBContext.setAdminMode();
            setContext(request);
            setContentHeaders(response);
            ServiceFactory factory = new ServiceFactory(this);
            MetadataService service = factory.getService(request.getPathInfo(), request, response);
            service.process();
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            response.setStatus(Utils.getResponseStatus(e));
            response.getWriter().write(JsonUtils.convertExceptionToJson(e));
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    @Override
    public final void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    @Override
    public final void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    private void setContext(HttpServletRequest request) {
        OBContext context = OBContext.getOBContext();
        Language language = getLanguage(request);

        if (language != null) {
            context.setLanguage(language);
        }

        OBContext.setOBContextInSession(request, context);
    }

    private void setContentHeaders(HttpServletResponse response) {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

}


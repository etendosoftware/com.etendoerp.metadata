package com.etendoerp.metadata.http;

import com.etendoerp.metadata.service.ServiceFactory;
import org.apache.http.entity.ContentType;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.exceptions.Utils.handleException;
import static com.etendoerp.metadata.utils.Utils.getLanguage;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpSecureAppServlet {
    private final static ServiceFactory factory = new ServiceFactory();

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            OBContext.setAdminMode();
            setContext(request);
            setContentHeaders(response);
            factory.getService(this, request, response).process();
        } catch (Exception e) {
            handleException(e, response);
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


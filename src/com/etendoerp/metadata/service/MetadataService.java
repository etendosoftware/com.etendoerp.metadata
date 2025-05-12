package com.etendoerp.metadata.service;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.metadata.utils.Utils;

/**
 * @author luuchorocha
 */
public abstract class MetadataService {
    private static final ThreadLocal<HttpServletRequest> requestThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<HttpServletResponse> responseThreadLocal = new ThreadLocal<>();
    protected final Logger logger = LogManager.getLogger(this.getClass());

    public MetadataService(HttpServletRequest request, HttpServletResponse response) {
        requestThreadLocal.set(request);
        responseThreadLocal.set(response);
        Utils.setContext(request);
    }

    public static void clear() {
        requestThreadLocal.remove();
        responseThreadLocal.remove();
    }

    protected HttpServletRequest getRequest() {
        return requestThreadLocal.get();
    }

    protected HttpServletResponse getResponse() {
        return responseThreadLocal.get();
    }

    protected void write(JSONObject data) throws IOException {
        HttpServletResponse response = getResponse();
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (Writer writer = response.getWriter()) {
            writer.write(data.toString());
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);

            throw e;
        }
    }

    public abstract void process() throws IOException;
}

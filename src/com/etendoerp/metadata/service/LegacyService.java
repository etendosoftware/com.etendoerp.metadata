package com.etendoerp.metadata.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.http.LegacyProcessServlet;

/**
 * Service that handles legacy process requests by delegating to {@link LegacyProcessServlet}.
 * <p>
 * This service is registered in the {@code MetadataServlet} to handle
 * requests under the {@code /meta/legacy/*} path.
 * </p>
 */
public class LegacyService extends MetadataService {

    /**
     * Creates a new {@link LegacyService} instance for handling legacy process requests.
     * <p>
     * The constructor receives the raw {@link HttpServletRequest} and {@link HttpServletResponse}
     * objects associated with the incoming request and delegates them to the parent
     * {@link MetadataService} for lifecycle management.
     * </p>
     *
     * @param request  the current HTTP request containing client data and path information
     * @param response the HTTP response object used to send data back to the client
     */
    public LegacyService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        try {
            HttpServletRequest request = getRequest();
            HttpServletResponse response = getResponse();
            String pathInfo = request.getPathInfo();
            HttpServletRequestWrapper wrappedRequest = getHttpServletRequestWrapper(pathInfo, request);

            LegacyProcessServlet legacyServlet = new LegacyProcessServlet();

            legacyServlet.service(wrappedRequest, response);

        } catch (Exception e) {
            throw new InternalServerException("Failed to process legacy request: " + e.getMessage());
        }
    }

    private HttpServletRequestWrapper getHttpServletRequestWrapper(String pathInfo, HttpServletRequest request) {
        String legacyPath = null;
        if (pathInfo != null && pathInfo.startsWith("/legacy")) {
            legacyPath = pathInfo.substring("/legacy".length());
        }

        String finalLegacyPath = legacyPath;
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getPathInfo() {
                return finalLegacyPath;
            }

            @Override
            public String getRequestURI() {
                String originalURI = super.getRequestURI();
                return originalURI.replace("/meta/legacy", "");
            }
        };
    }
}

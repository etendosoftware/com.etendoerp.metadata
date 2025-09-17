package com.etendoerp.metadata.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.http.LegacyProcessServlet;

/**
 * Service that handles legacy process requests by delegating to LegacyProcessServlet.
 * This service is registered in the MetadataServlet to handle /meta/legacy/* requests
 */
public class LegacyService extends MetadataService {

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
        String legacyPath = pathInfo.substring("/legacy".length());

        return new HttpServletRequestWrapper(request) {
            @Override
            public String getPathInfo() {
                return legacyPath;
            }

            @Override
            public String getRequestURI() {
                String originalURI = super.getRequestURI();
                return originalURI.replace("/meta/legacy", "");
            }
        };
    }
}
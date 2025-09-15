/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.service.ServiceFactory;
import com.etendoerp.metadata.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.service.web.WebService;

/**
 * @author luuchorocha
 */
public class MetadataServlet implements WebService {
    private static final Logger log4j = LogManager.getLogger(MetadataServlet.class);

    private void process(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            ServiceFactory.getService(req, res).process();
        } catch (Throwable t) {
            handleException(req, res, t);
        }
    }

    private void handleException(HttpServletRequest req, HttpServletResponse res, Throwable t) throws IOException {
        String correlationId = UUID.randomUUID().toString();
        Throwable root = getRootCause(t);
        int status = Utils.getHttpStatusFor(root);
        String method = req.getMethod();
        String uri = req.getRequestURI();
        String query = req.getQueryString();
        String accept = req.getHeader("Accept");
        String isc = req.getParameter("isc_dataFormat");

        log4j.error("[meta] Service error (cid={}): {} {}{} - {} ({})",
            correlationId,
            method,
            uri,
            query != null ? ("?" + query) : "",
            root.getMessage(),
            root.getClass().getName(),
            t);

        if (res.isCommitted()) {
            return;
        }

        boolean wantsJson = (isc != null && isc.equalsIgnoreCase("json")) || (accept != null && accept.toLowerCase().contains("application/json"));

        if (wantsJson) {
            String json = Utils.convertToJson(root).toString();
            json = json.substring(0, json.length() - 1) + ",\"cid\":\"" + correlationId + "\"}";
            Utils.writeJsonResponse(res, status, json);
        } else {
            String body = buildHtmlError(correlationId, status, method, uri, root.getMessage());
            res.reset();
            res.setStatus(status);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.setContentType("text/html; charset=UTF-8");
            res.getWriter().write(body);
            res.getWriter().flush();
        }
    }

    private Throwable getRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private String buildHtmlError(String cid, int status, String method, String uri, String message) {
        String safeMessage = message != null ? message : "Unexpected error";
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<title>Etendo Meta Error</title>"
            + "<style>body{font-family:system-ui,Segoe UI,Roboto,Arial,Helvetica,sans-serif;margin:2rem;color:#222}"
            + "h1{margin:0 0 .5rem 0;font-size:1.4rem}code{background:#f5f5f5;padding:.2rem .4rem;border-radius:4px}"
            + ".box{border:1px solid #eee;border-radius:8px;padding:1rem;margin-top:1rem}</style>"
            + "</head><body>"
            + "<h1>Request failed in /meta</h1>"
            + "<div class=\"box\">"
            + "<div><b>Status:</b> " + status + "</div>"
            + "<div><b>Method:</b> " + escape(method) + "</div>"
            + "<div><b>Path:</b> <code>" + escape(uri) + "</code></div>"
            + "<div><b>Message:</b> " + escape(safeMessage) + "</div>"
            + "<div><b>Correlation Id:</b> <code>" + cid + "</code></div>"
            + "</div>"
            + "<p>Please check server logs with the correlation id above.</p>"
            + "</body></html>";
    }

    private String escape(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
        throws Exception {
        process(request, response);
    }

    @Override
    public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
        throws Exception {
        process(request, response);
    }

    @Override
    public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
        throws Exception {
        process(request, response);
    }

    @Override
    public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
        throws Exception {
        process(request, response);
    }
}

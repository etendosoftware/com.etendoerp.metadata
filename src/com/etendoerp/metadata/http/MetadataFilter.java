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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.service.MetadataService;
import com.etendoerp.metadata.utils.Utils;

/**
 * @author luuchorocha
 */
@WebFilter(urlPatterns = { "/meta", "/meta/*" })
public class MetadataFilter implements Filter {
    private static final Logger log4j = LogManager.getLogger(MetadataFilter.class);

    @Override
    public void init(FilterConfig fConfig) {
        RequestContext.setServletContext(fConfig.getServletContext());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpReq = request instanceof HttpServletRequest ? (HttpServletRequest) request : null;
        HttpServletResponse httpRes = response instanceof HttpServletResponse ? (HttpServletResponse) response : null;
        boolean capture = shouldCaptureHtml(httpReq, httpRes);

        HttpServletResponse effectiveRes = httpRes;
        HttpServletResponseLegacyWrapper captureWrapper = null;

        try {
            if (capture && httpRes != null) {
                captureWrapper = new HttpServletResponseLegacyWrapper(httpRes);
                effectiveRes = captureWrapper;
            }
            chain.doFilter(request, effectiveRes);
            // Fallback diagnostic for empty successful HTML responses
            if (capture && captureWrapper != null) {
                byte[] body = captureWrapper.getCapturedOutput();
                int status = captureWrapper.getStatus();
                String contentType = captureWrapper.getContentType();
                if (body.length == 0 && (status == 0 || status == HttpServletResponse.SC_OK)) {
                    String uri = httpReq != null ? httpReq.getRequestURI() : "";
                    String html = buildHtmlError(UUID.randomUUID().toString(),
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        httpReq != null ? httpReq.getMethod() : "",
                        uri,
                        "Empty response returned by downstream component");
                    HttpServletResponse orig = httpRes;
                    orig.reset();
                    orig.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    orig.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    orig.setContentType("text/html; charset=UTF-8");
                    orig.getWriter().write(html);
                    orig.getWriter().flush();
                } else {
                    // Relay captured output as-is
                    HttpServletResponse orig = httpRes;
                    if (contentType != null) {
                        orig.setContentType(contentType);
                    }
                    if (status != 0) {
                        orig.setStatus(status);
                    }
                    if (body.length > 0) {
                        orig.getOutputStream().write(body);
                        orig.getOutputStream().flush();
                    }
                }
            }
        } catch (Throwable t) {
            handleException(request, response, t);
        } finally {
            MetadataService.clear();
        }
    }

    @Override
    public void destroy() {
    }

    private void handleException(ServletRequest req, ServletResponse res, Throwable t) throws IOException {
        HttpServletRequest request = req instanceof HttpServletRequest ? (HttpServletRequest) req : null;
        HttpServletResponse response = res instanceof HttpServletResponse ? (HttpServletResponse) res : null;

        String correlationId = UUID.randomUUID().toString();
        String method = request != null ? request.getMethod() : "";
        String uri = request != null ? request.getRequestURI() : "";
        String query = request != null ? request.getQueryString() : null;
        String accept = request != null ? request.getHeader("Accept") : null;

        Throwable root = getRootCause(t);
        String rootClass = root.getClass().getName();
        String rootMsg = root.getMessage();

        log4j.error("[meta] Unhandled error (cid={}): {} {}{} - {} ({})",
            correlationId,
            method,
            uri,
            query != null ? ("?" + query) : "",
            rootMsg,
            rootClass,
            t);

        if (response == null || response.isCommitted()) {
            return; // Nothing safe to do
        }

        int status = Utils.getHttpStatusFor(root);

        // Decide response type: HTML for legacy pages (.html in URI or Accept contains text/html), JSON otherwise
        String isc = request != null ? request.getParameter("isc_dataFormat") : null;
        boolean wantsHtml = (uri != null && uri.toLowerCase().endsWith(".html"))
            || (accept != null && accept.toLowerCase().contains("text/html"));
        if (isc != null && isc.equalsIgnoreCase("json")) {
            wantsHtml = false;
        }

        if (wantsHtml) {
            String body = buildHtmlErrorDetailed(correlationId, status, method, uri, rootClass, rootMsg, request);
            response.reset();
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/html; charset=UTF-8");
            response.getWriter().write(body);
            response.getWriter().flush();
        } else {
            String json = Utils.convertToJson(root).toString();
            // Attach correlation id for easier server/client matching
            json = json.substring(0, json.length() - 1) + ",\"cid\":\"" + correlationId + "\"}";
            Utils.writeJsonResponse(response, status, json);
        }
    }

    private boolean shouldCaptureHtml(HttpServletRequest req, HttpServletResponse res) {
        if (req == null || res == null) return false;
        String uri = req.getRequestURI();
        String accept = req.getHeader("Accept");
        return (uri != null && uri.toLowerCase().endsWith(".html"))
            || (accept != null && accept.toLowerCase().contains("text/html"));
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

    private String buildHtmlErrorDetailed(String cid, int status, String method, String uri, String exClass, String message, HttpServletRequest req) {
        String base = buildHtmlError(cid, status, method, uri, message + (exClass != null ? " (" + exClass + ")" : ""));
        boolean isLegacyHtml = uri != null && uri.toLowerCase().endsWith(".html");
        boolean isCNF = exClass != null && (exClass.endsWith("ClassNotFoundException") || (message != null && message.contains("org.openbravo.erpWindows")));
        if (isLegacyHtml && isCNF) {
            String hint = buildLegacyHint(uri);
            // Insert hint box before closing body
            return base.replace("</body>",
                "<div class=\"box\" style=\"border-color:#f2c200\"><b>Hint:</b> Legacy WAD servlet not found. "
                    + hint 
                    + "</div></body>");
        }
        return base;
    }

    private String buildLegacyHint(String uri) {
        String expected = deriveLegacyClass(uri);
        StringBuilder sb = new StringBuilder();
        if (expected != null) {
            sb.append("Expected class: <code>").append(escape(expected)).append("</code>. ");
        }
        sb.append("Make sure legacy WAD windows are generated and deployed (compile src-wad) so legacy HTML pages are available.");
        return sb.toString();
    }

    private String deriveLegacyClass(String uri) {
        try {
            // /etendo/meta/forward/SalesInvoice/Header_Edition.html => org.openbravo.erpWindows.SalesInvoice.Header
            int idx = uri.indexOf("/forward/");
            if (idx == -1) return null;
            String tail = uri.substring(idx + "/forward/".length());
            String[] parts = tail.split("/");
            if (parts.length < 2) return null;
            String window = parts[0];
            String page = parts[1];
            if (page.endsWith(".html")) page = page.substring(0, page.length() - 5);
            String base = page.contains("_") ? page.substring(0, page.indexOf('_')) : page;
            return "org.openbravo.erpWindows." + window + "." + base;
        } catch (Exception ignore) {
            return null;
        }
    }

    private Throwable getRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
    private String escape(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

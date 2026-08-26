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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * A response wrapper that captures everything written to it into an in-memory buffer
 * instead of sending it to the real client connection.
 *
 * <p>Used by {@link ForwarderServlet} to inspect the body a forwarded servlet is about to
 * write (e.g. to detect a stale-object/optimistic-lock conflict) before deciding whether to
 * rewrite it or replay it unchanged to the real {@link HttpServletResponse} via
 * {@link #flushToRealResponse()}.</p>
 */
class BufferedResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private ServletOutputStream sos;
    private PrintWriter writer;
    private int capturedStatus = HttpServletResponse.SC_OK;

    BufferedResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        this.capturedStatus = sc;
    }

    @Override
    public int getStatus() {
        return capturedStatus;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (sos == null) {
            sos = new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // no-op: this stream only buffers in memory, it is never async-write-ready-gated
                }

                @Override
                public void write(int b) {
                    buffer.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    buffer.write(b, off, len);
                }
            };
        }
        return sos;
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(buffer, resolveCharset()), true);
        }
        return writer;
    }

    @Override
    public boolean isCommitted() {
        // Nothing captured here is ever sent to the real client, so from the wrapped servlet's
        // point of view the response is never "already committed".
        return false;
    }

    /**
     * @return the buffered body written so far, decoded using the response's character encoding.
     */
    String getCapturedBodyAsString() {
        if (writer != null) {
            writer.flush();
        }
        return buffer.toString(resolveCharset());
    }

    /**
     * Replays the captured status code and body onto the real, wrapped response, unchanged.
     *
     * @throws IOException if writing to the real response fails
     */
    void flushToRealResponse() throws IOException {
        HttpServletResponse real = (HttpServletResponse) getResponse();
        real.setStatus(capturedStatus);
        if (writer != null) {
            writer.flush();
        }
        real.getOutputStream().write(buffer.toByteArray());
        real.getOutputStream().flush();
    }

    private Charset resolveCharset() {
        String encoding = getCharacterEncoding();
        return encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
    }
}

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
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.lenient;

/**
 * Fixtures shared by {@link ForwarderServletTest} and {@link StaleObjectConflictFilterTest},
 * which exercise the same stale-object-conflict detection through two different entry points
 * (the {@code sws/.../forward} servlet path and the direct datasource-servlet path).
 */
final class StaleObjectTestFixtures {

    static final String STALE_JSON_BODY =
            "{\"response\":{\"status\":-4,\"error\":{\"message\":\"@OBJSON_StaleDate@\",\"type\":\"system\"}}}";
    static final String VALIDATION_ERROR_BODY =
            "{\"response\":{\"status\":-4,\"error\":{\"message\":\"Some field is required\",\"type\":\"system\"}}}";
    static final String SUCCESS_BODY = "{\"response\":{\"status\":0,\"data\":[{\"id\":\"1\"}]}}";
    static final String STALE_OBJECT_CODE_JSON = "\"code\":\"STALE_OBJECT\"";

    private StaleObjectTestFixtures() {
    }

    /**
     * Wires {@code response}'s output stream and writer so both capture everything written into
     * a single in-memory buffer, letting a test assert on the result regardless of which of the
     * two the code under test happens to write through.
     *
     * @param response the mock response to wire up
     * @return the buffer that everything written to {@code response} lands in
     * @throws IOException never actually thrown (the mocked stream/writer can't fail), but
     *                      required by {@link HttpServletResponse#getOutputStream()}
     */
    static ByteArrayOutputStream captureResponseOutput(HttpServletResponse response) throws IOException {
        ByteArrayOutputStream realOutput = new ByteArrayOutputStream();
        ServletOutputStream realSos = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // no-op
            }

            @Override
            public void write(int b) {
                realOutput.write(b);
            }
        };
        lenient().when(response.getOutputStream()).thenReturn(realSos);
        PrintWriter realWriter = new PrintWriter(new OutputStreamWriter(realOutput, StandardCharsets.UTF_8), true);
        lenient().when(response.getWriter()).thenReturn(realWriter);
        return realOutput;
    }
}

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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Regression tests for ETP-6074 Finding 8: generic {@code catch (Exception e)} blocks in
 * {@link NotesServlet} must return a fixed, generic message to the client instead of
 * {@link Throwable#getMessage()}, which can leak internal detail (e.g. JSON parser internals,
 * SQL fragments, schema names, file paths).
 */
@RunWith(MockitoJUnitRunner.class)
public class NotesServletErrorTest extends NoteServletTestSupport {

    private static final String MALFORMED_JSON_BODY = "{bad json";
    private static final String GENERIC_ERROR_MESSAGE = "Error processing request";

    /**
     * Sends a malformed JSON body to POST /notes. {@link NotesServlet} reads and parses the body
     * via {@code readRequestBody} before any other validation runs, so the {@link JSONException}
     * thrown by the jettison parser reaches the generic {@code catch (Exception e)} in
     * {@code doPost}. The response must contain only the generic error message, never the
     * parser's internal detail.
     */
    @Test
    public void testCreateNote_MalformedJsonBody_DoesNotLeakParserDetail() throws Exception {
        // Arrange
        stubRequestBody(MALFORMED_JSON_BODY);
        String leakedDetail = captureJettisonParserMessage(MALFORMED_JSON_BODY);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        String body = getResponseContent();
        assertTrue("Response should contain the generic error message", body.contains(GENERIC_ERROR_MESSAGE));
        assertFalse("Response must not leak the JSON parser's internal detail: " + leakedDetail,
                body.contains(leakedDetail));
    }

    /**
     * Independently reproduces the exception the jettison parser throws for the malformed body,
     * so the test does not hardcode an assumption about its exact wording.
     *
     * @param malformedJson
     *            the malformed JSON payload
     * @return the message of the {@link JSONException} thrown while parsing it
     */
    private String captureJettisonParserMessage(String malformedJson) {
        try {
            new JSONObject(malformedJson);
            throw new IllegalStateException("Expected malformed JSON to throw JSONException");
        } catch (JSONException e) {
            return e.getMessage();
        }
    }
}

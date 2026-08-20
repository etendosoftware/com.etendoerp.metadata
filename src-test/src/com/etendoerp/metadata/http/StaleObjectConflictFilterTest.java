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

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.http.StaleObjectTestFixtures.STALE_JSON_BODY;
import static com.etendoerp.metadata.http.StaleObjectTestFixtures.STALE_OBJECT_CODE_JSON;
import static com.etendoerp.metadata.http.StaleObjectTestFixtures.SUCCESS_BODY;
import static com.etendoerp.metadata.http.StaleObjectTestFixtures.VALIDATION_ERROR_BODY;
import static com.etendoerp.metadata.http.StaleObjectTestFixtures.captureResponseOutput;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StaleObjectConflictFilter}.
 *
 * <p>Mirrors {@link ForwarderServletTest}'s stale-object coverage, but exercises the filter
 * directly against a {@link FilterChain} instead of a {@code DataSourceServlet}, since this
 * filter is what actually sees add/update saves in a real deployment (see the class javadoc).</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class StaleObjectConflictFilterTest {

    private static final String OPERATION_TYPE_PARAM = "_operationType";

    private StaleObjectConflictFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private ByteArrayOutputStream realOutput;

    /**
     * Initializes the filter under test and a response whose output stream/writer both capture
     * everything written into {@link #realOutput}.
     *
     * @throws IOException if test setup fails
     */
    @Before
    public void setUp() throws IOException {
        filter = new StaleObjectConflictFilter();
        realOutput = captureResponseOutput(response);
    }

    /**
     * Stubs {@code chain.doFilter} so that, when invoked with whatever response it is given
     * (real or buffered), it writes {@code body} to that response's writer -- simulating what
     * the servlet at the end of the chain does.
     */
    private void stubChainWrite(String body) throws IOException, ServletException {
        doAnswer(invocation -> {
            ServletResponse resp = invocation.getArgument(1);
            resp.getWriter().write(body);
            resp.getWriter().flush();
            return null;
        }).when(chain).doFilter(any(), any());
    }

    /**
     * A PUT (update) whose response body contains the core's stale-object marker must be
     * rewritten as a distinct HTTP 409 with a structured {@code STALE_OBJECT} conflict body.
     *
     * @throws IOException      if an error occurs during test execution
     * @throws ServletException if an error occurs during test execution
     */
    @Test
    public void putWithStaleObjectConflictShouldReturn409() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("PUT");
        stubChainWrite(STALE_JSON_BODY);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.SC_CONFLICT);
        String written = realOutput.toString(StandardCharsets.UTF_8);
        assertTrue(written.contains(STALE_OBJECT_CODE_JSON));
        assertTrue(written.contains("@OBJSON_StaleDate@"));
    }

    /**
     * A POST {@code update} whose response contains the stale-object marker must also be
     * rewritten as HTTP 409, the same as PUT.
     *
     * @throws IOException      if an error occurs during test execution
     * @throws ServletException if an error occurs during test execution
     */
    @Test
    public void postUpdateWithStaleObjectConflictShouldReturn409() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn("update");
        stubChainWrite(STALE_JSON_BODY);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.SC_CONFLICT);
    }

    /**
     * A POST {@code add} (new record) whose response contains the stale-object marker must also
     * be rewritten as HTTP 409 -- same handling as {@code update}.
     *
     * @throws IOException      if an error occurs during test execution
     * @throws ServletException if an error occurs during test execution
     */
    @Test
    public void postAddWithStaleObjectConflictShouldReturn409() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn("add");
        stubChainWrite(STALE_JSON_BODY);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.SC_CONFLICT);
    }

    /**
     * A fetch (read) request is not a save operation -- the filter must not buffer it at all,
     * passing the original, unwrapped response straight down the chain.
     *
     * @throws IOException      if an error occurs during test execution
     * @throws ServletException if an error occurs during test execution
     */
    @Test
    public void postFetchShouldBypassBufferingEntirely() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn("fetch");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.SC_CONFLICT);
    }

    /**
     * A save whose response is a normal validation error (no stale-object marker) must be
     * passed through to the real response completely unchanged -- no regression for any other
     * kind of save error.
     *
     * @throws IOException      if an error occurs during test execution
     * @throws ServletException if an error occurs during test execution
     */
    @Test
    public void putWithValidationErrorShouldPassThroughUnchanged() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("PUT");
        stubChainWrite(VALIDATION_ERROR_BODY);

        filter.doFilter(request, response, chain);

        verify(response, never()).setStatus(HttpStatus.SC_CONFLICT);
        assertEquals(VALIDATION_ERROR_BODY, realOutput.toString(StandardCharsets.UTF_8));
    }

    /**
     * A successful save must be passed through to the real response completely unchanged.
     *
     * @throws IOException      if an error occurs during test execution
     * @throws ServletException if an error occurs during test execution
     */
    @Test
    public void putWithSuccessShouldPassThroughUnchanged() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("PUT");
        stubChainWrite(SUCCESS_BODY);

        filter.doFilter(request, response, chain);

        verify(response, never()).setStatus(HttpStatus.SC_CONFLICT);
        assertEquals(SUCCESS_BODY, realOutput.toString(StandardCharsets.UTF_8));
    }
}

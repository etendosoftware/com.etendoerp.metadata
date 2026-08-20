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

import com.etendoerp.metadata.service.ExtraPropertiesEnricher;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.datasource.DataSourceServlet;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ForwarderServlet}.
 *
 * <p>Verifies that requests are dispatched to the correct {@link DataSourceServlet} method
 * and that POST fetch requests are enriched with {@code _extraProperties} when the entity
 * has FK fields pointing to entities with Color-typed columns.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class ForwarderServletTest {

    private static final String TEST_PATH = "/some/path";
    private static final String ENTITY_PATH = "/ETASK_TaskType";
    private static final String ENTITY_NAME = "ETASK_TaskType";
    private static final String OPERATION_TYPE_PARAM = "_operationType";
    private static final String EXTRA_PROPERTIES_PARAM = "_extraProperties";
    private static final String DISTINCT_PARAM = "_distinct";
    private static final String FETCH_OPERATION  = "fetch";
    private static final String REMOVE_OPERATION = "remove";
    private static final String ADD_OPERATION = "add";
    private static final String UPDATE_OPERATION = "update";
    private static final String COLOR_EXTRA_PROP = "priority.color";
    private static final String STALE_JSON_BODY =
            "{\"response\":{\"status\":-4,\"error\":{\"message\":\"@OBJSON_StaleDate@\",\"type\":\"system\"}}}";
    private static final String STALE_APRM_BODY =
            "{\"response\":{\"status\":-4,\"error\":{\"message\":\"@APRM_StaleDate@\",\"type\":\"system\"}}}";
    private static final String VALIDATION_ERROR_BODY =
            "{\"response\":{\"status\":-4,\"error\":{\"message\":\"Some field is required\",\"type\":\"system\"}}}";
    private static final String SUCCESS_BODY = "{\"response\":{\"status\":0,\"data\":[{\"id\":\"1\"}]}}";
    // The core resolves the "@OBJSON_StaleDate@" placeholder into this AD_Message text before
    // writing the response -- a real conflict response never contains the raw marker (see
    // JsonUtils.convertExceptionToJson -> OBMessageUtils.translateError -> ErrorTextParserPOSTGRE).
    private static final String TRANSLATED_STALE_TEXT = "The record you are saving has already been "
            + "changed by another user or process. Cancel your changes and refresh the data by "
            + "clicking the refresh button.";
    private static final String TRANSLATED_STALE_BODY = "{\"response\":{\"status\":-1,\"error\":{\"message\":\""
            + TRANSLATED_STALE_TEXT + "\",\"type\":\"Error\",\"title\":\"\"},\"totalRows\":0}}";

    private ForwarderServlet forwarderServlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private DataSourceServlet dataSourceServlet;

    private ByteArrayOutputStream realOutput;

    /** Initializes the servlet under test. */
    @Before
    public void setUp() throws IOException {
        forwarderServlet = new ForwarderServlet();
        realOutput = new ByteArrayOutputStream();
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
        // Utils.writeJsonResponse (used to write the 409 conflict body) writes via getWriter(),
        // while BufferedResponseWrapper#flushToRealResponse (the passthrough path) writes via
        // getOutputStream() -- both must land in the same buffer so tests can assert on either path.
        PrintWriter realWriter = new PrintWriter(new OutputStreamWriter(realOutput, StandardCharsets.UTF_8), true);
        lenient().when(response.getWriter()).thenReturn(realWriter);
    }

    /**
     * Stubs {@code dataSourceServlet.doPost}/{@code doPut} so that, when invoked with whatever
     * response it is given (real or buffered), it writes {@code body} to that response's writer
     * -- simulating what the core servlet's {@code writeResult()} does.
     */
    private void stubForwardedWrite(boolean isPost, String body) throws Exception {
        if (isPost) {
            doAnswer(invocation -> {
                HttpServletResponse resp = invocation.getArgument(1);
                resp.getWriter().write(body);
                resp.getWriter().flush();
                return null;
            }).when(dataSourceServlet).doPost(any(), any());
        } else {
            doAnswer(invocation -> {
                HttpServletResponse resp = invocation.getArgument(1);
                resp.getWriter().write(body);
                resp.getWriter().flush();
                return null;
            }).when(dataSourceServlet).doPut(any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // HTTP method dispatch
    // -------------------------------------------------------------------------

    /**
     * GET requests must be forwarded to {@link DataSourceServlet#doGet}.
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processGetShouldDelegateToDataSourceServletDoGet() throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("GET");

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doGet(request, response);
        }
    }

    /**
     * POST requests with an operation type that is not enriched (e.g. {@code "remove"}) must be
     * forwarded to {@link DataSourceServlet#doPost} with the original (unwrapped) request.
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processNonEnrichablePostShouldDelegateToDataSourceServletDoPostUnchanged()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn(REMOVE_OPERATION);

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(request, response);
        }
    }

    /**
     * DELETE requests must be forwarded to {@link DataSourceServlet#doDelete}.
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processDeleteShouldDelegateToDataSourceServletDoDelete() throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("DELETE");

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doDelete(request, response);
        }
    }

    /**
     * PUT requests must be forwarded to {@link DataSourceServlet#doPut}, wrapped in a
     * {@link BufferedResponseWrapper} so the forwarder can inspect the response before it
     * reaches the real client (PUT is always a save/update operation).
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processPutShouldDelegateToDataSourceServletDoPut() throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("PUT");

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPut(eq(request), any(BufferedResponseWrapper.class));
        }
    }

    // -------------------------------------------------------------------------
    // Optimistic-lock (stale object) conflict detection on forwarded saves
    // -------------------------------------------------------------------------

    /**
     * A PUT (update) whose forwarded response body contains the core's stale-object marker must
     * be rewritten as a distinct HTTP 409 with a structured {@code STALE_OBJECT} conflict body,
     * instead of the generic HTTP 200 the core itself would have written.
     */
    @Test
    public void processPutWithStaleObjectConflictShouldReturn409WithStaleObjectCode() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("PUT");
            stubForwardedWrite(false, STALE_JSON_BODY);

            forwarderServlet.process(request, response);

            verify(response).setStatus(HttpStatus.SC_CONFLICT);
            String written = realOutput.toString(StandardCharsets.UTF_8);
            assertTrue(written.contains("\"code\":\"STALE_OBJECT\""));
            assertTrue(written.contains("@OBJSON_StaleDate@"));
        }
    }

    /**
     * The same detection must also apply to the {@code @APRM_StaleDate@} marker used by
     * payment-related action handlers.
     */
    @Test
    public void processPutWithAprmStaleObjectConflictShouldReturn409() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("PUT");
            stubForwardedWrite(false, STALE_APRM_BODY);

            forwarderServlet.process(request, response);

            verify(response).setStatus(HttpStatus.SC_CONFLICT);
            assertTrue(realOutput.toString(StandardCharsets.UTF_8).contains("\"code\":\"STALE_OBJECT\""));
        }
    }

    /**
     * A POST {@code add}/{@code update} whose forwarded response contains the stale-object
     * marker must also be rewritten as HTTP 409, the same as PUT.
     */
    @Test
    public void processPostUpdateWithStaleObjectConflictShouldReturn409() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn(UPDATE_OPERATION);
            stubForwardedWrite(true, STALE_JSON_BODY);

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(any(), any(BufferedResponseWrapper.class));
            verify(response).setStatus(HttpStatus.SC_CONFLICT);
            assertTrue(realOutput.toString(StandardCharsets.UTF_8).contains("\"code\":\"STALE_OBJECT\""));
        }
    }

    /**
     * A POST {@code add} (new record) whose forwarded response contains the stale-object marker
     * must also be rewritten as HTTP 409 -- same handling as {@code update}, since both are
     * save operations that carry the {@code updated} timestamp for optimistic locking.
     */
    @Test
    public void processPostAddWithStaleObjectConflictShouldReturn409() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn(ADD_OPERATION);
            stubForwardedWrite(true, STALE_JSON_BODY);

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(any(), any(BufferedResponseWrapper.class));
            verify(response).setStatus(HttpStatus.SC_CONFLICT);
            assertTrue(realOutput.toString(StandardCharsets.UTF_8).contains("\"code\":\"STALE_OBJECT\""));
        }
    }

    /**
     * A save whose forwarded response carries no raw {@code @OBJSON_StaleDate@} marker -- because
     * the core already resolved it into the current session language's AD_Message text before
     * writing the response, which is what a real deployment actually returns -- must still be
     * detected and rewritten as HTTP 409, by matching against that translated text instead.
     */
    @Test
    public void processPutWithTranslatedStaleObjectConflictShouldReturn409() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class);
                MockedStatic<Utility> utilityMock = mockStatic(Utility.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            stubSessionLanguage(obContextMock, "en_US");
            utilityMock.when(() -> Utility.messageBD(any(), eq("OBJSON_StaleDate"), eq("en_US")))
                    .thenReturn(TRANSLATED_STALE_TEXT);
            utilityMock.when(() -> Utility.messageBD(any(), eq("APRM_StaleDate"), eq("en_US")))
                    .thenReturn("");
            when(request.getMethod()).thenReturn("PUT");
            stubForwardedWrite(false, TRANSLATED_STALE_BODY);

            forwarderServlet.process(request, response);

            verify(response).setStatus(HttpStatus.SC_CONFLICT);
            String written = realOutput.toString(StandardCharsets.UTF_8);
            assertTrue(written.contains("\"code\":\"STALE_OBJECT\""));
            assertTrue(written.contains("@OBJSON_StaleDate@"));
        }
    }

    /**
     * A successful save (no {@code "error"} key in the body) must skip the AD_Message translation
     * lookups entirely -- only failed saves pay that cost.
     */
    @Test
    public void processPutWithSuccessShouldNotQueryMessageTranslation() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<Utility> utilityMock = mockStatic(Utility.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("PUT");
            stubForwardedWrite(false, SUCCESS_BODY);

            forwarderServlet.process(request, response);

            utilityMock.verify(() -> Utility.messageBD(any(), anyString(), anyString()), never());
            verify(response, never()).setStatus(HttpStatus.SC_CONFLICT);
        }
    }

    /**
     * Stubs {@code OBContext.getOBContext().getLanguage().getLanguage()} to return
     * {@code languageCode}.
     */
    private void stubSessionLanguage(MockedStatic<OBContext> obContextMock, String languageCode) {
        OBContext context = mock(OBContext.class);
        Language language = mock(Language.class);
        when(language.getLanguage()).thenReturn(languageCode);
        when(context.getLanguage()).thenReturn(language);
        obContextMock.when(OBContext::getOBContext).thenReturn(context);
    }

    /**
     * A save whose response is a normal validation error (no stale-object marker) must be
     * passed through to the real response completely unchanged -- no regression for any other
     * kind of save error (validation, permissions, DB constraints, etc.).
     */
    @Test
    public void processPutWithValidationErrorShouldPassThroughUnchanged() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("PUT");
            stubForwardedWrite(false, VALIDATION_ERROR_BODY);

            forwarderServlet.process(request, response);

            verify(response, never()).setStatus(HttpStatus.SC_CONFLICT);
            assertEquals(VALIDATION_ERROR_BODY, realOutput.toString(StandardCharsets.UTF_8));
        }
    }

    /**
     * A successful save must be passed through to the real response completely unchanged.
     */
    @Test
    public void processPutWithSuccessShouldPassThroughUnchanged() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("PUT");
            stubForwardedWrite(false, SUCCESS_BODY);

            forwarderServlet.process(request, response);

            verify(response, never()).setStatus(HttpStatus.SC_CONFLICT);
            assertEquals(SUCCESS_BODY, realOutput.toString(StandardCharsets.UTF_8));
        }
    }

    // -------------------------------------------------------------------------
    // _extraProperties enrichment for fetch POST
    // -------------------------------------------------------------------------

    /**
     * A POST fetch request for an entity that has FK fields with Color columns must be
     * forwarded to {@link DataSourceServlet#doPost} with an enriched wrapper that carries
     * {@code _extraProperties}.
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processFetchPostShouldInjectExtraPropertiesIntoWrapper()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<ExtraPropertiesEnricher> enricherMock = mockStatic(ExtraPropertiesEnricher.class)) {

            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            enricherMock.when(() -> ExtraPropertiesEnricher.getExtraProperties(ENTITY_NAME))
                    .thenReturn(COLOR_EXTRA_PROP);

            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn(FETCH_OPERATION);
            when(request.getParameterMap()).thenReturn(Collections.emptyMap());

            ArgumentCaptor<HttpServletRequest> reqCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(reqCaptor.capture(), eq(response));
            assertEquals(COLOR_EXTRA_PROP, reqCaptor.getValue().getParameter(EXTRA_PROPERTIES_PARAM));
        }
    }

    /**
     * When the request already carries {@code _extraProperties}, the enriched value must
     * be appended with a comma separator rather than replacing the existing value.
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processFetchPostShouldAppendToExistingExtraProperties()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<ExtraPropertiesEnricher> enricherMock = mockStatic(ExtraPropertiesEnricher.class)) {

            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            enricherMock.when(() -> ExtraPropertiesEnricher.getExtraProperties(ENTITY_NAME))
                    .thenReturn(COLOR_EXTRA_PROP);

            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn(FETCH_OPERATION);
            when(request.getParameterMap()).thenReturn(
                    Collections.singletonMap(EXTRA_PROPERTIES_PARAM, new String[]{ "existing.prop" }));

            ArgumentCaptor<HttpServletRequest> reqCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(reqCaptor.capture(), eq(response));
            assertEquals("existing.prop," + COLOR_EXTRA_PROP, reqCaptor.getValue().getParameter(EXTRA_PROPERTIES_PARAM));
        }
    }

    /**
     * A POST fetch request that carries {@code _distinct} must be forwarded with the original
     * (unwrapped) request, without {@code _extraProperties}, even when the entity has FK fields
     * with Color columns. A distinct query re-parents the result set to the referenced entity,
     * so color paths built for the grid's own entity would fail to resolve and NPE during
     * serialization.
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processDistinctFetchPostShouldNotInjectExtraProperties()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn(FETCH_OPERATION);
            when(request.getParameter(DISTINCT_PARAM)).thenReturn("priority");

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(request, response);
        }
    }

    /**
     * A POST fetch request for an entity with no Color FK fields must be forwarded with the
     * original request object (no wrapper created).
     *
     * @throws ServletException if servlet processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Test
    public void processFetchPostWithNoColorPropertiesShouldNotWrapRequest()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<ExtraPropertiesEnricher> enricherMock = mockStatic(ExtraPropertiesEnricher.class)) {

            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            enricherMock.when(() -> ExtraPropertiesEnricher.getExtraProperties(ENTITY_NAME))
                    .thenReturn("");

            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter(OPERATION_TYPE_PARAM)).thenReturn(FETCH_OPERATION);

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(request, response);
        }
    }

    // -------------------------------------------------------------------------
    // BaseWebService delegation (doGet/doPost/doPut/doDelete → process)
    // -------------------------------------------------------------------------

    /**
     * {@link ForwarderServlet#doGet} must delegate to {@link ForwarderServlet#process}.
     *
     * @throws Exception if an error occurs during invocation
     */
    @Test
    public void doGetShouldCallProcessMethod() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            ForwarderServlet spyServlet = spy(forwarderServlet);

            spyServlet.doGet(TEST_PATH, request, response);

            verify(spyServlet).process(request, response);
        }
    }

    /**
     * {@link ForwarderServlet#doPost} must delegate to {@link ForwarderServlet#process}.
     *
     * @throws Exception if an error occurs during invocation
     */
    @Test
    public void doPostShouldCallProcessMethod() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<ExtraPropertiesEnricher> enricherMock = mockStatic(ExtraPropertiesEnricher.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            enricherMock.when(() -> ExtraPropertiesEnricher.getExtraProperties(any())).thenReturn("");

            ForwarderServlet spyServlet = spy(forwarderServlet);
            spyServlet.doPost(TEST_PATH, request, response);

            verify(spyServlet).process(request, response);
        }
    }

    /**
     * {@link ForwarderServlet#doPut} must delegate to {@link ForwarderServlet#process}.
     *
     * @throws Exception if an error occurs during invocation
     */
    @Test
    public void doPutShouldCallProcessMethod() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            ForwarderServlet spyServlet = spy(forwarderServlet);

            spyServlet.doPut(TEST_PATH, request, response);

            verify(spyServlet).process(request, response);
        }
    }

    /**
     * {@link ForwarderServlet#doDelete} must delegate to {@link ForwarderServlet#process}.
     *
     * @throws Exception if an error occurs during invocation
     */
    @Test
    public void doDeleteShouldCallProcessMethod() throws Exception {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            ForwarderServlet spyServlet = spy(forwarderServlet);

            spyServlet.doDelete(TEST_PATH, request, response);

            verify(spyServlet).process(request, response);
        }
    }
}

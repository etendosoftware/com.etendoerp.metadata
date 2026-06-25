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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.service.datasource.DataSourceServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
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
    private static final String FETCH_OPERATION  = "fetch";
    private static final String REMOVE_OPERATION = "remove";
    private static final String COLOR_EXTRA_PROP = "priority.color";

    private ForwarderServlet forwarderServlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private DataSourceServlet dataSourceServlet;

    /** Initializes the servlet under test. */
    @Before
    public void setUp() {
        forwarderServlet = new ForwarderServlet();
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
     * PUT requests must be forwarded to {@link DataSourceServlet#doPut}.
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

            verify(dataSourceServlet).doPut(request, response);
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

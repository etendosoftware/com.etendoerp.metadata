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

    private ForwarderServlet forwarderServlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private DataSourceServlet dataSourceServlet;

    @Before
    public void setUp() {
        forwarderServlet = new ForwarderServlet();
    }

    // -------------------------------------------------------------------------
    // HTTP method dispatch
    // -------------------------------------------------------------------------

    /**
     * GET requests must be forwarded to {@link DataSourceServlet#doGet}.
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
     * POST requests that are not fetch operations must be forwarded to {@link DataSourceServlet#doPost}
     * with the original (unwrapped) request.
     */
    @Test
    public void processNonFetchPostShouldDelegateToDataSourceServletDoPostUnchanged()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter("_operationType")).thenReturn("add");

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(request, response);
        }
    }

    /**
     * DELETE requests must be forwarded to {@link DataSourceServlet#doDelete}.
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
     */
    @Test
    public void processFetchPostShouldInjectExtraPropertiesIntoWrapper()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<ExtraPropertiesEnricher> enricherMock = mockStatic(ExtraPropertiesEnricher.class)) {

            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            enricherMock.when(() -> ExtraPropertiesEnricher.getExtraProperties(ENTITY_NAME))
                    .thenReturn("priority.color");

            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter("_operationType")).thenReturn("fetch");
            when(request.getParameterMap()).thenReturn(Collections.emptyMap());

            ArgumentCaptor<HttpServletRequest> reqCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(reqCaptor.capture(), eq(response));
            assertEquals("priority.color", reqCaptor.getValue().getParameter("_extraProperties"));
        }
    }

    /**
     * When the request already carries {@code _extraProperties}, the enriched value must
     * be appended with a comma separator rather than replacing the existing value.
     */
    @Test
    public void processFetchPostShouldAppendToExistingExtraProperties()
            throws ServletException, IOException {
        try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class);
                MockedStatic<ExtraPropertiesEnricher> enricherMock = mockStatic(ExtraPropertiesEnricher.class)) {

            weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
                    .thenReturn(dataSourceServlet);
            enricherMock.when(() -> ExtraPropertiesEnricher.getExtraProperties(ENTITY_NAME))
                    .thenReturn("priority.color");

            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(ENTITY_PATH);
            when(request.getParameter("_operationType")).thenReturn("fetch");
            when(request.getParameterMap()).thenReturn(
                    Collections.singletonMap("_extraProperties", new String[]{ "existing.prop" }));

            ArgumentCaptor<HttpServletRequest> reqCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(reqCaptor.capture(), eq(response));
            assertEquals("existing.prop,priority.color", reqCaptor.getValue().getParameter("_extraProperties"));
        }
    }

    /**
     * A POST fetch request for an entity with no Color FK fields must be forwarded with the
     * original request object (no wrapper created).
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
            when(request.getParameter("_operationType")).thenReturn("fetch");

            forwarderServlet.process(request, response);

            verify(dataSourceServlet).doPost(request, response);
        }
    }

    // -------------------------------------------------------------------------
    // BaseWebService delegation (doGet/doPost/doPut/doDelete → process)
    // -------------------------------------------------------------------------

    /**
     * {@link ForwarderServlet#doGet} must delegate to {@link ForwarderServlet#process}.
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

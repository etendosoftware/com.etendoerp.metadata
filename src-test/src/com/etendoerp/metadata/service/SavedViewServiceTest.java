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

package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.data.EtmetaSavedView;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * Unit tests for {@link SavedViewService}.
 * Tests all CRUD operations (GET, POST, PUT, DELETE), ID extraction, body parsing,
 * and JSON response generation.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
@SuppressWarnings("unchecked")
public class SavedViewServiceTest {

    private static final String VIEW_ID = "view-abc-123";
    private static final String VIEW_NAME = "My Saved View";
    private static final String TAB_ID = "tab-xyz-456";
    private static final String USER_ID = "user-001";
    private static final String CLIENT_ID = "client-001";
    private static final String ORG_ID = "org-001";
    private static final String SAVED_VIEW_PATH_PREFIX = "/saved-views";
    private static final String PATH_WITH_ID = "/saved-views/" + VIEW_ID;
    private static final String PATH_WITH_META_PREFIX = "/com.etendoerp.metadata.meta/saved-views/" + VIEW_ID;

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private OBDal mockOBDal;
    @Mock private OBContext mockOBContext;
    @Mock private OBProvider mockOBProvider;
    @Mock private EtmetaSavedView mockView;
    @Mock private Tab mockTab;
    @Mock private User mockUser;
    @Mock private Client mockClient;
    @Mock private Organization mockOrg;
    @Mock private OBCriteria<EtmetaSavedView> mockCriteria;

    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<OBProvider> obProviderMock;

    private StringWriter responseWriter;
    private SavedViewService service;

    @Before
    public void setUp() throws IOException {
        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseWriter));

        obDalMock = mockStatic(OBDal.class);
        obContextMock = mockStatic(OBContext.class);
        obProviderMock = mockStatic(OBProvider.class);

        obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
        obContextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
        obProviderMock.when(OBProvider::getInstance).thenReturn(mockOBProvider);
        obContextMock.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(inv -> null);
        obContextMock.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);

        // Common view mock for toJSON()
        when(mockView.getId()).thenReturn(VIEW_ID);
        when(mockView.getName()).thenReturn(VIEW_NAME);
        when(mockView.getTab()).thenReturn(mockTab);
        when(mockTab.getId()).thenReturn(TAB_ID);
        when(mockView.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockView.isDefault()).thenReturn(false);
        when(mockView.isActive()).thenReturn(true);
        when(mockView.getFilterclause()).thenReturn(null);
        when(mockView.getGridconfiguration()).thenReturn(null);

        // Common context mock for handlePost/handleGet-list
        when(mockOBContext.getUser()).thenReturn(mockUser);
        when(mockOBContext.getCurrentClient()).thenReturn(mockClient);
        when(mockOBContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockOBDal.get(Client.class, CLIENT_ID)).thenReturn(mockClient);
        when(mockOBDal.get(Organization.class, ORG_ID)).thenReturn(mockOrg);
        when(mockOBDal.get(User.class, USER_ID)).thenReturn(mockUser);

        when(mockRequest.getPathInfo()).thenReturn(PATH_WITH_ID);
        service = new SavedViewService(mockRequest, mockResponse);
    }

    @After
    public void tearDown() {
        if (obDalMock != null) obDalMock.close();
        if (obContextMock != null) obContextMock.close();
        if (obProviderMock != null) obProviderMock.close();
        MetadataService.clear();
    }

    // --- Constructor ---

    @Test
    public void testConstructor() {
        assertNotNull("Service should not be null", service);
        assertSame("Request should be injected", mockRequest, service.getRequest());
        assertSame("Response should be injected", mockResponse, service.getResponse());
        assertTrue("Should be instance of MetadataService", service instanceof MetadataService);
    }

    // --- GET: single item ---

    @Test
    public void testProcess_Get_WithId_Found() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();

        String response = responseWriter.toString();
        assertTrue("Response should contain view ID", response.contains(VIEW_ID));
        assertTrue("Response should contain status 0", response.contains("\"status\":0"));
    }

    @Test(expected = NotFoundException.class)
    public void testProcess_Get_WithId_NotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(null);

        service.process();
    }

    @Test
    public void testProcess_Get_WithId_ResponseContainsFields() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockView.getFilterclause()).thenReturn("some filter");
        when(mockView.getGridconfiguration()).thenReturn("{\"cols\":[]}");

        service.process();

        String response = responseWriter.toString();
        assertTrue("Response should contain tab ID", response.contains(TAB_ID));
        assertTrue("Response should contain user ID", response.contains(USER_ID));
        assertTrue("Response should contain filterclause", response.contains("some filter"));
        assertTrue("Response should contain gridconfiguration", response.contains("{\\\"cols\\\":[]}") ||
            response.contains("gridconfiguration"));
    }

    // --- GET: list ---

    @Test
    public void testProcess_Get_List_NoFilters() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getParameter("tab")).thenReturn(null);
        when(mockRequest.getParameter("isdefault")).thenReturn(null);
        when(mockOBDal.createCriteria(EtmetaSavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        service.process();

        String response = responseWriter.toString();
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain totalRows", response.contains("totalRows"));
        assertTrue("Response should contain startRow", response.contains("startRow"));
    }

    @Test
    public void testProcess_Get_List_WithTabFilter() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter("isdefault")).thenReturn(null);
        when(mockOBDal.createCriteria(EtmetaSavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(mockView));

        service.process();

        String response = responseWriter.toString();
        assertTrue("Response should contain view ID", response.contains(VIEW_ID));
        verify(mockCriteria, atLeast(2)).add(any());
    }

    @Test
    public void testProcess_Get_List_WithIsDefaultFilter() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getParameter("tab")).thenReturn(null);
        when(mockRequest.getParameter("isdefault")).thenReturn("true");
        when(mockOBDal.createCriteria(EtmetaSavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        service.process();

        verify(mockCriteria, atLeast(2)).add(any());
    }

    @Test
    public void testProcess_Get_List_WithBothFilters() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter("isdefault")).thenReturn("false");
        when(mockOBDal.createCriteria(EtmetaSavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        service.process();

        verify(mockCriteria, atLeast(3)).add(any());
    }

    // --- POST ---

    @Test
    public void testProcess_Post_CreatesView() throws IOException {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"name\":\"" + VIEW_NAME + "\",\"tab\":\"" + TAB_ID + "\",\"isdefault\":false}")));
        when(mockOBProvider.get(EtmetaSavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);

        service.process();

        verify(mockOBDal).save(mockView);
        verify(mockOBDal).flush();
        assertTrue("Response should contain view ID", responseWriter.toString().contains(VIEW_ID));
    }

    @Test
    public void testProcess_Post_SetsName() throws IOException {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"name\":\"" + VIEW_NAME + "\"}")));
        when(mockOBProvider.get(EtmetaSavedView.class)).thenReturn(mockView);

        service.process();

        verify(mockView).setName(VIEW_NAME);
    }

    @Test
    public void testProcess_Post_WithFilterclause() throws IOException {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"filterclause\":\"col=1\",\"gridconfiguration\":\"{\\\"cols\\\":[]}\"}") ));
        when(mockOBProvider.get(EtmetaSavedView.class)).thenReturn(mockView);

        service.process();

        verify(mockView).setFilterclause("col=1");
        verify(mockView).setGridconfiguration("{\"cols\":[]}");
    }

    @Test
    public void testProcess_Post_WithNullFilterclause() throws IOException {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"filterclause\":\"null\",\"gridconfiguration\":\"null\"}")));
        when(mockOBProvider.get(EtmetaSavedView.class)).thenReturn(mockView);

        service.process();

        verify(mockView).setFilterclause(null);
        verify(mockView).setGridconfiguration(null);
    }

    // --- PUT ---

    @Test(expected = NotFoundException.class)
    public void testProcess_Put_NullId_ThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn("PUT");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);

        service.process();
    }

    @Test(expected = NotFoundException.class)
    public void testProcess_Put_ViewNotFound_ThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn("PUT");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(null);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{\"name\":\"X\"}")));

        service.process();
    }

    @Test
    public void testProcess_Put_Success() throws IOException {
        when(mockRequest.getMethod()).thenReturn("PUT");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{\"name\":\"Updated\"}")));

        service.process();

        verify(mockOBDal).save(mockView);
        verify(mockOBDal).flush();
        assertTrue("Response should contain view data", responseWriter.toString().contains(VIEW_ID));
    }

    // --- DELETE ---

    @Test(expected = NotFoundException.class)
    public void testProcess_Delete_NullId_ThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn("DELETE");
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);

        service.process();
    }

    @Test(expected = NotFoundException.class)
    public void testProcess_Delete_ViewNotFound_ThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn("DELETE");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(null);

        service.process();
    }

    @Test
    public void testProcess_Delete_Success() throws IOException {
        when(mockRequest.getMethod()).thenReturn("DELETE");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();

        verify(mockOBDal).remove(mockView);
        verify(mockOBDal).flush();
        String response = responseWriter.toString();
        assertTrue("Response should contain status field", response.contains("status"));
    }

    // --- Unsupported method ---

    @Test(expected = MethodNotAllowedException.class)
    public void testProcess_UnknownMethod_ThrowsMethodNotAllowed() throws IOException {
        when(mockRequest.getMethod()).thenReturn("PATCH");

        service.process();
    }

    // --- Admin mode lifecycle ---

    @Test
    public void testProcess_AdminMode_SetAndRestored() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();

        obContextMock.verify(() -> OBContext.setAdminMode(true));
        obContextMock.verify(OBContext::restorePreviousMode);
    }

    // --- extractId (via reflection) ---

    @Test
    public void testExtractId_NullPathInfo_ReturnsNull() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(null);
        assertNull("Null path should return null", invokeExtractId());
    }

    @Test
    public void testExtractId_PathWithId_ReturnsId() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(PATH_WITH_ID);
        assertEquals("Should extract ID from path", VIEW_ID, invokeExtractId());
    }

    @Test
    public void testExtractId_PathWithMetaPrefix_ReturnsId() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(PATH_WITH_META_PREFIX);
        assertEquals("Should extract ID after stripping meta prefix", VIEW_ID, invokeExtractId());
    }

    @Test
    public void testExtractId_PathWithoutId_ReturnsNull() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX);
        assertNull("Path without ID should return null", invokeExtractId());
    }

    @Test
    public void testExtractId_PathWithTrailingSlash_ReturnsNull() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_PATH_PREFIX + "/");
        assertNull("Path with trailing slash only should return null", invokeExtractId());
    }

    // --- readBody (via reflection) ---

    @Test
    public void testReadBody_ValidJson_ReturnsParsed() throws Exception {
        when(mockRequest.getReader()).thenReturn(
            new BufferedReader(new StringReader("{\"name\":\"Test View\"}")));

        Object result = invokeReadBody();
        assertNotNull("Should return parsed JSON object", result);
    }

    @Test(expected = IOException.class)
    public void testReadBody_InvalidJson_ThrowsIOException() throws Exception {
        when(mockRequest.getReader()).thenReturn(
            new BufferedReader(new StringReader("not-valid-json{{")));

        invokeReadBody();
    }

    // --- toJSON: null vs non-null filter/grid fields ---

    @Test
    public void testToJson_NullFilterAndGrid_ResponseContainsNullValues() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockView.getFilterclause()).thenReturn(null);
        when(mockView.getGridconfiguration()).thenReturn(null);

        service.process();

        String response = responseWriter.toString();
        assertTrue("Response should contain filterclause key", response.contains("filterclause"));
        assertTrue("Response should contain gridconfiguration key", response.contains("gridconfiguration"));
    }

    @Test
    public void testToJson_NonNullFilterAndGrid_ResponseContainsValues() throws IOException {
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockOBDal.get(EtmetaSavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockView.getFilterclause()).thenReturn("status='A'");
        when(mockView.getGridconfiguration()).thenReturn("{\"widths\":{}}");

        service.process();

        String response = responseWriter.toString();
        assertTrue("Response should contain filterclause value", response.contains("status='A'"));
    }

    // --- Helpers ---

    private String invokeExtractId() throws Exception {
        Method method = SavedViewService.class.getDeclaredMethod("extractId");
        method.setAccessible(true);
        return (String) method.invoke(service);
    }

    private Object invokeReadBody() throws Exception {
        Method method = SavedViewService.class.getDeclaredMethod("readBody");
        method.setAccessible(true);
        try {
            return method.invoke(service);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new Exception(cause);
        }
    }
}

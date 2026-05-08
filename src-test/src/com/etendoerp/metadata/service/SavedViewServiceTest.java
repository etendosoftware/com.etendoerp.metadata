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

import static com.etendoerp.metadata.utils.Constants.DELETE;
import static com.etendoerp.metadata.utils.Constants.GET;
import static com.etendoerp.metadata.utils.Constants.POST;
import static com.etendoerp.metadata.utils.Constants.PUT;
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

import com.etendoerp.metadata.data.SavedView;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * Unit tests for {@link SavedViewService}.
 * Covers all CRUD HTTP methods (GET, POST, PUT, DELETE), ID extraction from path,
 * request body parsing, JSON response generation, and admin-mode lifecycle.
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
    private static final String SAVED_VIEW_BASE_PATH = "/saved-views";
    private static final String PATH_WITH_ID = "/saved-views/" + VIEW_ID;
    private static final String PATH_WITH_META_PREFIX = "/com.etendoerp.metadata.meta/saved-views/" + VIEW_ID;
    private static final String RESPONSE_CONTAINS_VIEW_ID = "Response should contain view ID";
    private static final String ISDEFAULT_PARAM = "isdefault";
    private static final String A_STATUS = "status='A'";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private OBDal mockOBDal;
    @Mock private OBContext mockOBContext;
    @Mock private OBProvider mockOBProvider;
    @Mock private SavedView mockView;
    @Mock private Tab mockTab;
    @Mock private User mockUser;
    @Mock private Client mockClient;
    @Mock private Organization mockOrg;
    @Mock private OBCriteria<SavedView> mockCriteria;

    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<OBProvider> obProviderMock;

    private StringWriter responseWriter;
    private SavedViewService service;

    /**
     * Initialises static mocks, common stub chains, and the service under test
     * before each test method.
     *
     * @throws IOException if the response writer cannot be configured
     */
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

    /**
     * Closes all static mocks and clears thread-local state after each test.
     */
    @After
    public void tearDown() {
        if (obDalMock != null) obDalMock.close();
        if (obContextMock != null) obContextMock.close();
        if (obProviderMock != null) obProviderMock.close();
        MetadataService.clear();
    }

    // --- Constructor ---

    /**
     * Verifies that the service is constructed correctly and exposes the injected
     * request and response objects.
     */
    @Test
    public void testConstructor() {
        assertNotNull("Service should not be null", service);
        assertSame("Request should be injected", mockRequest, service.getRequest());
        assertSame("Response should be injected", mockResponse, service.getResponse());
        assertTrue("Should be instance of MetadataService", service instanceof MetadataService);
    }

    // --- GET with ID ---

    /**
     * Verifies that a GET request with a valid ID returns the view as JSON.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessGetWithIdFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();

        String response = responseWriter.toString();
        assertTrue(RESPONSE_CONTAINS_VIEW_ID, response.contains(VIEW_ID));
        assertTrue("Response should contain status 0", response.contains("\"status\":0"));
    }

    /**
     * Verifies that a GET with a non-existent ID throws NotFoundException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testProcessGetWithIdNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(null);

        service.process();
    }

    /**
     * Verifies that GET by ID includes all entity fields in the JSON response.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessGetWithIdResponseContainsAllFields() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockView.getFilterclause()).thenReturn(A_STATUS);
        when(mockView.getGridconfiguration()).thenReturn("{\"widths\":{}}");

        service.process();

        String response = responseWriter.toString();
        assertTrue(RESPONSE_CONTAINS_VIEW_ID, response.contains(VIEW_ID));
        assertTrue("Response should contain tab ID", response.contains(TAB_ID));
        assertTrue("Response should contain filterclause value", response.contains(A_STATUS));
    }

    // --- GET list ---

    /**
     * Verifies that a GET request without an ID returns an empty list response.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessGetListNoFilters() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(null);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn(null);
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        service.process();

        String response = responseWriter.toString();
        assertTrue("Response should contain totalRows", response.contains("totalRows"));
        assertTrue("Response should contain startRow", response.contains("startRow"));
    }

    /**
     * Verifies that a GET list request filtered by tab ID applies the tab restriction.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessGetListWithTabFilter() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn(null);
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.singletonList(mockView));

        service.process();

        String response = responseWriter.toString();
        assertTrue(RESPONSE_CONTAINS_VIEW_ID, response.contains(VIEW_ID));
        verify(mockCriteria, atLeast(2)).add(any());
    }

    /**
     * Verifies that a GET list request filtered by isDefault applies the boolean restriction.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessGetListWithIsDefaultFilter() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(null);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("true");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        service.process();

        verify(mockCriteria, atLeast(2)).add(any());
    }

    /**
     * Verifies that a GET list request with both tab and isDefault filters adds all restrictions.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessGetListWithBothFilters() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getParameter("tab")).thenReturn(TAB_ID);
        when(mockRequest.getParameter(ISDEFAULT_PARAM)).thenReturn("false");
        when(mockOBDal.createCriteria(SavedView.class)).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(Collections.emptyList());

        service.process();

        verify(mockCriteria, atLeast(3)).add(any());
    }

    // --- POST ---

    /**
     * Verifies that a POST request creates a new view and persists it.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessPostCreatesView() throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"name\":\"" + VIEW_NAME + "\",\"tab\":\"" + TAB_ID + "\",\"isdefault\":false}")));
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);

        service.process();

        verify(mockOBDal).save(mockView);
        verify(mockOBDal).flush();
        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
    }

    /**
     * Verifies that a POST request applies the name field to the new view.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessPostSetsName() throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"name\":\"" + VIEW_NAME + "\"}")));
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);

        service.process();

        verify(mockView).setName(VIEW_NAME);
    }

    /**
     * Verifies that a POST request with filterclause and gridconfiguration sets those fields.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessPostWithFilterclauseAndGrid() throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"filterclause\":\"col=1\",\"gridconfiguration\":\"{\\\"cols\\\":[]}\"}") ));
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);

        service.process();

        verify(mockView).setFilterclause("col=1");
        verify(mockView).setGridconfiguration("{\"cols\":[]}");
    }

    /**
     * Verifies that a POST body with filterclause and gridconfiguration set to the string
     * "null" stores null in those fields.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessPostWithNullLiteralFilterAndGrid() throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
            new StringReader("{\"filterclause\":\"null\",\"gridconfiguration\":\"null\"}")));
        when(mockOBProvider.get(SavedView.class)).thenReturn(mockView);

        service.process();

        verify(mockView).setFilterclause(null);
        verify(mockView).setGridconfiguration(null);
    }

    // --- PUT ---

    /**
     * Verifies that a PUT request without an ID throws NotFoundException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testProcessPutNullIdThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn(PUT);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);

        service.process();
    }

    /**
     * Verifies that a PUT request for a non-existent view throws NotFoundException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testProcessPutViewNotFoundThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn(PUT);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(null);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{\"name\":\"X\"}")));

        service.process();
    }

    /**
     * Verifies that a PUT request for an existing view updates and persists it.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessPutSuccess() throws IOException {
        when(mockRequest.getMethod()).thenReturn(PUT);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{\"name\":\"Updated\"}")));

        service.process();

        verify(mockOBDal).save(mockView);
        verify(mockOBDal).flush();
        assertTrue(RESPONSE_CONTAINS_VIEW_ID, responseWriter.toString().contains(VIEW_ID));
    }

    // --- DELETE ---

    /**
     * Verifies that a DELETE request without an ID throws NotFoundException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testProcessDeleteNullIdThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);

        service.process();
    }

    /**
     * Verifies that a DELETE request for a non-existent view throws NotFoundException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testProcessDeleteViewNotFoundThrowsNotFound() throws IOException {
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(null);

        service.process();
    }

    /**
     * Verifies that a DELETE request for an existing view removes it and returns status 0.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessDeleteSuccess() throws IOException {
        when(mockRequest.getMethod()).thenReturn(DELETE);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();

        verify(mockOBDal).remove(mockView);
        verify(mockOBDal).flush();
        assertTrue("Response should contain status field", responseWriter.toString().contains("status"));
    }

    // --- Unsupported method ---

    /**
     * Verifies that an unsupported HTTP method throws MethodNotAllowedException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = MethodNotAllowedException.class)
    public void testProcessUnsupportedMethodThrowsMethodNotAllowed() throws IOException {
        when(mockRequest.getMethod()).thenReturn("PATCH");

        service.process();
    }

    // --- Admin mode lifecycle ---

    /**
     * Verifies that process() enables admin mode and restores it in the finally block.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessAdminModeSetAndRestored() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);

        service.process();

        obContextMock.verify(() -> OBContext.setAdminMode(true));
        obContextMock.verify(OBContext::restorePreviousMode);
    }

    // --- extractId ---

    /**
     * Verifies that extractId returns null when pathInfo is null.
     */
    @Test
    public void testExtractIdNullPathReturnsNull() {
        when(mockRequest.getPathInfo()).thenReturn(null);
        assertNull("Null path should return null", invokeExtractId());
    }

    /**
     * Verifies that extractId extracts the ID from a plain saved-views path.
     */
    @Test
    public void testExtractIdWithIdReturnsId() {
        when(mockRequest.getPathInfo()).thenReturn(PATH_WITH_ID);
        assertEquals("Should extract ID from path", VIEW_ID, invokeExtractId());
    }

    /**
     * Verifies that extractId strips the meta prefix before extracting the ID.
     */
    @Test
    public void testExtractIdWithMetaPrefixReturnsId() {
        when(mockRequest.getPathInfo()).thenReturn(PATH_WITH_META_PREFIX);
        assertEquals("Should extract ID after stripping meta prefix", VIEW_ID, invokeExtractId());
    }

    /**
     * Verifies that extractId returns null when the path contains no ID segment.
     */
    @Test
    public void testExtractIdBasePathReturnsNull() {
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH);
        assertNull("Path without ID should return null", invokeExtractId());
    }

    /**
     * Verifies that extractId returns null when the remainder is only a trailing slash.
     */
    @Test
    public void testExtractIdTrailingSlashReturnsNull() {
        when(mockRequest.getPathInfo()).thenReturn(SAVED_VIEW_BASE_PATH + "/");
        assertNull("Trailing slash path should return null", invokeExtractId());
    }

    // --- readBody ---

    /**
     * Verifies that readBody correctly parses a valid JSON request body.
     *
     * @throws IOException if the body contains invalid JSON
     */
    @Test
    public void testReadBodyValidJsonReturnsParsed() throws IOException {
        when(mockRequest.getReader()).thenReturn(
            new BufferedReader(new StringReader("{\"name\":\"Test View\"}")));

        Object result = invokeReadBody();
        assertNotNull("Should return parsed JSON object", result);
    }

    /**
     * Verifies that readBody throws IOException when the body is not valid JSON.
     *
     * @throws IOException expected for invalid JSON input
     */
    @Test(expected = IOException.class)
    public void testReadBodyInvalidJsonThrowsIOException() throws IOException {
        when(mockRequest.getReader()).thenReturn(
            new BufferedReader(new StringReader("not-valid-json{{")));

        invokeReadBody();
    }

    // --- toJSON: null vs non-null filter/grid ---

    /**
     * Verifies that toJSON produces JSON null values when filterclause and
     * gridconfiguration are null.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testToJsonNullFilterAndGridProducesNullValues() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockView.getFilterclause()).thenReturn(null);
        when(mockView.getGridconfiguration()).thenReturn(null);

        service.process();

        String response = responseWriter.toString();
        assertTrue("Response should contain filterclause key", response.contains("filterclause"));
        assertTrue("Response should contain gridconfiguration key", response.contains("gridconfiguration"));
    }

    /**
     * Verifies that toJSON includes the filterclause value when it is not null.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testToJsonNonNullFilterIncludedInResponse() throws IOException {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SavedView.class, VIEW_ID)).thenReturn(mockView);
        when(mockView.getFilterclause()).thenReturn(A_STATUS);
        when(mockView.getGridconfiguration()).thenReturn("{\"widths\":{}}");

        service.process();

        assertTrue("Response should contain filterclause value",
            responseWriter.toString().contains(A_STATUS));
    }

    // --- Helpers ---

    /**
     * Invokes the private {@code extractId} method via reflection and returns its result.
     *
     * @return the extracted ID string, or null if no ID is present in the path
     */
    private String invokeExtractId() {
        try {
            Method method = SavedViewService.class.getDeclaredMethod("extractId");
            method.setAccessible(true);
            return (String) method.invoke(service);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new InternalServerException("Unexpected reflection cause: " + e.getMessage());
        } catch (ReflectiveOperationException e) {
            throw new InternalServerException("Reflection failed: " + e.getMessage());
        }
    }

    /**
     * Invokes the private {@code readBody} method via reflection and returns its result.
     *
     * @return the parsed JSONObject
     * @throws IOException if the request body contains invalid JSON
     */
    private Object invokeReadBody() throws IOException {
        try {
            Method method = SavedViewService.class.getDeclaredMethod("readBody");
            method.setAccessible(true);
            return method.invoke(service);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new InternalServerException("Unexpected reflection cause: " + e.getMessage());
        } catch (ReflectiveOperationException e) {
            throw new InternalServerException("Reflection failed: " + e.getMessage());
        }
    }
}

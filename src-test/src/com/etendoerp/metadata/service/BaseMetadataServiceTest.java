package com.etendoerp.metadata.service;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.test.base.OBBaseTest;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Base test class for MetadataService tests.
 * <p>
 * This class provides common setup and teardown functionality for all metadata service tests,
 * reducing code duplication and ensuring consistent test environment across all service test classes.
 * <p>
 * Subclasses should override {@link #getServicePath()} to provide the specific path for their service.
 *
 * @author Generated Test
 */
public abstract class BaseMetadataServiceTest extends OBBaseTest {

    protected HttpServletRequest mockRequest;
    protected HttpServletResponse mockResponse;
    protected StringWriter responseWriter;

    /**
     * Returns the service path for the specific service being tested.
     * This method must be implemented by subclasses to provide the correct path.
     *
     * @return the service path (e.g., "/meta/message", "/meta/menu", etc.)
     */
    protected abstract String getServicePath();

    /**
     * Sets up the test environment before each test method execution.
     * <p>
     * Initializes mock objects for HTTP request and response, configures the response writer
     * for capturing output, and sets up the necessary OBContext for database operations.
     * Also configures mock request paths using the service-specific path.
     */
    @Before
    public void setUp() throws Exception {
        setTestUserContext();

        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(responseWriter);
        lenient().when(mockResponse.getWriter()).thenReturn(printWriter);
        lenient().when(mockRequest.getMethod()).thenReturn("GET");
        lenient().when(mockRequest.getPathInfo()).thenReturn(getServicePath());
    }

    /**
     * Cleans up the test environment after each test method execution.
     * <p>
     * Restores the previous OBContext mode and clears any thread-local variables
     * to prevent memory leaks and ensure proper test isolation between test methods.
     */
    @After
    public void tearDown() {
        try {
            if (OBContext.getOBContext() != null) {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            // Ignore context restoration errors during cleanup
        }
        MetadataService.clear();
    }

    /**
     * Helper method to create an error response mock that throws IOException.
     *
     * @param exceptionMessage the exception message to throw
     * @return a mocked HttpServletResponse that throws IOException
     * @throws IOException when getWriter() is called
     */
    protected HttpServletResponse createErrorResponseMock(String exceptionMessage) throws IOException {
        HttpServletResponse errorResponse = mock(HttpServletResponse.class);
        when(errorResponse.getWriter()).thenThrow(new IOException(exceptionMessage));
        return errorResponse;
    }

    /**
     * Parses a JSON string and fails the test if it is not valid JSON.
     *
     * @param content the JSON string to parse
     * @return the parsed JSONObject
     */
    protected JSONObject parseJsonResponse(String content) {
        try {
            return new JSONObject(content);
        } catch (Exception e) {
            fail(MetadataTestConstants.INVALID_JSON_MSG + e.getMessage());
            return null;
        }
    }

    /**
     * Carries a Tab and one of its records for DB-dependent tests.
     * Returned by {@link #findFirstTabWithRecord()}.
     */
    protected static class TabRecordContext {
        /** The resolved {@link Tab} instance. */
        public final Tab tab;
        /** The first {@link BaseOBObject} record found for the tab's entity. */
        public final BaseOBObject dataRecord;

        TabRecordContext(Tab tab, BaseOBObject dataRecord) {
            this.tab        = tab;
            this.dataRecord = dataRecord;
        }
    }

    /**
     * Returns the first Tab that has a mapped entity with at least one record, or
     * {@code null} if the DB contains insufficient data (test should be skipped).
     */
    @SuppressWarnings("unchecked")
    protected TabRecordContext findFirstTabWithRecord() {
        List<Tab> tabs = OBDal.getInstance().createCriteria(Tab.class).setMaxResults(1).list();
        if (tabs.isEmpty()) return null;
        Tab tab = tabs.get(0);
        if (tab.getTable() == null) return null;
        Entity entity = ModelProvider.getInstance()
                .getEntityByTableName(tab.getTable().getDBTableName());
        if (entity == null) return null;
        List<BaseOBObject> records = OBDal.getInstance().getSession()
                .createQuery("from " + entity.getName()).setMaxResults(1).list();
        if (records.isEmpty()) return null;
        return new TabRecordContext(tab, records.get(0));
    }
}
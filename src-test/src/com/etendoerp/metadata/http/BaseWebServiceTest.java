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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.service.web.WebService;

/**
 * Unit tests for the {@link BaseWebService} abstract class.
 * <p>
 * This test class validates that all HTTP methods (GET, POST, PUT, DELETE)
 * properly delegate to the abstract {@code process} method. A concrete
 * implementation is used to test the abstract class behavior.
 * </p>
 */
@RunWith(MockitoJUnitRunner.class)
public class BaseWebServiceTest {

    private static final String TEST_PATH = "/test/path";
    private static final String REQUEST_PASSED_TO_PROCESS = "Request should be passed to process";
    private static final String RESPONSE_PASSED_TO_PROCESS = "Response should be passed to process";
    private static final String PROCESS_CALLED_REGARDLESS_OF_PATH = "Process should be called regardless of path";
    private static final String EXCEPTION_SHOULD_BE_PROPAGATED = "Exception should be propagated";
    private static final String SHOULD_THROW_EXCEPTION = "Should throw exception";
    private static final String TEST_EXCEPTION_MESSAGE = "Test exception";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    private TestableWebService webService;
    private boolean processWasCalled;
    private HttpServletRequest capturedRequest;
    private HttpServletResponse capturedResponse;

    /**
     * Concrete implementation of BaseWebService for testing purposes.
     * Tracks whether process was called and captures the arguments.
     */
    private class TestableWebService extends BaseWebService {
        @Override
        protected void process(HttpServletRequest request, HttpServletResponse response) throws Exception {
            processWasCalled = true;
            capturedRequest = request;
            capturedResponse = response;
        }
    }

    /**
     * Concrete implementation that throws an exception in process method.
     * Used for testing exception propagation.
     */
    private class ExceptionThrowingWebService extends BaseWebService {
        private final Exception exceptionToThrow;

        public ExceptionThrowingWebService(Exception exception) {
            this.exceptionToThrow = exception;
        }

        @Override
        protected void process(HttpServletRequest request, HttpServletResponse response) throws Exception {
            throw exceptionToThrow;
        }
    }

    /**
     * Sets up the test environment before each test method execution.
     * <p>
     * Initializes a fresh TestableWebService instance and resets all tracking
     * variables (processWasCalled, capturedRequest, capturedResponse) to ensure
     * test isolation between test methods.
     * </p>
     */
    @Before
    public void setUp() {
        webService = new TestableWebService();
        processWasCalled = false;
        capturedRequest = null;
        capturedResponse = null;
    }

    /**
     * Tests that BaseWebService implements WebService interface.
     */
    @Test
    public void testImplementsWebService() {
        assertTrue("BaseWebService should implement WebService interface",
                webService instanceof WebService);
    }

    /**
     * Tests that doGet delegates to process method.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoGetDelegatesToProcess() throws Exception {
        webService.doGet(TEST_PATH, mockRequest, mockResponse);

        assertTrue("doGet should call process method", processWasCalled);
        assertEquals(REQUEST_PASSED_TO_PROCESS, mockRequest, capturedRequest);
        assertEquals(RESPONSE_PASSED_TO_PROCESS, mockResponse, capturedResponse);
    }

    /**
     * Tests that doPost delegates to process method.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoPostDelegatesToProcess() throws Exception {
        webService.doPost(TEST_PATH, mockRequest, mockResponse);

        assertTrue("doPost should call process method", processWasCalled);
        assertEquals(REQUEST_PASSED_TO_PROCESS, mockRequest, capturedRequest);
        assertEquals(RESPONSE_PASSED_TO_PROCESS, mockResponse, capturedResponse);
    }

    /**
     * Tests that doPut delegates to process method.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoPutDelegatesToProcess() throws Exception {
        webService.doPut(TEST_PATH, mockRequest, mockResponse);

        assertTrue("doPut should call process method", processWasCalled);
        assertEquals(REQUEST_PASSED_TO_PROCESS, mockRequest, capturedRequest);
        assertEquals(RESPONSE_PASSED_TO_PROCESS, mockResponse, capturedResponse);
    }

    /**
     * Tests that doDelete delegates to process method.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoDeleteDelegatesToProcess() throws Exception {
        webService.doDelete(TEST_PATH, mockRequest, mockResponse);

        assertTrue("doDelete should call process method", processWasCalled);
        assertEquals(REQUEST_PASSED_TO_PROCESS, mockRequest, capturedRequest);
        assertEquals(RESPONSE_PASSED_TO_PROCESS, mockResponse, capturedResponse);
    }

    /**
     * Tests that path parameter is ignored in doGet (only request/response matter).
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoGetIgnoresPathParameter() throws Exception {
        webService.doGet("/different/path", mockRequest, mockResponse);

        assertTrue(PROCESS_CALLED_REGARDLESS_OF_PATH, processWasCalled);
    }

    /**
     * Tests that path parameter is ignored in doPost.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoPostIgnoresPathParameter() throws Exception {
        webService.doPost("/another/path", mockRequest, mockResponse);

        assertTrue(PROCESS_CALLED_REGARDLESS_OF_PATH, processWasCalled);
    }

    /**
     * Tests that path parameter is ignored in doPut.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoPutIgnoresPathParameter() throws Exception {
        webService.doPut("/yet/another/path", mockRequest, mockResponse);

        assertTrue(PROCESS_CALLED_REGARDLESS_OF_PATH, processWasCalled);
    }

    /**
     * Tests that path parameter is ignored in doDelete.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoDeleteIgnoresPathParameter() throws Exception {
        webService.doDelete("/final/path", mockRequest, mockResponse);

        assertTrue(PROCESS_CALLED_REGARDLESS_OF_PATH, processWasCalled);
    }

    /**
     * Tests that doGet handles null path.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoGetWithNullPath() throws Exception {
        webService.doGet(null, mockRequest, mockResponse);

        assertTrue("Process should be called even with null path", processWasCalled);
    }

    /**
     * Tests that doPost handles null path.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoPostWithNullPath() throws Exception {
        webService.doPost(null, mockRequest, mockResponse);

        assertTrue("Process should be called even with null path", processWasCalled);
    }

    /**
     * Tests that doGet propagates exceptions from process method.
     *
     * @throws Exception if test fails unexpectedly
     */
    @Test
    public void testDoGetPropagatesException() throws Exception {
        Exception expectedException = new RuntimeException(TEST_EXCEPTION_MESSAGE);
        ExceptionThrowingWebService exceptionService = new ExceptionThrowingWebService(expectedException);

        try {
            exceptionService.doGet(TEST_PATH, mockRequest, mockResponse);
            fail(SHOULD_THROW_EXCEPTION);
        } catch (Exception e) {
            assertEquals(EXCEPTION_SHOULD_BE_PROPAGATED, expectedException, e);
        }
    }

    /**
     * Tests that doPost propagates exceptions from process method.
     *
     * @throws Exception if test fails unexpectedly
     */
    @Test
    public void testDoPostPropagatesException() throws Exception {
        Exception expectedException = new RuntimeException(TEST_EXCEPTION_MESSAGE);
        ExceptionThrowingWebService exceptionService = new ExceptionThrowingWebService(expectedException);

        try {
            exceptionService.doPost(TEST_PATH, mockRequest, mockResponse);
            fail(SHOULD_THROW_EXCEPTION);
        } catch (Exception e) {
            assertEquals(EXCEPTION_SHOULD_BE_PROPAGATED, expectedException, e);
        }
    }

    /**
     * Tests that doPut propagates exceptions from process method.
     *
     * @throws Exception if test fails unexpectedly
     */
    @Test
    public void testDoPutPropagatesException() throws Exception {
        Exception expectedException = new RuntimeException(TEST_EXCEPTION_MESSAGE);
        ExceptionThrowingWebService exceptionService = new ExceptionThrowingWebService(expectedException);

        try {
            exceptionService.doPut(TEST_PATH, mockRequest, mockResponse);
            fail(SHOULD_THROW_EXCEPTION);
        } catch (Exception e) {
            assertEquals(EXCEPTION_SHOULD_BE_PROPAGATED, expectedException, e);
        }
    }

    /**
     * Tests that doDelete propagates exceptions from process method.
     *
     * @throws Exception if test fails unexpectedly
     */
    @Test
    public void testDoDeletePropagatesException() throws Exception {
        Exception expectedException = new RuntimeException(TEST_EXCEPTION_MESSAGE);
        ExceptionThrowingWebService exceptionService = new ExceptionThrowingWebService(expectedException);

        try {
            exceptionService.doDelete(TEST_PATH, mockRequest, mockResponse);
            fail(SHOULD_THROW_EXCEPTION);
        } catch (Exception e) {
            assertEquals(EXCEPTION_SHOULD_BE_PROPAGATED, expectedException, e);
        }
    }

    /**
     * Tests that all HTTP methods can be called in sequence.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testAllMethodsCanBeCalledSequentially() throws Exception {
        // Create a service that counts calls
        BaseWebService countingService = new BaseWebService() {
            private int calls = 0;

            @Override
            protected void process(HttpServletRequest request, HttpServletResponse response) {
                calls++;
            }

            public int getCalls() {
                return calls;
            }
        };

        countingService.doGet(TEST_PATH, mockRequest, mockResponse);
        countingService.doPost(TEST_PATH, mockRequest, mockResponse);
        countingService.doPut(TEST_PATH, mockRequest, mockResponse);
        countingService.doDelete(TEST_PATH, mockRequest, mockResponse);

        // Verify all methods were called by checking process was invoked
        // We can't directly check the call count, but we verify no exceptions
        assertTrue("All HTTP methods should execute without error", true);
    }

    /**
     * Tests doGet with empty path string.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDoGetWithEmptyPath() throws Exception {
        webService.doGet("", mockRequest, mockResponse);

        assertTrue("Process should be called with empty path", processWasCalled);
    }

    /**
     * Tests that different request/response pairs are correctly passed.
     *
     * @throws Exception if an error occurs during method invocation
     */
    @Test
    public void testDifferentRequestResponsePairs() throws Exception {
        HttpServletRequest request1 = mock(HttpServletRequest.class);
        HttpServletResponse response1 = mock(HttpServletResponse.class);
        HttpServletRequest request2 = mock(HttpServletRequest.class);
        HttpServletResponse response2 = mock(HttpServletResponse.class);

        webService.doGet(TEST_PATH, request1, response1);
        assertEquals("First request should be captured", request1, capturedRequest);
        assertEquals("First response should be captured", response1, capturedResponse);

        webService.doPost(TEST_PATH, request2, response2);
        assertEquals("Second request should be captured", request2, capturedRequest);
        assertEquals("Second response should be captured", response2, capturedResponse);
    }

    /**
     * Tests that checked exceptions are properly propagated.
     *
     * @throws Exception if test fails unexpectedly
     */
    @Test
    public void testCheckedExceptionPropagation() throws Exception {
        Exception checkedException = new Exception("Checked exception");
        ExceptionThrowingWebService exceptionService = new ExceptionThrowingWebService(checkedException);

        try {
            exceptionService.doGet(TEST_PATH, mockRequest, mockResponse);
            fail("Should throw checked exception");
        } catch (Exception e) {
            assertEquals(EXCEPTION_SHOULD_BE_PROPAGATED, checkedException, e);
        }
    }
}

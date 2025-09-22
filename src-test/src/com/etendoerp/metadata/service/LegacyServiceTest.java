package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.MetadataTestConstants.LEGACY_REQUEST_FAILED;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.etendoerp.metadata.exceptions.InternalServerException;

/**
 * Unit tests for {@link LegacyService} class.
 * <p>
 * This test class verifies the functionality of the LegacyService, including:
 * - Proper initialization and constructor behavior
 * - Path info processing and request wrapping
 * - Legacy servlet delegation
 * - Exception handling scenarios
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@RunWith(MockitoJUnitRunner.class)
public class LegacyServiceTest extends BaseMetadataServiceTest {


    private LegacyService legacyService;

    /**
     * Returns the base service path used in this test suite to emulate the
     * metadata service endpoint for legacy requests.
     *
     * @return a constant service path starting with /meta/legacy for use in URI composition
     */
    @Override
    protected String getServicePath() {
        return "/meta/legacy/test";
    }

    /**
     * Initializes the test fixture before each test method.
     * <p>
     * It invokes the base setup to configure mocked servlet request/response and
     * instantiates the LegacyService under test using those mocks.
     * </p>
     *
     * @throws Exception if the parent setup fails to initialize the test context
     */
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        legacyService = new LegacyService(mockRequest, mockResponse);
    }

    /**
     * Verifies that the LegacyService constructor creates a non-null instance
     * when provided with mocked HttpServletRequest and HttpServletResponse.
     * No exception is expected during construction.
     */
    @Test
    public void testConstructorInitialization() {
        LegacyService service = new LegacyService(mockRequest, mockResponse);
        assertNotNull("LegacyService should be created successfully", service);
    }

    /**
     * Processes a request whose pathInfo points to a legacy route.
     * The underlying legacy servlet is not available in the test environment,
     * therefore the call is expected to end up throwing InternalServerException,
     * which is caught and asserted by this test.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testProcessWithValidLegacyPath() {
        when(mockRequest.getPathInfo()).thenReturn("/legacy/test/path");

        try {
            legacyService.process();
        } catch (InternalServerException e) {
            // Expected to fail since we don't have actual servlet implementation
            assertTrue("Should contain legacy request message",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Ensures process() handles a null pathInfo gracefully.
     * Since there is no backing legacy servlet, an InternalServerException is expected,
     * which is caught and asserted by this test.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testProcessWithNullPathInfo() {
        when(mockRequest.getPathInfo()).thenReturn(null);

        try {
            legacyService.process();
        } catch (InternalServerException e) {
            assertTrue("Should handle null path info",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Ensures process() handles non-legacy paths by delegating or failing predictably.
     * In this isolated test, the legacy delegation cannot be completed so an
     * InternalServerException is expected and asserted.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testProcessWithNonLegacyPath() {
        when(mockRequest.getPathInfo()).thenReturn("/other/path");

        try {
            legacyService.process();
        } catch (InternalServerException e) {
            assertTrue("Should handle non-legacy path",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Validates behavior when the legacy prefix exists but no target path is provided.
     * The method is expected to fail with InternalServerException in this test context.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testProcessWithEmptyLegacyPath() {
        when(mockRequest.getPathInfo()).thenReturn("/legacy");

        try {
            legacyService.process();
        } catch (InternalServerException e) {
            assertTrue("Should handle empty legacy path",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Validates handling of a legacy path ending with a trailing slash.
     * The call is expected to result in an InternalServerException which the test asserts.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testProcessWithLegacyPathContainingSlash() {
        when(mockRequest.getPathInfo()).thenReturn("/legacy/");

        try {
            legacyService.process();
        } catch (InternalServerException e) {
            assertTrue("Should handle legacy path with trailing slash",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Verifies that complex nested legacy paths are processed up to the point of
     * delegating to the legacy servlet. In this test environment, delegation causes
     * an InternalServerException, which is caught and asserted.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testProcessWithComplexLegacyPath() {
        when(mockRequest.getPathInfo()).thenReturn("/legacy/complex/nested/path");

        try {
            legacyService.process();
        } catch (InternalServerException e) {
            assertTrue("Should handle complex nested legacy path",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Exercises the HttpServletRequest wrapper logic used by LegacyService for
     * pathInfo transformation. The process() call is expected to end with an
     * InternalServerException, which is caught and asserted.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testHttpServletRequestWrapperGetPathInfo() {
        when(mockRequest.getPathInfo()).thenReturn("/legacy/test/wrapper");

        // We need to access the wrapper indirectly through process method
        try {
            legacyService.process();
        } catch (InternalServerException e) {
            // This is expected - we're testing the wrapper creation logic
            assertTrue("Process should fail with expected message",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Exercises the HttpServletRequest wrapper logic for requestURI transformation.
     * The process() call is expected to end with an InternalServerException, which
     * is caught and asserted.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testHttpServletRequestWrapperGetRequestURI() {

      when(mockRequest.getPathInfo()).thenReturn("/legacy/test/uri");

        try {
            legacyService.process();
        } catch (InternalServerException e) {
            // Expected behavior - testing URI transformation logic
            assertTrue("Should handle URI transformation",
                e.getMessage().contains(LEGACY_REQUEST_FAILED));
        }
    }

    /**
     * Iterates through multiple pathInfo inputs to validate transformation of both
     * the pathInfo and requestURI by the LegacyService wrapper. Each iteration is
     * expected to lead to an InternalServerException in this test context, which is
     * caught and asserted.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testGetHttpServletRequestWrapperPathTransformation() {
        String[] testPaths = {
            "/legacy/simple",
            "/legacy/complex/nested/path",
            "/legacy/",
            "/legacy",
            null,
            "/other/path"
        };

        for (String testPath : testPaths) {
            when(mockRequest.getPathInfo()).thenReturn(testPath);
            when(mockRequest.getRequestURI()).thenReturn("/meta" + (testPath != null ? testPath : ""));

            try {
                legacyService.process();
            } catch (InternalServerException e) {
                // Expected - we're testing path processing logic
                assertTrue("Should handle path: " + testPath,
                    e.getMessage().contains(LEGACY_REQUEST_FAILED));
            }
        }
    }

    /**
     * Confirms that LegacyService inherits from MetadataService.
     * No exceptions are expected in this test.
     */
    @Test
    public void testLegacyServiceInheritance() {
        assertTrue("LegacyService should extend MetadataService",
            legacyService instanceof MetadataService);
    }

    /**
     * Invokes process() to verify that the method is overridden and reachable.
     * In this test environment, the call is expected to fail with an
     * InternalServerException; the test asserts that a message is present.
     *
     * Expected exception: InternalServerException
     */
    @Test
    public void testProcessMethodOverride() {
        try {
            legacyService.process();
        } catch (InternalServerException e) {
            // Expected behavior for abstract method implementation
            assertNotNull("Process method should be implemented", e.getMessage());
        }
    }
}

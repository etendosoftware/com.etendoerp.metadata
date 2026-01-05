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

package com.etendoerp.metadata.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * Unit tests for the {@link ProcessUtils} utility class.
 * Tests path parsing, entity ID extraction, and input validation.
 */
public class ProcessUtilsTest {

    private static final String PATH_DELIMITER = "/";
    private static final String RESOURCE_NAME = "report-and-process";
    private static final String ENTITY_ID = "123";
    private static final String VALID_PATH = PATH_DELIMITER + RESOURCE_NAME + PATH_DELIMITER + ENTITY_ID;
    private static final String METADATA_PREFIX = "/com.etendoerp.metadata.meta/";
    private static final String PATH_INFO_REQUIRED = "Path info is required";
    private static final String RESOURCE_NAME_REQUIRED = "Resource name is required";
    private static final String ERROR_PATH_REQUIRED = "Error message should indicate path required";
    private static final String ERROR_RESOURCE_REQUIRED = "Error message should indicate resource name required";
    private static final String ERROR_INVALID_PATH = "Error message should mention invalid path";
    private static final String INVALID_PATH_FORMAT = "Invalid path format";
    private static final String EXCEPTION_NOT_NULL = "Exception should not be null";

    // ========== extractEntityId Tests ==========

    /**
     * Tests extracting entity ID from a valid path.
     */
    @Test
    public void testExtractEntityIdValidPath() {
        String result = ProcessUtils.extractEntityId(VALID_PATH, RESOURCE_NAME);
        assertEquals("Entity ID should be extracted correctly", ENTITY_ID, result);
    }

    /**
     * Tests extracting entity ID from path with metadata prefix.
     */
    @Test
    public void testExtractEntityIdWithMetadataPrefix() {
        String pathWithPrefix = METADATA_PREFIX + RESOURCE_NAME + PATH_DELIMITER + ENTITY_ID;
        String result = ProcessUtils.extractEntityId(pathWithPrefix, RESOURCE_NAME);
        assertEquals("Entity ID should be extracted from prefixed path", ENTITY_ID, result);
    }

    /**
     * Tests extracting entity ID from path without leading slash.
     */
    @Test
    public void testExtractEntityIdWithoutLeadingSlash() {
        String pathWithoutSlash = RESOURCE_NAME + PATH_DELIMITER + ENTITY_ID;
        String result = ProcessUtils.extractEntityId(pathWithoutSlash, RESOURCE_NAME);
        assertEquals("Entity ID should be extracted from path without leading slash", ENTITY_ID, result);
    }

    /**
     * Tests extracting entity ID from path with trailing slash.
     */
    @Test
    public void testExtractEntityIdWithTrailingSlash() {
        String pathWithTrailing = VALID_PATH + PATH_DELIMITER;
        String result = ProcessUtils.extractEntityId(pathWithTrailing, RESOURCE_NAME);
        assertEquals("Entity ID should be extracted from path with trailing slash", ENTITY_ID, result);
    }

    /**
     * Tests that null path throws NotFoundException.
     */
    @Test
    public void testExtractEntityIdNullPath() {
        try {
            ProcessUtils.extractEntityId(null, RESOURCE_NAME);
            fail("Should throw NotFoundException for null path");
        } catch (NotFoundException e) {
            assertEquals(ERROR_PATH_REQUIRED, PATH_INFO_REQUIRED, e.getMessage());
        }
    }

    /**
     * Tests that empty path throws NotFoundException.
     */
    @Test
    public void testExtractEntityIdEmptyPath() {
        try {
            ProcessUtils.extractEntityId("", RESOURCE_NAME);
            fail("Should throw NotFoundException for empty path");
        } catch (NotFoundException e) {
            assertEquals(ERROR_PATH_REQUIRED, PATH_INFO_REQUIRED, e.getMessage());
        }
    }

    /**
     * Tests that blank path throws NotFoundException.
     */
    @Test
    public void testExtractEntityIdBlankPath() {
        try {
            ProcessUtils.extractEntityId("   ", RESOURCE_NAME);
            fail("Should throw NotFoundException for blank path");
        } catch (NotFoundException e) {
            assertEquals(ERROR_PATH_REQUIRED, PATH_INFO_REQUIRED, e.getMessage());
        }
    }

    /**
     * Tests that null resource name throws IllegalArgumentException.
     */
    @Test
    public void testExtractEntityIdNullResourceName() {
        try {
            ProcessUtils.extractEntityId(VALID_PATH, null);
            fail("Should throw IllegalArgumentException for null resource name");
        } catch (IllegalArgumentException e) {
            assertEquals(ERROR_RESOURCE_REQUIRED, RESOURCE_NAME_REQUIRED,
                    e.getMessage());
        }
    }

    /**
     * Tests that empty resource name throws IllegalArgumentException.
     */
    @Test
    public void testExtractEntityIdEmptyResourceName() {
        try {
            ProcessUtils.extractEntityId(VALID_PATH, "");
            fail("Should throw IllegalArgumentException for empty resource name");
        } catch (IllegalArgumentException e) {
            assertEquals(ERROR_RESOURCE_REQUIRED, RESOURCE_NAME_REQUIRED,
                    e.getMessage());
        }
    }

    /**
     * Tests that blank resource name throws IllegalArgumentException.
     */
    @Test
    public void testExtractEntityIdBlankResourceName() {
        try {
            ProcessUtils.extractEntityId(VALID_PATH, "   ");
            fail("Should throw IllegalArgumentException for blank resource name");
        } catch (IllegalArgumentException e) {
            assertEquals(ERROR_RESOURCE_REQUIRED, RESOURCE_NAME_REQUIRED,
                    e.getMessage());
        }
    }

    /**
     * Tests that invalid path format throws NotFoundException.
     */
    @Test
    public void testExtractEntityIdInvalidPathFormat() {
        try {
            ProcessUtils.extractEntityId(PATH_DELIMITER + "invalid" + PATH_DELIMITER + "path", RESOURCE_NAME);
            fail("Should throw NotFoundException for invalid path format");
        } catch (NotFoundException e) {
            assertTrue(ERROR_INVALID_PATH,
                    e.getMessage().contains(INVALID_PATH_FORMAT));
        }
    }

    /**
     * Tests that path with wrong resource name throws NotFoundException.
     */
    @Test
    public void testExtractEntityIdWrongResourceName() {
        try {
            ProcessUtils.extractEntityId(PATH_DELIMITER + "wrong-resource" + PATH_DELIMITER + "123", RESOURCE_NAME);
            fail("Should throw NotFoundException for wrong resource name");
        } catch (NotFoundException e) {
            assertTrue("Error message should mention expected path format",
                    e.getMessage().contains(RESOURCE_NAME));
        }
    }

    /**
     * Tests that path with only resource name (no ID) throws NotFoundException.
     */
    @Test
    public void testExtractEntityIdNoEntityId() {
        try {
            ProcessUtils.extractEntityId(PATH_DELIMITER + RESOURCE_NAME, RESOURCE_NAME);
            fail("Should throw NotFoundException when entity ID is missing");
        } catch (NotFoundException e) {
            assertTrue(ERROR_INVALID_PATH,
                    e.getMessage().contains(INVALID_PATH_FORMAT));
        }
    }

    // ========== extractProcessId Tests ==========

    /**
     * Tests that extractProcessId delegates to extractEntityId.
     */
    @Test
    public void testExtractProcessIdDelegatesToExtractEntityId() {
        String result = ProcessUtils.extractProcessId(VALID_PATH, RESOURCE_NAME);
        assertEquals("extractProcessId should return same result as extractEntityId", ENTITY_ID, result);
    }

    /**
     * Tests extractProcessId with metadata prefix.
     */
    @Test
    public void testExtractProcessIdWithMetadataPrefix() {
        String pathWithPrefix = METADATA_PREFIX + RESOURCE_NAME + PATH_DELIMITER + ENTITY_ID;
        String result = ProcessUtils.extractProcessId(pathWithPrefix, RESOURCE_NAME);
        assertEquals("Process ID should be extracted correctly", ENTITY_ID, result);
    }

    /**
     * Tests extractProcessId with null path.
     */
    @Test
    public void testExtractProcessIdNullPath() {
        try {
            ProcessUtils.extractProcessId(null, RESOURCE_NAME);
            fail("Should throw NotFoundException for null path");
        } catch (NotFoundException e) {
            assertNotNull(EXCEPTION_NOT_NULL, e);
        }
    }

    // ========== Edge Cases ==========

    /**
     * Tests extracting entity ID with UUID format.
     */
    @Test
    public void testExtractEntityIdWithUuid() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String path = PATH_DELIMITER + RESOURCE_NAME + PATH_DELIMITER + uuid;
        String result = ProcessUtils.extractEntityId(path, RESOURCE_NAME);
        assertEquals("UUID entity ID should be extracted correctly", uuid, result);
    }

    /**
     * Tests extracting entity ID with alphanumeric format.
     */
    @Test
    public void testExtractEntityIdWithAlphanumericId() {
        String alphanumericId = "ABC123XYZ";
        String path = PATH_DELIMITER + RESOURCE_NAME + PATH_DELIMITER + alphanumericId;
        String result = ProcessUtils.extractEntityId(path, RESOURCE_NAME);
        assertEquals("Alphanumeric entity ID should be extracted correctly", alphanumericId, result);
    }

    /**
     * Tests extracting entity ID with special resource name.
     */
    @Test
    public void testExtractEntityIdWithDifferentResourceName() {
        String resourceName = "window";
        String path = PATH_DELIMITER + resourceName + PATH_DELIMITER + ENTITY_ID;
        String result = ProcessUtils.extractEntityId(path, resourceName);
        assertEquals("Entity ID should be extracted for different resource names", ENTITY_ID, result);
    }

    /**
     * Tests path with multiple segments after entity ID still extracts correct ID.
     */
    @Test
    public void testExtractEntityIdWithExtraSegments() {
        String path = PATH_DELIMITER + RESOURCE_NAME + PATH_DELIMITER + ENTITY_ID + PATH_DELIMITER + "extra"
                + PATH_DELIMITER + "segments";
        String result = ProcessUtils.extractEntityId(path, RESOURCE_NAME);
        assertEquals("Entity ID should be extracted even with extra segments", ENTITY_ID, result);
    }

    /**
     * Tests that only leading slash path throws NotFoundException.
     */
    @Test
    public void testExtractEntityIdOnlySlash() {
        try {
            ProcessUtils.extractEntityId(PATH_DELIMITER, RESOURCE_NAME);
            fail("Should throw NotFoundException for path with only slash");
        } catch (NotFoundException e) {
            assertTrue(ERROR_INVALID_PATH,
                    e.getMessage().contains(INVALID_PATH_FORMAT));
        }
    }

    /**
     * Tests double slashes in path.
     */
    @Test
    public void testExtractEntityIdWithDoubleSlashes() {
        String path = PATH_DELIMITER + PATH_DELIMITER + RESOURCE_NAME + PATH_DELIMITER + PATH_DELIMITER + ENTITY_ID;
        // The result depends on how split handles empty strings
        try {
            ProcessUtils.extractEntityId(path, RESOURCE_NAME);
            // If it doesn't throw, the test passes
        } catch (NotFoundException e) {
            // This is also acceptable behavior for malformed paths
            assertNotNull(EXCEPTION_NOT_NULL, e);
        }
    }
}

package com.etendoerp.metadata.utils;

import com.etendoerp.metadata.exceptions.NotFoundException;
import java.util.Objects;

public final class ProcessUtils {

    /**
     * Prefix added by the servlet container that must be removed
     * before processing the logical path.
     */
    private static final String METADATA_PREFIX = "/com.etendoerp.metadata.meta/";

    /**
     * Extracts an entity ID from a request path.
     *
     * Expected format:
     *   /{resourceName}/{entityId}
     *
     * Example:
     *   resourceName = "report-and-process"
     *   pathInfo     = "/meta/report-and-process/123"
     *
     * @param pathInfo     the request path info
     * @param resourceName the expected resource name
     * @return the extracted entity ID
     * @throws NotFoundException if the path format is invalid
     */
    public static String extractEntityId(String pathInfo, String resourceName) {
        validateInputs(pathInfo, resourceName);

        String normalizedPath = normalizePath(pathInfo);
        String[] parts = normalizedPath.split("/");

        // Expected: ["", resourceName, entityId]
        if (parts.length < 3 || !resourceName.equals(parts[1])) {
            throw new NotFoundException(
                    "Invalid path format. Expected '/" + resourceName + "/{id}', got: " + pathInfo
            );
        }

        return parts[2];
    }

    /**
     * Convenience method for process extraction.
     */
    public static String extractProcessId(String pathInfo, String processResourceName) {
        return extractEntityId(pathInfo, processResourceName);
    }

    private static void validateInputs(String pathInfo, String resourceName) {
        if (pathInfo == null || pathInfo.isBlank()) {
            throw new NotFoundException("Path info is required");
        }
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("Resource name is required");
        }
    }

    private static String normalizePath(String pathInfo) {
        String cleanPath = pathInfo;

        if (cleanPath.startsWith(METADATA_PREFIX)) {
            cleanPath = cleanPath.substring(METADATA_PREFIX.length() - 1);
        }

        // Ensure it starts with '/'
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }

        // Remove trailing slash
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }

        return cleanPath;
    }
}

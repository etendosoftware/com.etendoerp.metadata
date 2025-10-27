/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2025 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package com.etendoerp.metadata.http;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.client.application.attachment.AttachmentUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Attachment;

/**
 * Servlet for managing attachments through REST API
 * Supports: LIST, UPLOAD, DOWNLOAD, DOWNLOAD_ALL, EDIT, DELETE, DELETE_ALL
 * This servlet expects JWT authentication to be handled by the surrounding infrastructure.
 * The OBContext should already be established when requests reach this servlet.
 * Registered path: /attachments/*
 */
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
        maxFileSize = 1024 * 1024 * 50,       // 50MB
        maxRequestSize = 1024 * 1024 * 100    // 100MB
)
public class AttachmentsServlet extends HttpSecureAppServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LogManager.getLogger(AttachmentsServlet.class);

    // Command constants
    private static final String CMD_LIST = "LIST";
    private static final String CMD_DOWNLOAD = "DOWNLOAD";
    private static final String CMD_DOWNLOAD_ALL = "DOWNLOAD_ALL";
    private static final String CMD_UPLOAD = "UPLOAD";
    private static final String CMD_EDIT = "EDIT";
    private static final String CMD_DELETE = "DELETE";
    private static final String CMD_DELETE_ALL = "DELETE_ALL";

    // Parameter names
    private static final String PARAM_COMMAND = "command";
    private static final String PARAM_TAB_ID = "tabId";
    private static final String PARAM_RECORD_ID = "recordId";
    private static final String PARAM_ORG_ID = "orgId";
    private static final String PARAM_ATTACHMENT_ID = "attachmentId";
    private static final String PARAM_DESCRIPTION = "description";
    private static final String PARAM_FILE = "file";

    // Content types
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_ZIP = "application/zip";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String CHARSET_UTF8 = "UTF-8";

    // Response messages
    private static final String MSG_ERROR_PROCESSING_REQUEST = "Error processing request: ";
    private static final String MSG_INVALID_COMMAND = "Invalid command: ";
    private static final String MSG_COMMAND_REQUIRED = "Command parameter is required";
    private static final String MSG_TAB_RECORD_REQUIRED = "tabId and recordId are required";
    private static final String MSG_TAB_RECORD_ORG_REQUIRED = "tabId, recordId, and orgId are required";
    private static final String MSG_ATTACHMENT_TAB_REQUIRED = "attachmentId and tabId are required";
    private static final String MSG_ATTACHMENT_REQUIRED = "attachmentId is required";
    private static final String MSG_INVALID_TAB_ID = "Invalid tabId";
    private static final String MSG_NO_FILE_UPLOADED = "No file uploaded";
    private static final String MSG_ATTACHMENT_NOT_FOUND = "Attachment not found";
    private static final String MSG_ATTACHMENT_UPDATED = "Attachment updated successfully";
    private static final String MSG_ATTACHMENT_DELETED = "Attachment deleted successfully";
    private static final String MSG_ALL_ATTACHMENTS_DELETED = "All attachments deleted successfully";

    // JSON keys
    private static final String JSON_KEY_ATTACHMENTS = "attachments";
    private static final String JSON_KEY_SUCCESS = "success";
    private static final String JSON_KEY_ERROR = "error";
    private static final String JSON_KEY_MESSAGE = "message";
    private static final String JSON_KEY_ID = "id";
    private static final String JSON_KEY_DATA_TYPE = "dataType";
    private static final String JSON_KEY_CREATED_BY = "createdBy";
    private static final String JSON_KEY_CREATED_BY_IDENTIFIER = "createdBy$_identifier";
    private static final String JSON_KEY_CREATION_DATE = "creationDate";
    private static final String JSON_KEY_SEQUENCE_NUMBER = "sequenceNumber";

    // Other constants
    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_VALUE = "attachment; filename=\"attachments.zip\"";
    private static final String CONTENT_DISPOSITION_TOKEN = "content-disposition";
    private static final String FILENAME_TOKEN = "filename";
    private static final String DEFAULT_FILENAME = "unknown";
    private static final String CORE_DESC_PARAMETER_ID = "E22E8E3B737D4A47A691A073951BBF16";

    // Temp directory for secure file handling
    private static final String APP_TEMP_DIR = System.getProperty("catalina.base", System.getProperty("user.home")) + "/temp/attachments";

    @Inject
    private AttachImplementationManager attachManager;

    @Override
    public void init() throws ServletException {
        super.init();
        // Initialize secure temp directory
        java.io.File tempDir = new java.io.File(APP_TEMP_DIR);
        if (!tempDir.exists()) {
            if (tempDir.mkdirs()) {
                // Set restrictive permissions (owner only)
                boolean success = true;
                success &= tempDir.setReadable(false, false);
                success &= tempDir.setWritable(false, false);
                success &= tempDir.setExecutable(false, false);
                success &= tempDir.setReadable(true, true);
                success &= tempDir.setWritable(true, true);
                success &= tempDir.setExecutable(true, true);

                if (success) {
                    log.info("Created secure temp directory: {}", APP_TEMP_DIR);
                } else {
                    log.warn("Created temp directory but failed to set all permissions: {}", APP_TEMP_DIR);
                }
            } else {
                log.warn("Failed to create temp directory: {}", APP_TEMP_DIR);
            }
        }
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            handleGet(request, response);
        } catch (Exception e) {
            log.error("Error processing GET request", e);
            sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    MSG_ERROR_PROCESSING_REQUEST + e.getMessage());
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            handlePost(request, response);
        } catch (Exception e) {
            log.error("Error processing POST request", e);
            sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    MSG_ERROR_PROCESSING_REQUEST + e.getMessage());
        }
    }

    /**
     * Handles GET requests - supports LIST, DOWNLOAD, and DOWNLOAD_ALL commands
     */
    private void handleGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String command = request.getParameter(PARAM_COMMAND);
        if (command == null) {
            command = CMD_LIST;
        }

        switch (command.toUpperCase()) {
            case CMD_LIST:
                handleList(request, response);
                break;
            case CMD_DOWNLOAD:
                handleDownload(request, response);
                break;
            case CMD_DOWNLOAD_ALL:
                handleDownloadAll(request, response);
                break;
            default:
                sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                        MSG_INVALID_COMMAND + command);
        }
    }

    /**
     * Handles POST requests - supports UPLOAD, EDIT, DELETE, and DELETE_ALL commands
     */
    private void handlePost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String command = request.getParameter(PARAM_COMMAND);
        if (command == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, MSG_COMMAND_REQUIRED);
            return;
        }

        switch (command.toUpperCase()) {
            case CMD_UPLOAD:
                handleUpload(request, response);
                break;
            case CMD_EDIT:
                handleEdit(request, response);
                break;
            case CMD_DELETE:
                handleDelete(request, response);
                break;
            case CMD_DELETE_ALL:
                handleDeleteAll(request, response);
                break;
            default:
                sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                        MSG_INVALID_COMMAND + command);
        }
    }

    /**
     * List all attachments for a record
     */
    private void handleList(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String tabId = request.getParameter(PARAM_TAB_ID);
        String recordId = request.getParameter(PARAM_RECORD_ID);

        if (validateTabAndRecord(tabId, recordId, response)) {
            return;
        }

        try {
            OBContext.setAdminMode(true);

            Tab tab = validateAndGetTab(tabId, response);
            if (tab == null) {
                return;
            }

            String[] recordIds = new String[] { recordId };
            List<JSONObject> attachmentsList = AttachmentUtils.getTabAttachmentsForRows(tab, recordIds);

            JSONArray jsonArray = new JSONArray();
            for (JSONObject attachmentJson : attachmentsList) {
                String attachmentId = attachmentJson.getString(JSON_KEY_ID);
                Attachment attachment = OBDal.getInstance().get(Attachment.class, attachmentId);

                attachmentJson.put(JSON_KEY_DATA_TYPE, attachment.getDataType());
                attachmentJson.put(JSON_KEY_CREATED_BY, attachment.getCreatedBy().getId());
                attachmentJson.put(JSON_KEY_CREATED_BY_IDENTIFIER, attachment.getCreatedBy().getIdentifier());
                attachmentJson.put(JSON_KEY_CREATION_DATE, attachment.getCreationDate());
                attachmentJson.put(JSON_KEY_SEQUENCE_NUMBER, attachment.getSequenceNumber());

                jsonArray.put(attachmentJson);
            }

            JSONObject result = new JSONObject();
            result.put(JSON_KEY_ATTACHMENTS, jsonArray);

            sendJsonResponse(response, HttpStatus.SC_OK, result);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Upload a new attachment
     */
    private void handleUpload(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String tabId = request.getParameter(PARAM_TAB_ID);
        String recordId = request.getParameter(PARAM_RECORD_ID);
        String orgId = request.getParameter(PARAM_ORG_ID);

        if (tabId == null || recordId == null || orgId == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, MSG_TAB_RECORD_ORG_REQUIRED);
            return;
        }

        java.io.File tempFile = null;
        try {
            OBContext.setAdminMode(true);

            Map<String, String> params = new HashMap<>();
            Part filePart = request.getPart(PARAM_FILE);

            if (filePart == null) {
                sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, MSG_NO_FILE_UPLOADED);
                return;
            }

            String description = request.getParameter(PARAM_DESCRIPTION);
            if (description != null) {
                params.put(PARAM_DESCRIPTION, description);
            }

            String fileName = getFileName(filePart);

            // Create temp file in secure directory with restrictive permissions
            java.io.File tempDir = new java.io.File(APP_TEMP_DIR);
            tempFile = java.io.File.createTempFile("attachment_", "_" + fileName, tempDir);

            // Set restrictive permissions (owner only: rw-------)
            boolean permissionsSet = true;
            permissionsSet &= tempFile.setReadable(false, false);
            permissionsSet &= tempFile.setWritable(false, false);
            permissionsSet &= tempFile.setReadable(true, true);
            permissionsSet &= tempFile.setWritable(true, true);

            if (!permissionsSet) {
                log.warn("Failed to set restrictive permissions on temp file: {}", tempFile.getAbsolutePath());
            }

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                IOUtils.copy(filePart.getInputStream(), fos);
            }

            attachManager.upload(params, tabId, recordId, orgId, tempFile);

            JSONObject result = new JSONObject();
            result.put(JSON_KEY_SUCCESS, true);

            sendJsonResponse(response, HttpStatus.SC_OK, result);
        } catch (Exception e) {
            log.error("Error uploading attachment", e);
            sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    MSG_ERROR_PROCESSING_REQUEST + e.getMessage());
        } finally {
            // Ensure temp file is deleted
            if (tempFile != null && tempFile.exists()) {
                try {
                    java.nio.file.Files.delete(tempFile.toPath());
                } catch (IOException e) {
                    log.error("Error deleting temporary file: {}", tempFile.getAbsolutePath(), e);
                    // Fallback: mark for deletion on JVM exit
                    tempFile.deleteOnExit();
                }
            }
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Download a single attachment
     */
    private void handleDownload(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String attachmentId = request.getParameter(PARAM_ATTACHMENT_ID);

        try {
            OBContext.setAdminMode(true);

            Attachment attachment = validateAndGetAttachment(attachmentId, response);
            if (attachment == null) {
                return;
            }

            response.setContentType(CONTENT_TYPE_OCTET_STREAM);
            response.setHeader(CONTENT_DISPOSITION_HEADER,
                    "attachment; filename=\"" + attachment.getName() + "\"");

            try (OutputStream out = response.getOutputStream()) {
                attachManager.download(attachmentId, out);
                out.flush();
            }
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Download all attachments as a zip file
     */
    private void handleDownloadAll(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String tabId = request.getParameter(PARAM_TAB_ID);
        String recordId = request.getParameter(PARAM_RECORD_ID);

        if (validateTabAndRecord(tabId, recordId, response)) {
            return;
        }

        try {
            OBContext.setAdminMode(true);

            Tab tab = validateAndGetTab(tabId, response);
            if (tab == null) {
                return;
            }

            response.setContentType(CONTENT_TYPE_ZIP);
            response.setHeader(CONTENT_DISPOSITION_HEADER, CONTENT_DISPOSITION_VALUE);

            try (OutputStream out = response.getOutputStream()) {
                attachManager.downloadAll(tabId, recordId, out);
                out.flush();
            }
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Edit attachment metadata
     */
    private void handleEdit(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String attachmentId = request.getParameter(PARAM_ATTACHMENT_ID);
        String tabId = request.getParameter(PARAM_TAB_ID);

        if (attachmentId == null || tabId == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, MSG_ATTACHMENT_TAB_REQUIRED);
            return;
        }

        try {
            OBContext.setAdminMode(true);

            Map<String, String> params = new HashMap<>();

            JSONObject requestBody = readRequestBody(request);
            if (requestBody.has(PARAM_DESCRIPTION)) {
                String description = requestBody.getString(PARAM_DESCRIPTION);
                if (description != null && !description.isEmpty()) {
                    params.put(CORE_DESC_PARAMETER_ID, description);
                }
            }

            if (params.isEmpty()) {
                String description = request.getParameter(PARAM_DESCRIPTION);
                if (description != null) {
                    params.put(CORE_DESC_PARAMETER_ID, description);
                }
            }

            attachManager.update(params, attachmentId, tabId);

            JSONObject result = new JSONObject();
            result.put(JSON_KEY_SUCCESS, true);
            result.put(JSON_KEY_MESSAGE, MSG_ATTACHMENT_UPDATED);

            sendJsonResponse(response, HttpStatus.SC_OK, result);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Delete a single attachment
     */
    private void handleDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String attachmentId = request.getParameter(PARAM_ATTACHMENT_ID);

        try {
            OBContext.setAdminMode(true);

            Attachment attachment = validateAndGetAttachment(attachmentId, response);
            if (attachment == null) {
                return;
            }

            attachManager.delete(attachment);

            JSONObject result = new JSONObject();
            result.put(JSON_KEY_SUCCESS, true);
            result.put(JSON_KEY_MESSAGE, MSG_ATTACHMENT_DELETED);

            sendJsonResponse(response, HttpStatus.SC_OK, result);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Delete all attachments for a record
     */
    private void handleDeleteAll(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String tabId = request.getParameter(PARAM_TAB_ID);
        String recordId = request.getParameter(PARAM_RECORD_ID);

        if (validateTabAndRecord(tabId, recordId, response)) {
            return;
        }

        try {
            OBContext.setAdminMode(true);

            Tab tab = validateAndGetTab(tabId, response);
            if (tab == null) {
                return;
            }

            Table table = tab.getTable();
            OBCriteria<Attachment> criteria = OBDal.getInstance().createCriteria(Attachment.class);
            criteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE, table));
            criteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, recordId));
            criteria.setFilterOnReadableOrganization(false);

            List<Attachment> attachments = criteria.list();
            for (Attachment attachment : attachments) {
                attachManager.delete(attachment);
            }

            JSONObject result = new JSONObject();
            result.put(JSON_KEY_SUCCESS, true);
            result.put(JSON_KEY_MESSAGE, MSG_ALL_ATTACHMENTS_DELETED);

            sendJsonResponse(response, HttpStatus.SC_OK, result);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Validates tabId and recordId parameters
     * @return true if valid, false if invalid (error response already sent)
     */
    private boolean validateTabAndRecord(String tabId, String recordId, HttpServletResponse response)
            throws  JSONException {
        if (tabId == null || recordId == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, MSG_TAB_RECORD_REQUIRED);
            return true;
        }
        return false;
    }

    /**
     * Validates and retrieves a Tab by ID
     * @return The Tab object or null if validation fails (error response already sent)
     */
    private Tab validateAndGetTab(String tabId, HttpServletResponse response)
            throws IOException, JSONException {
        if (tabId == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, "tabId is required");
            return null;
        }

        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        if (tab == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, MSG_INVALID_TAB_ID);
            return null;
        }

        return tab;
    }

    /**
     * Validates and retrieves an Attachment by ID
     * @return The Attachment object or null if validation fails (error response already sent)
     */
    private Attachment validateAndGetAttachment(String attachmentId, HttpServletResponse response)
            throws IOException, JSONException {
        if (attachmentId == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST, MSG_ATTACHMENT_REQUIRED);
            return null;
        }

        Attachment attachment = OBDal.getInstance().get(Attachment.class, attachmentId);
        if (attachment == null) {
            sendErrorResponse(response, HttpStatus.SC_NOT_FOUND, MSG_ATTACHMENT_NOT_FOUND);
            return null;
        }

        return attachment;
    }

    /**
     * Extract filename from Part header with improved sanitization
     */
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader(CONTENT_DISPOSITION_TOKEN);
        if (contentDisposition != null) {
            for (String token : contentDisposition.split(";")) {
                if (token.trim().startsWith(FILENAME_TOKEN)) {
                    String candidate = token.substring(token.indexOf('=') + 1).trim().replace("\"", "");

                    // Prevent path traversal attacks
                    candidate = candidate.replace("\\.\\.", "");
                    candidate = candidate.replaceAll("[/\\\\]", "");

                    // Sanitize special characters
                    String sanitized = candidate.replaceAll("[^a-zA-Z0-9._-]", "_");

                    // Limit length to prevent issues
                    if (sanitized.length() > 255) {
                        sanitized = sanitized.substring(0, 255);
                    }

                    return sanitized.isEmpty() ? DEFAULT_FILENAME : sanitized;
                }
            }
        }
        return DEFAULT_FILENAME;
    }

    /**
     * Reads and parses JSON request body
     */
    private JSONObject readRequestBody(HttpServletRequest request) throws IOException, JSONException {
        StringBuilder buffer = new StringBuilder();
        String line;

        try (java.io.BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
        }

        String requestBody = buffer.toString();
        if (requestBody.isEmpty()) {
            return new JSONObject();
        }

        return new JSONObject(requestBody);
    }

    /**
     * Sends a JSON response
     */
    private void sendJsonResponse(HttpServletResponse response, int statusCode, JSONObject json)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(CHARSET_UTF8);

        PrintWriter out = response.getWriter();
        out.print(json.toString());
        out.flush();
    }

    /**
     * Sends an error response in JSON format
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) {
        try {
            JSONObject errorJson = new JSONObject();
            errorJson.put(JSON_KEY_SUCCESS, false);
            errorJson.put(JSON_KEY_ERROR, message);

            sendJsonResponse(response, statusCode, errorJson);
        } catch (Exception e) {
            log.error("Error sending error response", e);
            response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // SWS framework required methods with path parameter

    public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        doGet(request, response);
    }

    public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        doPost(request, response);
    }

    public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        doPost(request, response);
    }
}
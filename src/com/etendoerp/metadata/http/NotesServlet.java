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

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.structure.OrganizationEnabled;
import org.openbravo.client.application.Note;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.SecurityChecker;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;

/**
 * Servlet to handle notes operations as a REST API endpoint.
 * Provides GET (fetch), POST (create), and DELETE operations for AD_Note entities.
 * This servlet expects JWT authentication to be handled by the surrounding infrastructure.
 * The OBContext should already be established when requests reach this servlet.
 * Registered path: /notes/*
 */
public class NotesServlet extends HttpSecureAppServlet {
    private static final Logger log = LogManager.getLogger(NotesServlet.class);
    private static final long serialVersionUID = 1L;
    private static final String PARAM_TABLE = "table";
    private static final String PARAM_RECORD = "record";
    private static final String PARAM_NOTE = "note";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String ISO_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String ERROR_PROCESSING_REQUEST = "Error processing request: ";
    private static final String ERROR_INVALID_TABLE_ID = "Invalid table ID: ";
    private static final String ERROR_RECORD_NOT_ACCESSIBLE = "Insufficient permissions to access record: ";

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            handleGet(request, response);
        } catch (Exception e) {
            log.error("Error processing GET request", e);
            sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ERROR_PROCESSING_REQUEST + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            handlePost(request, response);
        } catch (Exception e) {
            log.error("Error processing POST request", e);
            sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ERROR_PROCESSING_REQUEST + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            handleDelete(request, response);
        } catch (Exception e) {
            log.error("Error processing DELETE request", e);
            sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ERROR_PROCESSING_REQUEST + e.getMessage());
        }
    }

    /**
     * Handles GET requests to fetch notes
     */
    private void handleGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        // Get query parameters
        String tableId = request.getParameter(PARAM_TABLE);
        String recordId = request.getParameter(PARAM_RECORD);

        // Validate required parameters
        if (StringUtils.isEmpty(tableId) || StringUtils.isEmpty(recordId)) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                    "Missing required parameters: table and record");
            return;
        }

        // Validate table exists
        if (findTable(tableId) == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                    ERROR_INVALID_TABLE_ID + tableId);
            return;
        }

        // Validate the user can read the record the notes belong to
        if (!isRecordReadable(tableId, recordId)) {
            sendErrorResponse(response, HttpStatus.SC_FORBIDDEN,
                    ERROR_RECORD_NOT_ACCESSIBLE + recordId);
            return;
        }

        // Fetch notes
        List<Note> notes = fetchNotes(tableId, recordId);

        // Convert to JSON array
        JSONArray jsonArray = notesToJsonArray(notes);

        // Send response
        sendJsonResponse(response, HttpStatus.SC_OK, jsonArray);
    }

    /**
     * Handles POST requests to create notes
     */
    private void handlePost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        JSONObject requestBody = readRequestBody(request);

        String tableId = requestBody.optString(PARAM_TABLE);
        String recordId = requestBody.optString(PARAM_RECORD);
        String noteContent = requestBody.optString(PARAM_NOTE);

        if (StringUtils.isEmpty(tableId)) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                    "Missing required parameter: table");
            return;
        }

        if (StringUtils.isEmpty(recordId)) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                    "Missing required parameter: record");
            return;
        }

        if (StringUtils.isEmpty(noteContent)) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                    "Missing required parameter: note");
            return;
        }

        // Validate table exists
        Table table = findTable(tableId);
        if (table == null) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                    ERROR_INVALID_TABLE_ID + tableId);
            return;
        }

        // Validate the user can read the record the note is attached to
        if (!isRecordReadable(tableId, recordId)) {
            sendErrorResponse(response, HttpStatus.SC_FORBIDDEN,
                    ERROR_RECORD_NOT_ACCESSIBLE + recordId);
            return;
        }

        // Create note
        Note note = createNote(table, recordId, noteContent);

        // Convert to JSON
        JSONObject jsonObject = noteToJson(note);

        // Send response
        sendJsonResponse(response, HttpStatus.SC_OK, jsonObject);
    }

    /**
     * Handles DELETE requests to remove notes
     */
    private void handleDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        String noteId = extractNoteIdFromPath(request);

        if (StringUtils.isEmpty(noteId)) {
            sendErrorResponse(response, HttpStatus.SC_BAD_REQUEST,
                    "Missing note ID in path");
            return;
        }

        try {
            OBContext.setAdminMode(true);
            Note note = OBDal.getInstance().get(Note.class, noteId);

            if (note == null) {
                sendErrorResponse(response, HttpStatus.SC_NOT_FOUND,
                        "Note not found: " + noteId);
                return;
            }

            if (!isRecordReadable(note.getTable().getId(), note.getRecord())) {
                sendErrorResponse(response, HttpStatus.SC_FORBIDDEN,
                        ERROR_RECORD_NOT_ACCESSIBLE + note.getRecord());
                return;
            }

            if (!canDeleteNote(note)) {
                sendErrorResponse(response, HttpStatus.SC_FORBIDDEN,
                        "Insufficient permissions to delete note");
                return;
            }

            deleteNote(note);
            JSONObject successResponse = new JSONObject();
            successResponse.put("success", true);
            successResponse.put("id", noteId);

            sendJsonResponse(response, HttpStatus.SC_OK, successResponse);
        } catch (Exception e) {
            log.error("Error in handleDelete", e);
            sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    ERROR_PROCESSING_REQUEST + e.getMessage());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Loads the AD_Table row for the given id. It runs in administrator mode because AD_Table is
     * System level metadata and therefore is not readable by functional roles, which would make the
     * read fail with an OBSecurityException.
     *
     * @param tableId
     *            the AD_Table_ID received from the client
     * @return the table, or null when it does not exist or it cannot be loaded
     */
    private Table findTable(String tableId) {
        try {
            OBContext.setAdminMode(true);
            return OBDal.getInstance().get(Table.class, tableId);
        } catch (Exception e) {
            log.error("Error validating table {}", tableId, e);
            return null;
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Checks whether the current user is allowed to read the record the note belongs to. It mirrors
     * the classic NoteDataSource behaviour: the entity is resolved from the in-memory runtime model
     * (no privileged metadata read) and the target record is loaded with the caller privileges.
     * When invoked inside an administrator mode block, as the delete flow does, the entity level
     * check is bypassed by the platform and only organization readability is enforced.
     *
     * @param tableId
     *            the AD_Table_ID of the record
     * @param recordId
     *            the id of the record the note is attached to
     * @return true when the record is readable, or when the table has no entity in the runtime
     *         model; false when the platform denies the read
     */
    private boolean isRecordReadable(String tableId, String recordId) {
        try {
            Entity entity = ModelProvider.getInstance().getEntityByTableId(tableId);
            if (entity == null) {
                return true;
            }

            Object currentRecord = OBDal.getInstance().get(entity.getMappingClass(), recordId);
            if (currentRecord instanceof OrganizationEnabled) {
                SecurityChecker.getInstance().checkReadableAccess((OrganizationEnabled) currentRecord);
            }

            return true;
        } catch (OBSecurityException e) {
            log.warn("Record {} of table {} is not readable by the current user", recordId, tableId, e);
            return false;
        }
    }

    /**
     * Fetches notes for a given table and record
     */
    private List<Note> fetchNotes(String tableId, String recordId) {
        try {
            OBContext.setAdminMode(true);

            OBCriteria<Note> criteria = OBDal.getInstance().createCriteria(Note.class);
            criteria.add(Restrictions.eq(Note.PROPERTY_TABLE + ".id", tableId));
            criteria.add(Restrictions.eq(Note.PROPERTY_RECORD, recordId));
            criteria.addOrderBy(Note.PROPERTY_UPDATED, false);

            criteria.setFilterOnReadableOrganization(false);

            return criteria.list();
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Creates a new note for the given table and record
     *
     * @param table
     *            the table the record belongs to, already loaded by {@link #findTable(String)}
     * @param recordId
     *            the id of the record the note is attached to
     * @param noteContent
     *            the text of the note
     * @return the persisted note
     */
    private Note createNote(Table table, String recordId, String noteContent) {
        try {
            OBContext.setAdminMode(true);

            Note note = OBProvider.getInstance().get(Note.class);
            note.setTable(table);
            note.setRecord(recordId);
            note.setNote(noteContent);
            note.setActive(true);

            note.setClient(OBContext.getOBContext().getCurrentClient());
            note.setOrganization(OBContext.getOBContext().getCurrentOrganization());

            OBDal.getInstance().save(note);
            OBDal.getInstance().flush();

            return note;
        } catch (Exception e) {
            throw new OBException("Error creating note", e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Deletes a note
     */
    private void deleteNote(Note note) {
        try {
            OBContext.setAdminMode(true);

            OBDal.getInstance().remove(note);
            OBDal.getInstance().flush();
        } catch (Exception e) {
            throw new OBException("Error deleting note", e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Checks if the current user can delete a note
     * Validates if user is the creator or has appropriate permissions
     */
    private boolean canDeleteNote(Note note) {
        try {
            String currentUserId = OBContext.getOBContext().getUser().getId();
            if (note.getCreatedBy() == null) {
                log.error("Note creator is null for note: " + note.getId());
                return false;
            }
            String creatorId = note.getCreatedBy().getId();

            return currentUserId.equals(creatorId);
        } catch (Exception e) {
            log.error("Error checking delete permissions", e);
            return false;
        }
    }

    /**
     * Converts a list of notes to JSON array
     */
    private JSONArray notesToJsonArray(List<Note> notes) throws JSONException {
        JSONArray jsonArray = new JSONArray();

        for (Note note : notes) {
            jsonArray.put(noteToJson(note));
        }

        return jsonArray;
    }

    /**
     * Converts a single note to JSON object
     */
    private JSONObject noteToJson(Note note) throws JSONException {
        JSONObject json = new JSONObject();

        SimpleDateFormat dateFormat = new SimpleDateFormat(ISO_DATE_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        json.put("id", note.getId());
        json.put("note", note.getNote());
        json.put(PARAM_TABLE, note.getTable().getId());
        json.put(PARAM_RECORD, note.getRecord());
        json.put("createdBy", note.getCreatedBy().getId());
        json.put("createdBy$_identifier", note.getCreatedBy().getIdentifier());

        if (note.getCreationDate() != null) {
            json.put("creationDate", dateFormat.format(note.getCreationDate()));
        }

        if (note.getUpdated() != null) {
            json.put("updated", dateFormat.format(note.getUpdated()));
        }

        return json;
    }

    /**
     * Extracts note ID from request path
     * Expected format: /sws/com.etendoerp.metadata.notes/{noteId}
     */
    private String extractNoteIdFromPath(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();

        if (pathInfo != null && pathInfo.length() > 1) {
            // Remove leading slash and return the ID
            return pathInfo.substring(1);
        }

        return null;
    }

    /**
     * Reads and parses JSON request body
     */
    private JSONObject readRequestBody(HttpServletRequest request) throws IOException, JSONException {
        StringBuilder buffer = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;

        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }

        String requestBody = buffer.toString();

        if (StringUtils.isEmpty(requestBody)) {
            return new JSONObject();
        }

        return new JSONObject(requestBody);
    }

    /**
     * Sends a JSON response
     */
    private void sendJsonResponse(HttpServletResponse response, int statusCode, Object jsonData)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(CHARSET_UTF8);

        response.getWriter().write(jsonData.toString());
        response.getWriter().flush();
    }

    /**
     * Sends an error response in JSON format
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) {
        try {
            JSONObject errorJson = new JSONObject();
            errorJson.put("error", message);
            errorJson.put("status", statusCode);

            sendJsonResponse(response, statusCode, errorJson);
        } catch (Exception e) {
            log.error("Error sending error response", e);
            response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        handleGet(request, response);
    }

    public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        handlePost(request, response);
    }

    public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        handleDelete(request, response);
    }
}
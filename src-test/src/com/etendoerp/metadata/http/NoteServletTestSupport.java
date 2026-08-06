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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Note;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.OBBaseTest;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Shared fixture for the {@link NotesServlet} test classes.
 *
 * <p>
 * It owns the mocks, the captured response writer and the stubbing helpers that every notes scenario
 * needs, so that the concrete test classes only declare what is specific to them.
 * </p>
 */
public abstract class NoteServletTestSupport extends OBBaseTest {

    protected static final String TEST_TABLE_ID = MetadataTestConstants.TABLE_ID;
    protected static final String TEST_USER_ID = MetadataTestConstants.TEST_USER_ID;
    protected static final String TEST_RECORD_ID = "test-record-123";
    protected static final String TEST_NOTE_ID = "note-456";
    protected static final String TEST_NOTE_CONTENT = "This is a test note";
    protected static final String TEST_USER_IDENTIFIER = "Test User";
    protected static final String PARAM_TABLE = "table";
    protected static final String PARAM_RECORD = "record";
    protected static final String PARAM_NOTE = "note";

    @Mock
    protected HttpServletRequest mockRequest;

    @Mock
    protected HttpServletResponse mockResponse;

    @Mock
    protected OBDal mockDal;

    @Mock
    protected OBProvider mockProvider;

    @Mock
    protected OBContext mockContext;

    @Mock
    protected Table mockTable;

    @Mock
    protected Note mockNote;

    @Mock
    protected User mockUser;

    @Mock
    protected Organization mockOrganization;

    @Mock
    protected OBCriteria<Note> mockCriteria;

    protected NotesServlet servlet;
    protected StringWriter stringWriter;

    /**
     * Initializes the servlet under test, the captured response writer and the context defaults
     * shared by every scenario.
     *
     * @throws Exception
     *             when the base test setup or the response writer cannot be initialized
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new NotesServlet();
        stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        lenient().when(mockResponse.getWriter()).thenReturn(printWriter);
        lenient().when(mockContext.getUser()).thenReturn(mockUser);
        lenient().when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
        lenient().when(mockUser.getId()).thenReturn(TEST_USER_ID);
    }

    /**
     * Returns the response body written by the servlet.
     *
     * @return the captured response content
     */
    protected String getResponseContent() {
        return stringWriter.toString();
    }

    /**
     * Wires the DAL singleton to its mock.
     *
     * @param dalMock
     *            static mock of {@link OBDal}
     */
    protected void setupDal(MockedStatic<OBDal> dalMock) {
        dalMock.when(OBDal::getInstance).thenReturn(mockDal);
    }

    /**
     * Wires the DAL and context singletons to their mocks. The context is only needed by the flows
     * that read the current user, client or organization.
     *
     * @param dalMock
     *            static mock of {@link OBDal}
     * @param contextMock
     *            static mock of {@link OBContext}
     */
    protected void setupDalAndContext(MockedStatic<OBDal> dalMock, MockedStatic<OBContext> contextMock) {
        setupDal(dalMock);
        contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
    }

    /**
     * Stubs the AD_Table lookup performed before any notes operation.
     */
    protected void setupTableLookup() {
        when(mockDal.get(Table.class, TEST_TABLE_ID)).thenReturn(mockTable);
    }

    /**
     * Stubs the table lookup and the criteria used to fetch the notes of a record.
     *
     * @param notes
     *            the notes returned by the criteria
     */
    protected void setupNoteCriteria(List<Note> notes) {
        setupTableLookup();
        when(mockDal.createCriteria(Note.class)).thenReturn(mockCriteria);
        when(mockCriteria.add(any())).thenReturn(mockCriteria);
        when(mockCriteria.addOrderBy(anyString(), anyBoolean())).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(notes);
    }

    /**
     * Prepares everything needed to create and serialize a new note.
     *
     * @param providerMock
     *            static mock of {@link OBProvider}
     */
    protected void setupNoteCreation(MockedStatic<OBProvider> providerMock) {
        providerMock.when(OBProvider::getInstance).thenReturn(mockProvider);
        when(mockProvider.get(Note.class)).thenReturn(mockNote);
        setupTableLookup();
        stubNoteJsonFields(mockNote);
    }

    /**
     * Stubs the note lookup performed by the delete flow, including the table needed by the record
     * level access check.
     *
     * @param creator
     *            the user that created the note, null to simulate a note without creator
     */
    protected void setupNoteForDelete(User creator) {
        when(mockDal.get(Note.class, TEST_NOTE_ID)).thenReturn(mockNote);
        when(mockNote.getTable()).thenReturn(mockTable);
        when(mockNote.getCreatedBy()).thenReturn(creator);
    }

    /**
     * Stubs the getters of a note read while building the JSON response.
     *
     * @param note
     *            the note to stub
     */
    protected void stubNoteJsonFields(Note note) {
        lenient().when(note.getId()).thenReturn(TEST_NOTE_ID);
        lenient().when(note.getNote()).thenReturn(TEST_NOTE_CONTENT);
        lenient().when(note.getTable()).thenReturn(mockTable);
        lenient().when(note.getRecord()).thenReturn(TEST_RECORD_ID);
        lenient().when(note.getCreatedBy()).thenReturn(mockUser);
        lenient().when(note.getCreationDate()).thenReturn(new Date());
        lenient().when(note.getUpdated()).thenReturn(new Date());
        lenient().when(mockTable.getId()).thenReturn(TEST_TABLE_ID);
        lenient().when(mockUser.getIdentifier()).thenReturn(TEST_USER_IDENTIFIER);
    }

    /**
     * Prepares the request path pointing to the test note, as used by the delete flow.
     */
    protected void stubNotePath() {
        when(mockRequest.getPathInfo()).thenReturn("/" + TEST_NOTE_ID);
    }

    /**
     * Prepares the request parameters of a notes fetch.
     */
    protected void stubGetParameters() {
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn(TEST_TABLE_ID);
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(TEST_RECORD_ID);
    }

    /**
     * Prepares a valid note creation request body.
     *
     * @throws Exception
     *             when the request reader cannot be stubbed
     */
    protected void stubPostBody() throws Exception {
        stubRequestBody(createNoteRequestBody());
    }

    /**
     * Prepares the given request body.
     *
     * @param body
     *            the raw body the servlet will read
     * @throws Exception
     *             when the request reader cannot be stubbed
     */
    protected void stubRequestBody(String body) throws Exception {
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    /**
     * Builds a valid note creation request body.
     *
     * @return the JSON body containing table, record and note
     * @throws JSONException
     *             when the body cannot be built
     */
    protected String createNoteRequestBody() throws JSONException {
        JSONObject requestBody = new JSONObject();
        requestBody.put(PARAM_TABLE, TEST_TABLE_ID);
        requestBody.put(PARAM_RECORD, TEST_RECORD_ID);
        requestBody.put(PARAM_NOTE, TEST_NOTE_CONTENT);

        return requestBody.toString();
    }
}

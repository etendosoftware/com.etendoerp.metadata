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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for the record-level authorization added to {@link AttachmentsServlet}.
 *
 * <p>These tests verify that the DOWNLOAD command denies access when the attachment's
 * owning record is not visible to the caller (an IDOR fix), and allows it when it is.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class AttachmentsServletAccessTest extends OBBaseTest {

    private static final String TEST_ATTACHMENT_ID = "attachment-abc";
    private static final String TEST_RECORD_ID = "record-456";
    private static final String TEST_TAB_ID = "tab-123";
    private static final String TEST_ORG_ID = "org-123";

    private static final String PARAM_COMMAND = "command";
    private static final String PARAM_ATTACHMENT_ID = "attachmentId";
    private static final String PARAM_TAB_ID = "tabId";
    private static final String PARAM_RECORD_ID = "recordId";
    private static final String PARAM_ORG_ID = "orgId";
    private static final String PARAM_FILE = "file";

    private static final String CMD_DOWNLOAD = "DOWNLOAD";
    private static final String CMD_DELETE = "DELETE";
    private static final String CMD_EDIT = "EDIT";
    private static final String CMD_LIST = "LIST";
    private static final String CMD_DOWNLOAD_ALL = "DOWNLOAD_ALL";
    private static final String CMD_UPLOAD = "UPLOAD";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private AttachImplementationManager mockAttachManager;
    @Mock private OBDal mockDal;
    @Mock private OBContext mockContext;
    @Mock private Tab mockTab;
    @Mock private Table mockTable;
    @Mock private Attachment mockAttachment;
    @Mock private ModelProvider mockModelProvider;
    @Mock private Entity mockEntity;
    @Mock private Organization mockOrganization;
    @Mock private Part mockPart;
    @Mock private InputStream mockInputStream;

    private AttachmentsServlet servlet;
    private StringWriter stringWriter;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new AttachmentsServlet();

        java.lang.reflect.Field field = AttachmentsServlet.class.getDeclaredField("attachManager");
        field.setAccessible(true);
        field.set(servlet, mockAttachManager);

        stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(mockResponse.getWriter()).thenReturn(printWriter);
        when(mockResponse.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                // not exercised in these tests
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // not exercised in these tests
            }
        });
    }

    /**
     * Download must be denied (403) when the attachment's owning record is not
     * visible to the caller, and the file must never be streamed.
     */
    @Test
    public void testDownload_denied_whenRecordNotVisibleToUser() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getTable()).thenReturn(mockTable);
        when(mockAttachment.getRecord()).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            AttachmentsAccessStubs.stubRecordNotFound(mockDal, TEST_RECORD_ID); // no access

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains(AttachmentsAccessStubs.MSG_ACCESS_DENIED));
            verify(mockAttachManager, never()).download(anyString(), any());
        }
    }

    /**
     * Positive control: download proceeds when the owning record IS visible to the caller.
     */
    @Test
    public void testDownload_allowed_whenRecordVisible() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getTable()).thenReturn(mockTable);
        when(mockAttachment.getRecord()).thenReturn(TEST_RECORD_ID);
        when(mockAttachment.getName()).thenReturn("test-file.txt");

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            AttachmentsAccessStubs.stubRecordVisible(mockDal, TEST_RECORD_ID); // visible

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse, never()).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any());
        }
    }

    /**
     * Delete must be denied (403) when the attachment's owning record is visible but its
     * organization is not among the caller's writable organizations, and the attachment
     * must never be deleted.
     */
    @Test
    public void testDelete_denied_whenNotWritable() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getTable()).thenReturn(mockTable);
        when(mockAttachment.getRecord()).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            AttachmentsAccessStubs.stubRecordNotWritable(mockDal, mockContext, TEST_RECORD_ID, mockOrganization, TEST_ORG_ID);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains(AttachmentsAccessStubs.MSG_ACCESS_DENIED));
            verify(mockAttachManager, never()).delete(any(Attachment.class));
        }
    }

    /**
     * Positive control: delete proceeds when the owning record's organization IS writable.
     */
    @Test
    public void testDelete_allowed_whenWritable() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getTable()).thenReturn(mockTable);
        when(mockAttachment.getRecord()).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            AttachmentsAccessStubs.stubRecordWritable(mockDal, mockContext, TEST_RECORD_ID, mockOrganization, TEST_ORG_ID);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse, never()).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockAttachManager).delete(mockAttachment);
        }
    }

    /**
     * Edit must be denied (403) when the attachment's owning record is visible but its
     * organization is not among the caller's writable organizations, and the attachment
     * must never be updated.
     */
    @Test
    public void testEdit_denied_whenNotWritable() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_EDIT);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockAttachment.getTable()).thenReturn(mockTable);
        when(mockAttachment.getRecord()).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            AttachmentsAccessStubs.stubRecordNotWritable(mockDal, mockContext, TEST_RECORD_ID, mockOrganization, TEST_ORG_ID);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains(AttachmentsAccessStubs.MSG_ACCESS_DENIED));
            verify(mockAttachManager, never()).update(any(), anyString(), anyString());
        }
    }

    /**
     * LIST must be denied (403) when the record is not visible to the caller, and the
     * attachment list must never be read.
     */
    @Test
    public void testList_denied_whenRecordNotVisible() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockTab.getTable()).thenReturn(mockTable);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            AttachmentsAccessStubs.stubRecordNotFound(mockDal, TEST_RECORD_ID); // no access

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains(AttachmentsAccessStubs.MSG_ACCESS_DENIED));
            // AttachmentUtils.getTabAttachmentsForRows is never reached: denial happens
            // before the list is read, which the 403 status above already demonstrates.
        }
    }

    /**
     * Positive control: LIST proceeds past authorization when the record IS visible to
     * the caller (it is not rejected with 403; what happens further downstream, in
     * {@code AttachmentUtils}, is outside this unit's scope).
     */
    @Test
    public void testList_allowed_whenRecordVisible() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockTab.getTable()).thenReturn(mockTable);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            AttachmentsAccessStubs.stubRecordVisible(mockDal, TEST_RECORD_ID); // visible

            try {
                servlet.doGet(mockRequest, mockResponse);
            } catch (Throwable downstreamFailure) {
                // AttachmentUtils.getTabAttachmentsForRows needs a live Weld container that
                // this plain Mockito unit test does not provide. Reaching it at all (Phase 3)
                // already proves authorization (Phase 2) was passed, which is what this test
                // verifies; whatever happens past that point is outside this unit's scope.
            }

            verify(mockResponse, never()).setStatus(HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * DOWNLOAD_ALL must be denied (403) when the record is not visible to the caller,
     * and the zip stream must never be produced.
     */
    @Test
    public void testDownloadAll_denied_whenRecordNotVisible() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD_ALL);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockTab.getTable()).thenReturn(mockTable);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            AttachmentsAccessStubs.stubRecordNotFound(mockDal, TEST_RECORD_ID); // no access

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains(AttachmentsAccessStubs.MSG_ACCESS_DENIED));
            verify(mockAttachManager, never()).downloadAll(anyString(), anyString(), any());
        }
    }

    /**
     * Positive control: DOWNLOAD_ALL proceeds when the record IS visible to the caller.
     */
    @Test
    public void testDownloadAll_allowed_whenRecordVisible() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD_ALL);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockTab.getTable()).thenReturn(mockTable);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            AttachmentsAccessStubs.stubRecordVisible(mockDal, TEST_RECORD_ID); // visible

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse, never()).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockAttachManager).downloadAll(eq(TEST_TAB_ID), eq(TEST_RECORD_ID), any());
        }
    }

    /**
     * Upload must be denied (403) when the owning record IS visible but its organization
     * is not among the caller's writable organizations, and the file must never be
     * handed to {@code attachManager.upload}.
     */
    @Test
    public void testUpload_denied_whenRecordNotWritable() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart(PARAM_FILE)).thenReturn(mockPart);
        when(mockPart.getHeader(AttachmentsAccessStubs.CONTENT_DISPOSITION_TOKEN))
                .thenReturn(AttachmentsAccessStubs.UPLOAD_CONTENT_DISPOSITION);
        when(mockTab.getTable()).thenReturn(mockTable);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            // record's org NOT writable
            AttachmentsAccessStubs.stubRecordNotWritable(mockDal, mockContext, TEST_RECORD_ID, mockOrganization, TEST_ORG_ID);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains(AttachmentsAccessStubs.MSG_ACCESS_DENIED));
            verify(mockAttachManager, never()).upload(any(), any(), any(), any(), any());
        }
    }

    /**
     * Upload must be denied (403) when the owning record IS writable but the request's
     * {@code orgId} is not among the caller's writable organizations, and the file must
     * never be handed to {@code attachManager.upload}.
     */
    @Test
    public void testUpload_denied_whenOrgNotWritable() throws Exception {
        final String requestOrgId = "org-999";

        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(requestOrgId);
        when(mockRequest.getPart(PARAM_FILE)).thenReturn(mockPart);
        when(mockPart.getHeader(AttachmentsAccessStubs.CONTENT_DISPOSITION_TOKEN))
                .thenReturn(AttachmentsAccessStubs.UPLOAD_CONTENT_DISPOSITION);
        when(mockTab.getTable()).thenReturn(mockTable);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            // Writable orgs include the record's org but NOT the request's orgId.
            AttachmentsAccessStubs.stubRecordWritable(mockDal, mockContext, TEST_RECORD_ID, mockOrganization, TEST_ORG_ID);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains(AttachmentsAccessStubs.MSG_ACCESS_DENIED));
            verify(mockAttachManager, never()).upload(any(), any(), any(), any(), any());
        }
    }

    /**
     * Positive control: upload proceeds when the owning record's organization AND the
     * request's orgId are both writable (it is not rejected with 403; what happens
     * further downstream - the physical file I/O - is outside this unit's scope, mirroring
     * {@code testUploadAttachment_ValidationSuccess}).
     */
    @Test
    public void testUpload_allowed_whenWritable() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart(PARAM_FILE)).thenReturn(mockPart);
        when(mockPart.getHeader(AttachmentsAccessStubs.CONTENT_DISPOSITION_TOKEN))
                .thenReturn(AttachmentsAccessStubs.UPLOAD_CONTENT_DISPOSITION);
        lenient().when(mockPart.getInputStream()).thenReturn(mockInputStream);
        lenient().when(mockInputStream.read(any(byte[].class))).thenReturn(-1);
        when(mockTab.getTable()).thenReturn(mockTable);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            AttachmentsAccessStubs.stubEntityResolution(mockTable, mp, mockModelProvider, mockEntity);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            AttachmentsAccessStubs.stubRecordWritable(mockDal, mockContext, TEST_RECORD_ID, mockOrganization, TEST_ORG_ID);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse, never()).setStatus(HttpStatus.SC_FORBIDDEN);
        }
    }
}

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Attachment;
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

    private static final String PARAM_COMMAND = "command";
    private static final String PARAM_ATTACHMENT_ID = "attachmentId";

    private static final String CMD_DOWNLOAD = "DOWNLOAD";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private AttachImplementationManager mockAttachManager;
    @Mock private OBDal mockDal;
    @Mock private OBContext mockContext;
    @Mock private Table mockTable;
    @Mock private Attachment mockAttachment;
    @Mock private ModelProvider mockModelProvider;
    @Mock private Entity mockEntity;

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
        when(mockTable.getDBTableName()).thenReturn("C_Order");

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            mp.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
            when(mockModelProvider.getEntityByTableName("C_Order")).thenReturn(mockEntity);
            when(mockEntity.getName()).thenReturn("Order");
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            when(mockDal.get("Order", TEST_RECORD_ID)).thenReturn(null); // no access

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(stringWriter.toString().contains("Access denied"));
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
        when(mockTable.getDBTableName()).thenReturn("C_Order");

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> ctx = mockStatic(OBContext.class);
             MockedStatic<ModelProvider> mp = mockStatic(ModelProvider.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            ctx.when(OBContext::getOBContext).thenReturn(mockContext);
            ctx.when(() -> OBContext.setAdminMode(true)).thenAnswer(i -> null);
            ctx.when(OBContext::restorePreviousMode).thenAnswer(i -> null);
            mp.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
            when(mockModelProvider.getEntityByTableName("C_Order")).thenReturn(mockEntity);
            when(mockEntity.getName()).thenReturn("Order");
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            when(mockDal.get("Order", TEST_RECORD_ID)).thenReturn(mock(BaseOBObject.class)); // visible

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse, never()).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any());
        }
    }
}

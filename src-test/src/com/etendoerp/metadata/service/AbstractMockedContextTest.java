/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Base class for unit tests that need mocked OBContext and OBDal statics.
 * Provides common mock fields, response capture, and a helper to run test
 * logic inside a properly wired static-mock scope.
 */
abstract class AbstractMockedContextTest {

    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock OBContext obContext;
    @Mock OBDal     obDal;
    @Mock Session   session;

    /** Captures the text written to {@code response.getWriter()}. */
    StringWriter responseCapture;

    @BeforeEach
    void setUpResponseWriter() throws Exception {
        responseCapture = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseCapture));
    }

    /**
     * Executes {@code action} inside a scope where {@link OBContext#getOBContext()}
     * and {@link OBDal#getInstance()} return the mocked instances, and
     * {@code obDal.getSession()} returns the mocked session.
     */
    void runWithMockedContext(ThrowingRunnable action) throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            ctxMock.when(OBContext::getOBContext).thenReturn(obContext);
            ctxMock.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(inv -> null);
            ctxMock.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);
            action.run();
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        /**
         * Executes the test action, allowing checked exceptions for test convenience.
         *
         * @throws Exception if the test action fails
         */
        @SuppressWarnings("java:S112")
        void run() throws Exception;
    }
}

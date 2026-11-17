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

package com.etendoerp.metadata.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openbravo.base.exception.OBException;

/**
 * Tests for the executor accessors of {@link CallAsyncProcess}, the pool where the background processes run.
 */
class CallAsyncProcessExecutorTest {

    @Test
    void theExecutorCanBeReplacedAndReadBack() {
        CallAsyncProcess process = CallAsyncProcess.getInstance();
        ExecutorService original = process.getExecutorService();
        ExecutorService replacement = Mockito.mock(ExecutorService.class);

        try {
            process.setExecutorService(replacement);

            assertSame(replacement, process.getExecutorService());
        } finally {
            process.setExecutorService(original);
        }
    }

    @Test
    void aNullExecutorIsRejectedAndTheCurrentOneIsKept() {
        CallAsyncProcess process = CallAsyncProcess.getInstance();
        ExecutorService current = process.getExecutorService();

        OBException error = assertThrows(OBException.class, () -> process.setExecutorService(null));

        assertEquals("ExecutorService cannot be null", error.getMessage());
        assertSame(current, process.getExecutorService());
    }
}

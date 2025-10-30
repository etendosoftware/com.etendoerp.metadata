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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * Tests for ServletRegistry utility methods that can be executed without complex framework setup.
 */
class ServletRegistryMethodTest {

    /**
     * Tests ServletRegistry with null path info throws NotFoundException.
     */
    @Test
    void getDelegatedServletWithNullPathInfoThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () -> {
            ServletRegistry.getDelegatedServlet(null, null);
        });
    }

    /**
     * Tests ServletRegistry with non-existent servlet path throws NotFoundException.
     */
    @Test
    void getDelegatedServletWithNonExistentPathThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () -> {
            ServletRegistry.getDelegatedServlet(null, "/non-existent-servlet-path");
        });
    }

    /**
     * Tests ServletRegistry with forward path structure.
     */
    @Test
    void getDelegatedServletWithForwardPathHandlesCorrectly() {
        assertThrows(NotFoundException.class, () -> {
            ServletRegistry.getDelegatedServlet(null, "/forward/test");
        });
    }

    /**
     * Tests ServletRegistry with empty path.
     */
    @Test
    void getDelegatedServletWithEmptyPathThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () -> {
            ServletRegistry.getDelegatedServlet(null, "");
        });
    }

    /**
     * Tests ServletRegistry exception handling for invalid servlet class.
     */
    @Test
    void getDelegatedServletWithInvalidClassThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () -> {
            ServletRegistry.getDelegatedServlet(null, "/invalid-class-path");
        });
    }
}
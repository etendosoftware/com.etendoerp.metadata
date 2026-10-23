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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.DELETE;
import static com.etendoerp.metadata.utils.Constants.GET;
import static com.etendoerp.metadata.utils.Constants.POST;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.etendoerp.metadata.exceptions.MethodNotAllowedException;

/**
 * Unit tests of the remaining behavior of {@link FormPersonalizationService}.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FormPersonalizationServiceMiscTest extends FormPersonalizationServiceTestBase {

    /**
     * Methods other than GET, POST and DELETE are not allowed.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = MethodNotAllowedException.class)
    public void testUnsupportedMethodThrowsMethodNotAllowed() throws IOException {
        when(mockRequest.getMethod()).thenReturn("PUT");

        service.process();
    }

    /** The scope of a stored personalization is the most specific level it is bound to. */
    @Test
    public void testDeriveScope() {
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);
        assertEquals("USER", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(mockClient, mockOrg, mockRole, null);
        assertEquals("ROLE", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(mockClient, mockOrg, null, null);
        assertEquals("ORGANIZATION", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(mockClient, null, null, null);
        assertEquals("CLIENT", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(clientWithId(SYSTEM_ID), null, null, null);
        assertEquals("SYSTEM", FormPersonalizationService.deriveScope(mockPersonalization));

        givenStoredAt(null, null, null, null);
        assertEquals("SYSTEM", FormPersonalizationService.deriveScope(mockPersonalization));
    }
}

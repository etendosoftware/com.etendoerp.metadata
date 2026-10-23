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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.client.application.ApplicationUtils;

import com.etendoerp.metadata.exceptions.ForbiddenException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Unit tests of the checks {@link FormPersonalizationService} makes when discarding a personalization.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FormPersonalizationServiceDeleteTest extends FormPersonalizationServiceTestBase {

    /**
     * The id of the personalization is required.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testDeleteWithoutIdRejected() throws IOException {
        givenDelete(null);

        service.process();
    }

    /**
     * Discarding something already gone is a success.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testDeleteUnknownIdIsASuccess() throws Exception {
        givenDelete(OTHER_ID);

        service.process();

        assertEquals("success", response().getString("result"));
        verify(mockOBDal, never()).remove(any());
    }

    /**
     * Only form personalizations can be discarded through this service.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testDeleteRejectsOtherPersonalizationTypes() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        when(mockPersonalization.getType()).thenReturn("Window");

        service.process();
    }

    /**
     * A user can discard their own personalization.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testDeleteOwnPersonalization() throws Exception {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
        verify(mockOBDal).flush();
        assertEquals("success", response().getString("result"));
    }

    /**
     * Nobody can discard another user's personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteAnotherUsersPersonalizationRejected() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, mockOrg, mockRole, userWithId(OTHER_ID));

        service.process();
    }

    /**
     * A shared role personalization needs the administrator of that role.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteSharedRolePersonalizationRejectedForRegularSession() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, null, mockRole, null);

        service.process();
    }

    /**
     * The administrator of a role can discard its shared personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteSharedRolePersonalizationAllowedForRoleAdministrator() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, null, roleWithId(OTHER_ID), null);
        givenAdminOfRole(OTHER_ID);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }

    /**
     * The administrator of an organization can discard its shared personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteSharedOrganizationPersonalizationAllowedForOrganizationAdministrator() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, orgWithId(ORG_ID), null, null);
        givenAdminOfOrganization(ORG_ID);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }

    /**
     * An organization personalization of an organization the session does not administer is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteSharedOrganizationPersonalizationRejectedForAnotherOrganization() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, orgWithId(OTHER_ID), null, null);
        givenAdminOfOrganization(ORG_ID);

        service.process();
    }

    /**
     * A client administrator can discard the personalization of the whole client.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteClientPersonalizationAllowedForClientAdministrator() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(mockClient, null, null, null);
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }

    /**
     * A system-wide personalization is only editable by the system role.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testDeleteSystemPersonalizationRejectedForOtherRoles() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(null, null, null, null);
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);

        service.process();
    }

    /**
     * The system role can discard a system-wide personalization.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testDeleteSystemPersonalizationAllowedForSystemRole() throws IOException {
        givenDelete(PERSONALIZATION_ID);
        givenStoredAt(clientWithId(SYSTEM_ID), null, null, null);
        when(mockRole.getId()).thenReturn(SYSTEM_ID);

        service.process();

        verify(mockOBDal).remove(mockPersonalization);
    }
}

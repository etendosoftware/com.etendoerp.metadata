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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * Unit tests of the levels {@link FormPersonalizationService} accepts on a save and the checks it makes.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FormPersonalizationServicePostTest extends FormPersonalizationServiceTestBase {

    /**
     * Saving without a scope stores the personalization for the current user, bound to the current
     * client, organization and role like Classic's user level.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testPostStoresAtUserLevelByDefault() throws Exception {
        givenPost("{" + TAB_FIELD + "," + FORM_BODY + "}");
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), eq(ORG_ID), eq(ROLE_ID), eq(USER_ID),
            eq(TAB_ID), isNull(), eq("form"), any(), eq(false));
        verify(mockOBDal).flush();
        assertEquals("USER", response().getJSONObject(PERSONALIZATION_KEY).getString(SCOPE_KEY));
    }

    /**
     * The tab can also come in the query string.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostTakesTheTabFromTheQueryString() throws IOException {
        givenPost("{" + FORM_BODY + "}");
        when(mockRequest.getParameter(TAB_PARAM)).thenReturn(TAB_ID);

        service.process();

        verify(mockHandler).storePersonalization(any(), any(), any(), any(), any(), eq(TAB_ID), any(), any(), any(),
            anyBoolean());
    }

    /**
     * A role administrator can personalize for a role they administer.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostRoleScopeAllowedForRoleAdministrator() throws IOException {
        givenAdminOfRole(OTHER_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ROLE\",\"role\":\"" + OTHER_ID + "\"," + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), isNull(), eq(OTHER_ID), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * Without a role to administer, the role level is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostRoleScopeRejectedForRegularSession() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ROLE\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * A role the session does not administer is rejected even if it administers another one.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostRoleScopeRejectedForAnotherRole() throws IOException {
        givenAdminOfRole(OTHER_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ROLE\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * An organization administrator can personalize for their organization, which is the default target.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostOrganizationScopeAllowedForOrganizationAdministrator() throws IOException {
        givenAdminOfOrganization(ORG_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ORGANIZATION\"," + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), eq(ORG_ID), isNull(), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * An organization the session does not administer is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostOrganizationScopeRejectedForAnotherOrganization() throws IOException {
        givenAdminOfOrganization(ORG_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"ORGANIZATION\",\"organization\":\"" + OTHER_ID + "\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * A client administrator can personalize for the whole client.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostClientScopeAllowedForClientAdministrator() throws IOException {
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);
        givenPost("{" + TAB_FIELD + CLIENT_SCOPE_FIELD + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), eq(CLIENT_ID), isNull(), isNull(), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * Anyone else is rejected at the client level.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostClientScopeRejectedForRegularSession() throws IOException {
        givenPost("{" + TAB_FIELD + CLIENT_SCOPE_FIELD + FORM_BODY + "}");

        service.process();
    }

    /**
     * Only the system role can personalize for the whole system, with no level set.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPostSystemScopeAllowedForSystemRole() throws IOException {
        when(mockRole.getId()).thenReturn(SYSTEM_ID);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"system\"," + FORM_BODY + "}");

        service.process();

        verify(mockHandler).storePersonalization(isNull(), isNull(), isNull(), isNull(), isNull(), eq(TAB_ID),
            isNull(), eq("form"), any(), eq(false));
    }

    /**
     * Any other role is rejected at the system level.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostSystemScopeRejectedForOtherRoles() throws IOException {
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);
        givenPost("{" + TAB_FIELD + ",\"scope\":\"SYSTEM\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * An unknown scope is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostInvalidScopeRejected() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"scope\":\"EVERYONE\"," + FORM_BODY + "}");

        service.process();
    }

    /**
     * The form must be sent.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostWithoutFormRejected() throws IOException {
        givenPost("{" + TAB_FIELD + "}");

        service.process();
    }

    /**
     * The form must have a list of fields.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostWithoutFieldsRejected() throws IOException {
        givenPost("{" + TAB_FIELD + ",\"form\":{\"other\":1}}");

        service.process();
    }

    /**
     * A body that is not JSON is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostInvalidJsonRejected() throws IOException {
        givenPost("not json");

        service.process();
    }

    /**
     * Nothing is stored when the request is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testRejectedPostStoresNothing() throws IOException {
        givenPost("{" + TAB_FIELD + CLIENT_SCOPE_FIELD + FORM_BODY + "}");

        try {
            service.process();
        } catch (ForbiddenException expected) {
            verify(mockHandler, never()).storePersonalization(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyBoolean());
            return;
        }
        throw new AssertionError("The request should have been rejected");
    }
}

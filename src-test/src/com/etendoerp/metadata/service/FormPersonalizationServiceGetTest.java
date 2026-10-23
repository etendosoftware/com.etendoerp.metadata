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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.client.application.ApplicationUtils;

import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Unit tests of what {@link FormPersonalizationService} returns on a read.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FormPersonalizationServiceGetTest extends FormPersonalizationServiceTestBase {

    /**
     * Without a personalization the form uses the layout defined in the dictionary.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetWithoutPersonalization() throws Exception {
        givenGet(TAB_ID);

        service.process();

        JSONObject body = response();
        assertEquals(TAB_ID, body.getString(TAB_PARAM));
        assertTrue(body.isNull(PERSONALIZATION_KEY));
        assertEquals("[\"USER\"]", body.getJSONObject(PERMISSIONS_KEY).getJSONArray("scopes").toString());
    }

    /**
     * The personalization that applies is returned with its scope, form and whether it can be edited.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetReturnsThePersonalizationThatApplies() throws Exception {
        givenGet(TAB_ID);
        givenStoredAt(mockClient, mockOrg, mockRole, mockUser);
        when(mockHandler.getPersonalizationForTab(mockTab)).thenReturn(mockPersonalization);

        service.process();

        JSONObject personalization = response().getJSONObject(PERSONALIZATION_KEY);
        assertEquals(PERSONALIZATION_ID, personalization.getString("id"));
        assertEquals("USER", personalization.getString(SCOPE_KEY));
        assertTrue(personalization.getBoolean("canEdit"));
        assertEquals("a", personalization.getJSONObject("form").getJSONArray("fields").getJSONObject(0).getString("name"));
    }

    /**
     * A record without form data (for example one that only holds other personalization) is ignored.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetIgnoresARecordWithoutForm() throws Exception {
        givenGet(TAB_ID);
        when(mockPersonalization.getValue()).thenReturn("{\"views\":{}}");
        when(mockHandler.getPersonalizationForTab(mockTab)).thenReturn(mockPersonalization);

        service.process();

        assertTrue(response().isNull(PERSONALIZATION_KEY));
    }

    /**
     * A shared personalization cannot be edited by a session that does not administer it.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetSharedPersonalizationIsReadOnlyForRegularSession() throws Exception {
        givenGet(TAB_ID);
        givenStoredAt(mockClient, null, mockRole, null);
        when(mockHandler.getPersonalizationForTab(mockTab)).thenReturn(mockPersonalization);

        service.process();

        JSONObject personalization = response().getJSONObject(PERSONALIZATION_KEY);
        assertEquals("ROLE", personalization.getString(SCOPE_KEY));
        assertFalse(personalization.getBoolean("canEdit"));
    }

    /**
     * Administrators are offered the levels they administer, with the roles and organizations.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetOffersTheLevelsTheSessionAdministers() throws Exception {
        givenGet(TAB_ID);
        givenAdminOfRole(ROLE_ID);
        givenAdminOfOrganization(ORG_ID);
        appUtilsMock.when(ApplicationUtils::isClientAdmin).thenReturn(true);
        when(mockRole.getId()).thenReturn(SYSTEM_ID);

        service.process();

        JSONObject permissions = response().getJSONObject(PERMISSIONS_KEY);
        assertEquals("[\"USER\",\"ROLE\",\"ORGANIZATION\",\"CLIENT\",\"SYSTEM\"]", permissions.getJSONArray("scopes").toString());
        assertEquals(ROLE_ID, permissions.getJSONArray("roles").getJSONObject(0).getString("id"));
        assertEquals(ORG_ID, permissions.getJSONArray("organizations").getJSONObject(0).getString("id"));
    }

    /**
     * The tab is required.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testGetWithoutTabRejected() throws IOException {
        givenGet(null);

        service.process();
    }

    /**
     * An unknown tab is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testGetWithUnknownTabRejected() throws IOException {
        givenGet(OTHER_ID);

        service.process();
    }
}

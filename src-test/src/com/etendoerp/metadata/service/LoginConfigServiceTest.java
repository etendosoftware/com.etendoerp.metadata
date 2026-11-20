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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedStatic.Verification;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;

/**
 * Tests {@link LoginConfigService}: the System-level {@code ForcedLoginURL} the login screen applies.
 */
class LoginConfigServiceTest {

    private static final String FORCED_URL = "https://ui.example.com/";

    private MockedStatic<Preferences> preferences;
    private MockedStatic<OBContext> obContext;

    /**
     * Mocks the preference lookup and the admin mode switches.
     */
    @BeforeEach
    void setUp() {
        preferences = mockStatic(Preferences.class);
        obContext = mockStatic(OBContext.class);
    }

    /**
     * Closes the static mocks.
     */
    @AfterEach
    void tearDown() {
        preferences.close();
        obContext.close();
    }

    /** The System-level lookup of ForcedLoginURL, as classic Login.java does it. */
    private static final Verification FORCED_LOGIN_URL_LOOKUP = () -> Preferences.getPreferenceValue(
            eq(LoginConfigService.FORCED_LOGIN_URL_PREFERENCE), eq(true), (String) isNull(), isNull(), isNull(),
            isNull(), isNull());

    /**
     * Stubs the System-level lookup of ForcedLoginURL.
     *
     * @param value the value the preference holds
     */
    private void stubForcedLoginUrl(String value) {
        preferences.when(FORCED_LOGIN_URL_LOOKUP).thenReturn(value);
    }

    /**
     * The value stored at System level is answered.
     */
    @Test
    void readForcedLoginUrlAnswersTheSystemValue() {
        stubForcedLoginUrl(FORCED_URL);

        assertEquals(FORCED_URL, LoginConfigService.readForcedLoginUrl());
    }

    /**
     * An undefined or conflicting preference means the standard login.
     */
    @Test
    void readForcedLoginUrlIsEmptyWithoutThePreference() {
        preferences.when(FORCED_LOGIN_URL_LOOKUP).thenThrow(new PropertyException());

        assertEquals("", LoginConfigService.readForcedLoginUrl());
    }

    /**
     * The service answers the value as JSON.
     *
     * @throws Exception if the service fails or the response cannot be parsed
     */
    @Test
    void processWritesTheForcedLoginUrl() throws Exception {
        stubForcedLoginUrl(FORCED_URL);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        new LoginConfigService(mock(HttpServletRequest.class), response).process();

        assertEquals(FORCED_URL, new JSONObject(body.toString()).getString(LoginConfigService.FORCED_LOGIN_URL));
    }
}

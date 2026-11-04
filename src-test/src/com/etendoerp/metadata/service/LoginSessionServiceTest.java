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

package com.etendoerp.metadata.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.access.User;

import com.etendoerp.metadata.auth.LoginSessionRegistry;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;

/**
 * Test class for {@link LoginSessionService}.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginSessionServiceTest extends AbstractProfileServiceTest {
    private static final String USERNAME = "test-user";
    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "session-1";

    private MockedStatic<LoginSessionRegistry> registryMock;

    /**
     * Initializes the {@link LoginSessionRegistry} static mock, on top of the shared mocks from
     * {@link AbstractProfileServiceTest}.
     */
    @Before
    public void setUpRegistryMock() {
        registryMock = mockStatic(LoginSessionRegistry.class);
    }

    /**
     * Releases the static mock opened in {@link #setUpRegistryMock()}.
     */
    @After
    public void tearDownRegistryMock() {
        registryMock.close();
    }

    /**
     * A GET request must be rejected with {@link MethodNotAllowedException}.
     *
     * @throws Exception if the service fails with an unexpected exception
     */
    @Test(expected = MethodNotAllowedException.class)
    public void testProcessThrowsMethodNotAllowedForGet() throws Exception {
        when(mockRequest.getMethod()).thenReturn("GET");

        new LoginSessionService(mockRequest, mockResponse).process();
    }

    /**
     * The successful login of the token's user is recorded and its session id returned.
     *
     * @throws Exception if the response cannot be written or parsed
     */
    @Test
    public void testProcessRegistersSuccessfulSessionOfTheTokenUser() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        StringWriter stringWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(stringWriter));
        User user = mock(User.class);
        when(user.getUsername()).thenReturn(USERNAME);
        when(user.getId()).thenReturn(USER_ID);
        when(obContext.getUser()).thenReturn(user);
        registryMock.when(() -> LoginSessionRegistry.register(mockRequest, USERNAME, USER_ID,
                LoginSessionRegistry.SUCCESS_STATUS)).thenReturn(SESSION_ID);

        new LoginSessionService(mockRequest, mockResponse).process();

        JSONObject response = new JSONObject(stringWriter.toString());
        assertEquals(SESSION_ID, response.getString(LoginSessionRegistry.SESSION_ID));
    }
}

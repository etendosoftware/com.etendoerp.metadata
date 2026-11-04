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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import com.etendoerp.metadata.auth.LoginAttemptGuard;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Test class for {@link LoginGuardService}.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginGuardServiceTest extends AbstractProfileServiceTest {
    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password"; // NOSONAR - fake value, only used to stub a mocked LoginAttemptGuard call
    private static final String CREDENTIALS_BODY = "{\"username\":\"test-user\",\"password\":\"test-password\"}";
    private static final String POST_METHOD = "POST";

    private MockedStatic<LoginAttemptGuard> guardMock;

    /**
     * Initializes the {@link LoginAttemptGuard} static mock, on top of the shared mocks from
     * {@link AbstractProfileServiceTest}.
     */
    @Before
    public void setUpGuardMock() {
        guardMock = mockStatic(LoginAttemptGuard.class);
    }

    /**
     * Releases the static mock opened in {@link #setUpGuardMock()}.
     */
    @After
    public void tearDownGuardMock() {
        guardMock.close();
    }

    /**
     * A GET request must be rejected with {@link MethodNotAllowedException}.
     *
     * @throws Exception if the service fails with an unexpected exception
     */
    @Test(expected = MethodNotAllowedException.class)
    public void testProcessThrowsMethodNotAllowedForGet() throws Exception {
        when(mockRequest.getMethod()).thenReturn("GET");

        new LoginGuardService(mockRequest, mockResponse).process();
    }

    /**
     * A request without username/password must be rejected before the guard runs, so nothing is
     * delayed or recorded.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessMissingCredentialsThrowsUnprocessable() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody("{\"username\":\"test-user\"}");

        assertProcessThrowsUnprocessable(new LoginGuardService(mockRequest, mockResponse),
                "Expected UnprocessableContentException");
        guardMock.verify(() -> LoginAttemptGuard.authenticate(any(), any(), any()), never());
    }

    /**
     * Credentials accepted by the guard are answered with an empty JSON object.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    public void testProcessAcceptedCredentialsWritesEmptyObject() throws Exception {
        StringWriter stringWriter = stubGuardRequest();
        guardMock.when(() -> LoginAttemptGuard.authenticate(mockRequest, USERNAME, PASSWORD)).thenReturn("user-1");

        new LoginGuardService(mockRequest, mockResponse).process();

        assertEquals("{}", stringWriter.toString());
    }

    /**
     * A refusal of the guard (wrong password or locked user) propagates with its Classic message.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test
    public void testProcessRefusedCredentialsThrowsUnauthorized() throws Exception {
        stubGuardRequest();
        guardMock.when(() -> LoginAttemptGuard.authenticate(mockRequest, USERNAME, PASSWORD))
                .thenThrow(new UnauthorizedException("Invalid user name or password."));

        try {
            new LoginGuardService(mockRequest, mockResponse).process();
            fail("Expected UnauthorizedException");
        } catch (UnauthorizedException expected) {
            assertEquals("Invalid user name or password.", expected.getMessage());
        }
    }

    /**
     * An unexpected failure of the guard is reported as {@link InternalServerException}.
     *
     * @throws Exception if the request body cannot be read
     */
    @Test(expected = InternalServerException.class)
    public void testProcessUnexpectedFailureThrowsInternalServerError() throws Exception {
        stubGuardRequest();
        guardMock.when(() -> LoginAttemptGuard.authenticate(mockRequest, USERNAME, PASSWORD))
                .thenThrow(new IllegalStateException("DB unavailable"));

        new LoginGuardService(mockRequest, mockResponse).process();
    }

    /**
     * Stubs a POST request with valid credentials and the response writer.
     *
     * @return the writer receiving the response body
     * @throws Exception if stubbing the mocked reader or writer fails
     */
    private StringWriter stubGuardRequest() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        setRequestBody(CREDENTIALS_BODY);
        StringWriter stringWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(stringWriter));
        return stringWriter;
    }
}

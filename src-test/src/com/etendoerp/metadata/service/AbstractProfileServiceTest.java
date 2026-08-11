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

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.StringReader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Shared mocks and helpers for the JWT-issuing services' tests
 * ({@link LoginServiceTest} and {@link ChangeProfileServiceTest}), which both stub
 * {@link OBContext}/{@link OBDal} statically and read a JSON body off a mocked request.
 */
public abstract class AbstractProfileServiceTest {

    @Mock
    protected HttpServletRequest mockRequest;

    @Mock
    protected HttpServletResponse mockResponse;

    @Mock
    protected OBContext obContext;

    @Mock
    protected OBDal obDal;

    protected MockedStatic<OBContext> obContextMock;
    protected MockedStatic<OBDal> obDalMock;
    protected MockedStatic<com.etendoerp.metadata.auth.Utils> authUtilsMock;

    /**
     * Initializes the {@code OBContext}/{@code OBDal}/{@code auth.Utils} static mocks shared by
     * every subclass test.
     */
    @Before
    public void setUpProfileServiceMocks() {
        obContextMock = mockStatic(OBContext.class);
        obDalMock = mockStatic(OBDal.class);
        authUtilsMock = mockStatic(com.etendoerp.metadata.auth.Utils.class);

        obContextMock.when(OBContext::getOBContext).thenReturn(obContext);
        obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    }

    /**
     * Releases the static mocks opened in {@link #setUpProfileServiceMocks()}.
     */
    @After
    public void tearDownProfileServiceMocks() {
        authUtilsMock.close();
        obDalMock.close();
        obContextMock.close();
    }

    /**
     * Stubs {@link #mockRequest} to return the given raw JSON body when read.
     *
     * @param body the raw JSON body
     * @throws Exception if stubbing the mocked reader fails
     */
    protected void setRequestBody(String body) throws Exception {
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    /**
     * Asserts that invoking {@code service.process()} fails with
     * {@link UnprocessableContentException}.
     *
     * @param service        the service under test
     * @param failureMessage the message to report if the expected exception is not thrown
     * @throws Exception if {@code process()} throws anything other than the expected exception
     */
    protected void assertProcessThrowsUnprocessable(MetadataService service, String failureMessage)
            throws Exception {
        try {
            service.process();
            fail(failureMessage);
        } catch (UnprocessableContentException expected) {
            // expected
        }
    }
}

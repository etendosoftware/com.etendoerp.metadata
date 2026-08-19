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

package com.etendoerp.metadata.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Test class for UnauthenticatedLoginServlet.
 */
@RunWith(MockitoJUnitRunner.class)
public class UnauthenticatedLoginServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    /**
     * Verifies that POST requests are delegated to a fresh MetadataServlet's process method with
     * a request reporting {@code getPathInfo() == "/com.etendoerp.metadata.meta/login"} - the
     * exact-path {@code @WebServlet} mapping otherwise leaves it {@code null}, which
     * {@link ServiceFactory} would 404 on since it routes purely off that value.
     *
     * @throws Exception if the servlet invocation fails
     */
    @Test
    public void testDoPostDelegatesToMetadataServletWithPathInfoForRouting() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        String[] capturedPathInfo = new String[1];

        try (MockedConstruction<MetadataServlet> construction = mockConstruction(MetadataServlet.class,
                (mock, context) -> doAnswer(invocation -> {
                    HttpServletRequest forwarded = invocation.getArgument(0);
                    capturedPathInfo[0] = forwarded.getPathInfo();
                    return null;
                }).when(mock).process(any(HttpServletRequest.class), any(HttpServletResponse.class)))) {
            UnauthenticatedLoginServlet servlet = new UnauthenticatedLoginServlet();
            servlet.service(request, response);

            assertEquals("/com.etendoerp.metadata.meta/login", capturedPathInfo[0]);
        }
    }
}

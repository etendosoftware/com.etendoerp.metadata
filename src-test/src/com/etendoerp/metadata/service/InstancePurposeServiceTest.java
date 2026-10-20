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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.window.ApplicationDictionaryCachedStructures;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_process.HeartbeatProcess;
import org.openbravo.model.ad.system.SystemInformation;

import com.etendoerp.metadata.exceptions.ForbiddenException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Unit tests for {@link InstancePurposeService}: when the configuration modal is required, who can
 * read it and change the purpose, input validation, and the Production side effect.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class InstancePurposeServiceTest {

    private static final String PATH = "/instance-purpose";
    private static final String SYSTEM_ID = "0";
    private static final String ADMIN_LEVEL = "S";
    private static final String REGULAR_LEVEL = "O";
    private static final String PURPOSE_KEY = "purpose";
    private static final String REQUIRED_KEY = "required";
    private static final String OPTIONS_KEY = "options";
    private static final String DEVELOPMENT = "D";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private OBDal mockOBDal;
    @Mock private OBContext mockOBContext;
    @Mock private SystemInformation mockSystemInfo;
    @Mock private ApplicationDictionaryCachedStructures mockAdCache;

    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<WeldUtils> weldMock;
    private MockedStatic<HeartbeatProcess> heartbeatMock;

    private StringWriter responseWriter;
    private InstancePurposeService service;

    /**
     * Initialises the static mocks and the service under test, as a system administrator on an
     * instance whose purpose is not configured yet.
     *
     * @throws IOException if the response writer cannot be configured
     */
    @Before
    public void setUp() throws IOException {
        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenAnswer(inv -> new PrintWriter(responseWriter));

        obDalMock = mockStatic(OBDal.class);
        obContextMock = mockStatic(OBContext.class);
        weldMock = mockStatic(WeldUtils.class);
        heartbeatMock = mockStatic(HeartbeatProcess.class);

        obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
        obContextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
        obContextMock.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(inv -> null);
        obContextMock.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);
        weldMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(ApplicationDictionaryCachedStructures.class))
            .thenReturn(mockAdCache);

        when(mockOBContext.getUserLevel()).thenReturn(ADMIN_LEVEL);
        when(mockOBDal.get(SystemInformation.class, SYSTEM_ID)).thenReturn(mockSystemInfo);
        when(mockSystemInfo.getInstancePurpose()).thenReturn(null);
        heartbeatMock.when(HeartbeatProcess::isShowInstancePurposeRequired).thenReturn(true);

        when(mockRequest.getPathInfo()).thenReturn(PATH);
        service = new InstancePurposeService(mockRequest, mockResponse);
    }

    /** Closes the static mocks and clears thread-local state. */
    @After
    public void tearDown() {
        if (obDalMock != null) obDalMock.close();
        if (obContextMock != null) obContextMock.close();
        if (weldMock != null) weldMock.close();
        if (heartbeatMock != null) heartbeatMock.close();
        MetadataService.clear();
    }

    private void givenPost(String body) throws IOException {
        when(mockRequest.getMethod()).thenReturn(POST);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    private JSONObject response() throws JSONException {
        return new JSONObject(responseWriter.toString());
    }

    // --- GET ---

    /**
     * Modal appearance on first login: a system administrator, no purpose configured.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetRequiredForAdministratorWithoutPurpose() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET);

        service.process();

        JSONObject body = response();
        assertTrue(body.getBoolean(REQUIRED_KEY));
        assertTrue(body.isNull(PURPOSE_KEY));
        assertEquals(4, body.getJSONArray(OPTIONS_KEY).length());
        assertEquals("P", body.getJSONArray(OPTIONS_KEY).getString(0));
    }

    /**
     * Modal suppression after configuration: the purpose is already stored.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetNotRequiredOnceConfigured() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockSystemInfo.getInstancePurpose()).thenReturn(DEVELOPMENT);
        heartbeatMock.when(HeartbeatProcess::isShowInstancePurposeRequired).thenReturn(false);

        service.process();

        JSONObject body = response();
        assertFalse(body.getBoolean(REQUIRED_KEY));
        assertEquals(DEVELOPMENT, body.getString(PURPOSE_KEY));
    }

    /**
     * A role that is not system-level never gets the modal, and Classic's check is not even run.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetNotRequiredForRegularRole() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBContext.getUserLevel()).thenReturn(REGULAR_LEVEL);

        service.process();

        assertFalse(response().getBoolean(REQUIRED_KEY));
        heartbeatMock.verify(HeartbeatProcess::isShowInstancePurposeRequired, never());
    }

    /**
     * A blank purpose is reported as not configured.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetReportsEmptyPurposeAsNull() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockSystemInfo.getInstancePurpose()).thenReturn("");

        service.process();

        assertTrue(response().isNull(PURPOSE_KEY));
    }

    /**
     * Without a system information row the purpose is simply unknown.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testGetWithoutSystemInformationRow() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockOBDal.get(SystemInformation.class, SYSTEM_ID)).thenReturn(null);

        service.process();

        assertTrue(response().isNull(PURPOSE_KEY));
    }

    // --- POST ---

    /**
     * Saves a non-production purpose without touching the dictionary cache.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testPostSavesPurpose() throws Exception {
        givenPost("{\"purpose\":\"D\"}");
        when(mockSystemInfo.getInstancePurpose()).thenReturn(DEVELOPMENT);

        service.process();

        verify(mockSystemInfo).setInstancePurpose(DEVELOPMENT);
        verify(mockOBDal).save(mockSystemInfo);
        verify(mockOBDal).flush();
        verify(mockAdCache, never()).setNotInDevelopment();
        JSONObject body = response();
        assertFalse(body.getBoolean(REQUIRED_KEY));
        assertEquals(DEVELOPMENT, body.getString(PURPOSE_KEY));
    }

    /**
     * Declaring the instance as Production also leaves development mode, as Classic does.
     *
     * @throws Exception if the response cannot be parsed
     */
    @Test
    public void testPostProductionLeavesDevelopmentMode() throws Exception {
        givenPost("{\"purpose\":\"P\"}");

        service.process();

        verify(mockSystemInfo).setInstancePurpose("P");
        verify(mockAdCache).setNotInDevelopment();
    }

    /**
     * Only system-level roles can change the purpose.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = ForbiddenException.class)
    public void testPostRejectedForRegularRole() throws IOException {
        when(mockOBContext.getUserLevel()).thenReturn(REGULAR_LEVEL);
        givenPost("{\"purpose\":\"D\"}");

        service.process();
    }

    /**
     * A value outside the accepted list is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostRejectsUnknownPurpose() throws IOException {
        givenPost("{\"purpose\":\"X\"}");

        service.process();
    }

    /**
     * A body without the purpose is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostRejectsMissingPurpose() throws IOException {
        givenPost("{}");

        service.process();
    }

    /**
     * A body that is not JSON is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPostRejectsInvalidJson() throws IOException {
        givenPost("not json");

        service.process();
    }

    /**
     * Nothing can be saved if the system information row is missing.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testPostWithoutSystemInformationRowThrowsNotFound() throws IOException {
        givenPost("{\"purpose\":\"D\"}");
        when(mockOBDal.get(SystemInformation.class, SYSTEM_ID)).thenReturn(null);

        service.process();
    }

    // --- Other ---

    /**
     * Methods other than GET and POST are not allowed.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = MethodNotAllowedException.class)
    public void testUnsupportedMethodThrowsMethodNotAllowed() throws IOException {
        when(mockRequest.getMethod()).thenReturn(DELETE);

        service.process();
    }

    /** Only the system-only user level counts as administrator, whatever the role is called. */
    @Test
    public void testIsAdministrator() {
        assertTrue(InstancePurposeService.isAdministrator("S"));
        assertTrue(InstancePurposeService.isAdministrator(" S"));
        assertFalse(InstancePurposeService.isAdministrator("SCO"));
        assertFalse(InstancePurposeService.isAdministrator("C"));
        assertFalse(InstancePurposeService.isAdministrator(REGULAR_LEVEL));
        assertFalse(InstancePurposeService.isAdministrator(null));
        assertFalse(InstancePurposeService.isAdministrator(""));
    }

    /** The accepted purposes are the four Classic ones, Production first. */
    @Test
    public void testPurposesAreTheClassicOnes() {
        assertEquals(Arrays.asList("P", "D", "T", "E"), InstancePurposeService.PURPOSES);
        assertEquals("P", InstancePurposeService.PRODUCTION);
    }
}

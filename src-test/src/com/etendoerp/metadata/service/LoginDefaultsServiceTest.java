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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.auth.LoginDefaultsResolver;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Tests for {@link LoginDefaultsService}, which serves the Classic login defaults of the
 * authenticated user.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginDefaultsServiceTest extends AbstractProfileServiceTest {
    private static final String ROLE_ID = "role-1";
    private static final String ORG_ID = "org-1";
    private static final String WAREHOUSE_ID = "wh-1";
    private static final String CLIENT_ID = "client-1";
    private static final String CURRENT = "current";
    private static final String WAREHOUSE = "warehouse";

    private MockedStatic<LoginDefaultsResolver> resolverMock;
    private StringWriter responseWriter;
    private User user;

    /**
     * Opens the {@link LoginDefaultsResolver} static mock and stubs a GET request whose response
     * body is captured.
     *
     * @throws Exception if stubbing the mocked writer fails
     */
    @Before
    public void setUpResolverMock() throws Exception {
        resolverMock = mockStatic(LoginDefaultsResolver.class);
        responseWriter = new StringWriter();
        user = mock(User.class);
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(mockRequest.getMethod()).thenReturn("GET");
        when(obContext.getUser()).thenReturn(user);
    }

    /**
     * Releases the static mock opened in {@link #setUpResolverMock()}.
     */
    @After
    public void tearDownResolverMock() {
        resolverMock.close();
    }

    /**
     * When the token already uses the Classic defaults, they are returned with {@code current}
     * set to {@code true}.
     *
     * @throws Exception if the service fails or the response is not valid JSON
     */
    @Test
    public void testProcessReturnsDefaultsMatchingTheToken() throws Exception {
        AuthData defaults = stubDefaults(mockWarehouse(WAREHOUSE_ID));
        stubContext(defaults.getRole(), defaults.getOrg(), defaults.getWarehouse());

        JSONObject result = processAndParse();

        assertEquals(ROLE_ID, result.getString("role"));
        assertEquals(ORG_ID, result.getString("organization"));
        assertEquals(WAREHOUSE_ID, result.getString(WAREHOUSE));
        assertEquals(CLIENT_ID, result.getString("client"));
        assertTrue(result.getBoolean(CURRENT));
    }

    /**
     * A token with another role is not current.
     *
     * @throws Exception if the service fails or the response is not valid JSON
     */
    @Test
    public void testProcessFlagsAnotherRoleAsNotCurrent() throws Exception {
        AuthData defaults = stubDefaults(null);
        stubContext(mockEntity(Role.class, "role-2"), defaults.getOrg(), null);

        assertFalse(processAndParse().getBoolean(CURRENT));
    }

    /**
     * A token with another warehouse is not current, and a missing default warehouse is omitted
     * from the response.
     *
     * @throws Exception if the service fails or the response is not valid JSON
     */
    @Test
    public void testProcessFlagsAnotherWarehouseAsNotCurrent() throws Exception {
        AuthData defaults = stubDefaults(null);
        stubContext(defaults.getRole(), defaults.getOrg(), mockWarehouse(WAREHOUSE_ID));

        JSONObject result = processAndParse();

        assertFalse(result.getBoolean(CURRENT));
        assertFalse(result.has(WAREHOUSE));
    }

    /**
     * A POST request must be rejected with {@link MethodNotAllowedException}.
     *
     * @throws Exception if the service fails with an unexpected exception
     */
    @Test
    public void testProcessRejectsPost() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");

        try {
            new LoginDefaultsService(mockRequest, mockResponse).process();
            fail("Expected MethodNotAllowedException");
        } catch (MethodNotAllowedException expected) {
            // expected
        }
    }

    /**
     * A user without valid Classic defaults is reported as {@link UnprocessableContentException}.
     *
     * @throws Exception if the service fails with an unexpected exception
     */
    @Test
    public void testProcessWithoutValidDefaultsThrowsUnprocessable() throws Exception {
        resolverMock.when(() -> LoginDefaultsResolver.resolve(user))
            .thenThrow(new UnprocessableContentException("No valid login defaults"));

        assertProcessThrowsUnprocessable(new LoginDefaultsService(mockRequest, mockResponse),
            "Expected UnprocessableContentException");
    }

    private AuthData stubDefaults(Warehouse warehouse) {
        AuthData defaults = new AuthData(user, mockEntity(Role.class, ROLE_ID), mockEntity(Organization.class, ORG_ID),
            warehouse, mockEntity(Client.class, CLIENT_ID));
        resolverMock.when(() -> LoginDefaultsResolver.resolve(user)).thenReturn(defaults);
        return defaults;
    }

    private void stubContext(Role role, Organization organization, Warehouse warehouse) {
        when(obContext.getRole()).thenReturn(role);
        when(obContext.getCurrentOrganization()).thenReturn(organization);
        when(obContext.getWarehouse()).thenReturn(warehouse);
    }

    private Warehouse mockWarehouse(String id) {
        return mockEntity(Warehouse.class, id);
    }

    private static <T extends org.openbravo.base.structure.BaseOBObject> T mockEntity(Class<T> entityClass,
        String id) {
        T entity = mock(entityClass);
        when(entity.getId()).thenReturn(id);
        return entity;
    }

    private JSONObject processAndParse() throws Exception {
        new LoginDefaultsService(mockRequest, mockResponse).process();
        return new JSONObject(responseWriter.toString());
    }
}

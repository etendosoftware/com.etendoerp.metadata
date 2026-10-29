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

package com.etendoerp.metadata.auth;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import javax.servlet.ServletException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;
import org.openbravo.base.secureApp.DefaultValidationException;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.LoginUtils.RoleDefaults;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Tests for {@link LoginDefaultsResolver}, which resolves the login context with the Classic rule
 * ({@link LoginUtils#getLoginDefaults}).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginDefaultsResolverTest {
    private static final String USER_ID = "user-1";
    private static final String ROLE_ID = "role-1";
    private static final String ORG_ID = "org-1";
    private static final String CLIENT_ID = "client-1";
    private static final String WAREHOUSE_ID = "wh-1";

    @Mock
    private OBDal obDal;

    @Mock
    private User user;

    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<LoginUtils> loginUtilsMock;

    /**
     * Opens the {@link OBDal} and {@link LoginUtils} static mocks.
     */
    @Before
    public void setUp() {
        obDalMock = mockStatic(OBDal.class);
        loginUtilsMock = mockStatic(LoginUtils.class);
        obDalMock.when(OBDal::getInstance).thenReturn(obDal);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getUsername()).thenReturn("user");
    }

    /**
     * Releases the static mocks opened in {@link #setUp()}.
     */
    @After
    public void tearDown() {
        loginUtilsMock.close();
        obDalMock.close();
    }

    /**
     * The Classic defaults are asked for the user's default role and every id is loaded into the
     * returned login context.
     */
    @Test
    public void testResolveLoadsEveryClassicDefault() {
        stubClassicDefaults(WAREHOUSE_ID);
        Role role = stubEntity(Role.class, ROLE_ID);
        Organization organization = stubEntity(Organization.class, ORG_ID);
        Client client = stubEntity(Client.class, CLIENT_ID);
        Warehouse warehouse = stubEntity(Warehouse.class, WAREHOUSE_ID);

        AuthData authData = LoginDefaultsResolver.resolve(user);

        assertSame(user, authData.getUser());
        assertSame(role, authData.getRole());
        assertSame(organization, authData.getOrg());
        assertSame(client, authData.getClient());
        assertSame(warehouse, authData.getWarehouse());
    }

    /**
     * An organization without warehouses leaves the warehouse empty, as in Classic.
     */
    @Test
    public void testResolveWithoutWarehouseReturnsNullWarehouse() {
        stubClassicDefaults("");

        assertNull(LoginDefaultsResolver.resolve(user).getWarehouse());
    }

    /**
     * Invalid Classic defaults are reported as {@link UnprocessableContentException}.
     */
    @Test
    public void testResolveInvalidDefaultsThrowsUnprocessable() {
        stubClassicDefaultsFailure(new DefaultValidationException("Role", "Role"));

        assertResolveThrowsUnprocessable();
    }

    /**
     * A database failure while reading the Classic defaults is reported as
     * {@link UnprocessableContentException}.
     */
    @Test
    public void testResolveServletExceptionThrowsUnprocessable() {
        stubClassicDefaultsFailure(new ServletException("db error"));

        assertResolveThrowsUnprocessable();
    }

    private void stubClassicDefaults(String warehouseId) {
        RoleDefaults defaults = new RoleDefaults();
        defaults.role = ROLE_ID;
        defaults.org = ORG_ID;
        defaults.client = CLIENT_ID;
        defaults.warehouse = warehouseId;
        whenClassicDefaultsRequested().thenReturn(defaults);
    }

    private void stubClassicDefaultsFailure(Exception failure) {
        whenClassicDefaultsRequested().thenThrow(failure);
    }

    /**
     * Starts stubbing the Classic defaults request for the user's default role.
     *
     * @return the stubbing of {@link LoginUtils#getLoginDefaults}
     */
    private OngoingStubbing<RoleDefaults> whenClassicDefaultsRequested() {
        return loginUtilsMock.when(
            () -> LoginUtils.getLoginDefaults(eq(USER_ID), eq(""), any(ConnectionProvider.class)));
    }

    private <T> T stubEntity(Class<T> entityClass, String id) {
        T entity = mock(entityClass);
        when(obDal.get(entityClass, id)).thenReturn(entity);
        return entity;
    }

    /**
     * Asserts that resolving the user's defaults fails with {@link UnprocessableContentException}
     * naming the user.
     */
    private void assertResolveThrowsUnprocessable() {
        try {
            LoginDefaultsResolver.resolve(user);
            fail("Expected UnprocessableContentException");
        } catch (UnprocessableContentException expected) {
            assertTrue(expected.getMessage().contains("user"));
        }
    }
}

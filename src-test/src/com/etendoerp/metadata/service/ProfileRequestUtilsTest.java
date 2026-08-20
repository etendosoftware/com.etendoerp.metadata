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

import static com.etendoerp.metadata.MetadataTestConstants.ORG_ID;
import static com.etendoerp.metadata.MetadataTestConstants.WAREHOUSE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Tests for {@link ProfileRequestUtils}, the body-reading helper shared by {@link LoginService} and
 * {@link ChangeProfileService}.
 * <p>
 * The DAL is replaced by a static mock, so no record ever has to exist for a lookup to be exercised.
 */
@ExtendWith(MockitoExtension.class)
class ProfileRequestUtilsTest {

    private static final String ORGANIZATION_KEY = "organization";
    private static final String WAREHOUSE_KEY = "warehouse";
    private static final String UNKNOWN_ID = "no-such-record";

    /**
     * Builds a one-entry request body.
     *
     * @param key   the property name
     * @param value the property value
     * @return the body
     * @throws JSONException if the body cannot be built
     */
    private JSONObject body(String key, Object value) throws JSONException {
        JSONObject json = new JSONObject();
        json.put(key, value);
        return json;
    }

    /**
     * Runs the given assertions with {@link OBDal#getInstance()} answering a mock, which is what the
     * two resolvers reach for.
     *
     * @param assertions receives the stubbed DAL
     */
    private void withDal(Consumer<OBDal> assertions) {
        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            assertions.accept(obDal);
        }
    }

    /**
     * A key nobody sent is absent, not empty.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void getStringOrNullReturnsNullWhenKeyIsAbsent() throws JSONException {
        assertNull(ProfileRequestUtils.getStringOrNull(body(WAREHOUSE_KEY, WAREHOUSE_ID), ORGANIZATION_KEY));
    }

    /**
     * An explicit JSON null means "no value", the same as not sending the key at all.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void getStringOrNullReturnsNullWhenValueIsJsonNull() throws JSONException {
        assertNull(ProfileRequestUtils.getStringOrNull(body(ORGANIZATION_KEY, JSONObject.NULL), ORGANIZATION_KEY));
    }

    /**
     * An empty string is a cleared field in the UI, so it must not be taken for an id.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void getStringOrNullReturnsNullWhenValueIsEmpty() throws JSONException {
        assertNull(ProfileRequestUtils.getStringOrNull(body(ORGANIZATION_KEY, ""), ORGANIZATION_KEY));
    }

    /**
     * A populated key is returned verbatim.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void getStringOrNullReturnsTheValue() throws JSONException {
        assertEquals(ORG_ID, ProfileRequestUtils.getStringOrNull(body(ORGANIZATION_KEY, ORG_ID), ORGANIZATION_KEY));
    }

    /**
     * The defensive catch: a body whose read fails is treated as "no value" rather than propagating.
     * A plain {@link JSONObject} cannot reach it — Jettison's {@code getString} stringifies whatever
     * it finds — so the failure is injected through a mocked body.
     *
     * @throws JSONException never, declared because the stubbed getter is declared to throw it
     */
    @Test
    void getStringOrNullReturnsNullWhenTheValueCannotBeRead() throws JSONException {
        JSONObject unreadable = mock(JSONObject.class);
        when(unreadable.has(ORGANIZATION_KEY)).thenReturn(true);
        when(unreadable.isNull(ORGANIZATION_KEY)).thenReturn(false);
        when(unreadable.getString(ORGANIZATION_KEY)).thenThrow(new JSONException("unreadable value"));

        assertNull(ProfileRequestUtils.getStringOrNull(unreadable, ORGANIZATION_KEY));
    }

    /**
     * Omitting the organization is legal: the caller keeps the session's current one.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void resolveOrganizationReturnsNullWhenKeyIsAbsent() throws JSONException {
        assertNull(ProfileRequestUtils.resolveOrganization(new JSONObject(), ORGANIZATION_KEY));
    }

    /**
     * A known id resolves to its record.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void resolveOrganizationReturnsTheRecord() throws JSONException {
        JSONObject request = body(ORGANIZATION_KEY, ORG_ID);
        Organization organization = mock(Organization.class);

        withDal(obDal -> {
            when(obDal.get(Organization.class, ORG_ID)).thenReturn(organization);
            assertSame(organization, ProfileRequestUtils.resolveOrganization(request, ORGANIZATION_KEY));
        });
    }

    /**
     * An id that resolves to nothing is a 422, not a silent null: minting a token for an
     * organization that does not exist would hide the mistake until much later.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void resolveOrganizationRejectsAnUnknownId() throws JSONException {
        JSONObject request = body(ORGANIZATION_KEY, UNKNOWN_ID);

        withDal(obDal -> {
            when(obDal.get(Organization.class, UNKNOWN_ID)).thenReturn(null);
            UnprocessableContentException exception = assertThrows(UnprocessableContentException.class,
                    () -> ProfileRequestUtils.resolveOrganization(request, ORGANIZATION_KEY));
            assertTrue(exception.getMessage().contains(UNKNOWN_ID));
        });
    }

    /**
     * Omitting the warehouse is legal, exactly as for the organization.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void resolveWarehouseReturnsNullWhenKeyIsAbsent() throws JSONException {
        assertNull(ProfileRequestUtils.resolveWarehouse(new JSONObject(), WAREHOUSE_KEY));
    }

    /**
     * A known id resolves to its record.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void resolveWarehouseReturnsTheRecord() throws JSONException {
        JSONObject request = body(WAREHOUSE_KEY, WAREHOUSE_ID);
        Warehouse warehouse = mock(Warehouse.class);

        withDal(obDal -> {
            when(obDal.get(Warehouse.class, WAREHOUSE_ID)).thenReturn(warehouse);
            assertSame(warehouse, ProfileRequestUtils.resolveWarehouse(request, WAREHOUSE_KEY));
        });
    }

    /**
     * An unknown warehouse id is reported the same way an unknown organization is.
     *
     * @throws JSONException if the body cannot be built
     */
    @Test
    void resolveWarehouseRejectsAnUnknownId() throws JSONException {
        JSONObject request = body(WAREHOUSE_KEY, UNKNOWN_ID);

        withDal(obDal -> {
            when(obDal.get(Warehouse.class, UNKNOWN_ID)).thenReturn(null);
            UnprocessableContentException exception = assertThrows(UnprocessableContentException.class,
                    () -> ProfileRequestUtils.resolveWarehouse(request, WAREHOUSE_KEY));
            assertTrue(exception.getMessage().contains(UNKNOWN_ID));
        });
    }

    /**
     * The class only publishes static helpers, so its constructor stays private.
     *
     * @throws Exception if the constructor cannot be reached by reflection
     */
    @Test
    void constructorIsPrivate() throws Exception {
        Constructor<ProfileRequestUtils> constructor = ProfileRequestUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()), "Constructor should be private");

        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}

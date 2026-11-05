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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Collections;

import org.mockito.MockedStatic;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.base.structure.OrganizationEnabled;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Shared Mockito stubbing helpers for the record-level authorization added to
 * {@link AttachmentsServlet}.
 *
 * <p>Every {@code AttachmentsServlet*Test} class needs to simulate the same DAL/
 * {@link ModelProvider} lookup chain ({@code Table -> Entity -> owning record}) to
 * exercise {@code assertRecordAccess}. Centralizing it here keeps that chain defined
 * once instead of duplicated inline in each test method/class.</p>
 */
final class AttachmentsAccessStubs {

    static final String TABLE_C_ORDER = "C_Order";
    static final String ENTITY_ORDER = "Order";
    static final String MSG_ACCESS_DENIED = "Access denied";
    static final String CONTENT_DISPOSITION_TOKEN = "content-disposition";
    static final String UPLOAD_CONTENT_DISPOSITION = "form-data; name=\"file\"; filename=\"test.txt\"";

    private AttachmentsAccessStubs() {
        // utility class
    }

    /**
     * Stubs the {@code Table -> Entity} resolution step of {@code assertRecordAccess}:
     * {@code table.getDBTableName()} resolves to {@link #TABLE_C_ORDER}, and
     * {@link ModelProvider#getEntityByTableName(String)} resolves it to an entity named
     * {@link #ENTITY_ORDER}.
     */
    static void stubEntityResolution(Table mockTable, MockedStatic<ModelProvider> modelProviderMock,
            ModelProvider mockModelProvider, Entity mockEntity) {
        when(mockTable.getDBTableName()).thenReturn(TABLE_C_ORDER);
        modelProviderMock.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
        when(mockModelProvider.getEntityByTableName(TABLE_C_ORDER)).thenReturn(mockEntity);
        when(mockEntity.getName()).thenReturn(ENTITY_ORDER);
    }

    /**
     * Stubs the owning record as not found for {@code recordId}, so record-level
     * authorization denies access (DAL row-level security hid it, or it does not exist).
     */
    static void stubRecordNotFound(OBDal mockDal, String recordId) {
        when(mockDal.get(ENTITY_ORDER, recordId)).thenReturn(null);
    }

    /**
     * Stubs the owning record as visible (found) for {@code recordId}, with no claim on
     * its organization's writability. Sufficient for read-only authorization checks.
     */
    static BaseOBObject stubRecordVisible(OBDal mockDal, String recordId) {
        BaseOBObject ownerRecord = mock(BaseOBObject.class);
        when(mockDal.get(ENTITY_ORDER, recordId)).thenReturn(ownerRecord);
        return ownerRecord;
    }

    /**
     * Stubs the owning record as visible and belonging to an organization that IS among
     * the caller's writable organizations, so write-authorization (upload/edit/delete)
     * checks pass.
     */
    static BaseOBObject stubRecordWritable(OBDal mockDal, OBContext mockContext, String recordId,
            Organization mockOrganization, String orgId) {
        BaseOBObject ownerRecord = stubRecordWithOrganization(mockDal, recordId, mockOrganization, orgId);
        when(mockContext.getWritableOrganizations()).thenReturn(Collections.singleton(orgId));
        return ownerRecord;
    }

    /**
     * Stubs the owning record as visible but belonging to an organization that is NOT
     * among the caller's writable organizations, so write-authorization checks fail.
     */
    static BaseOBObject stubRecordNotWritable(OBDal mockDal, OBContext mockContext, String recordId,
            Organization mockOrganization, String orgId) {
        BaseOBObject ownerRecord = stubRecordWithOrganization(mockDal, recordId, mockOrganization, orgId);
        when(mockContext.getWritableOrganizations()).thenReturn(Collections.emptySet());
        return ownerRecord;
    }

    private static BaseOBObject stubRecordWithOrganization(OBDal mockDal, String recordId,
            Organization mockOrganization, String orgId) {
        BaseOBObject ownerRecord = mock(BaseOBObject.class, withSettings().extraInterfaces(OrganizationEnabled.class));
        when(((OrganizationEnabled) ownerRecord).getOrganization()).thenReturn(mockOrganization);
        when(mockOrganization.getId()).thenReturn(orgId);
        when(mockDal.get(ENTITY_ORDER, recordId)).thenReturn(ownerRecord);
        return ownerRecord;
    }
}

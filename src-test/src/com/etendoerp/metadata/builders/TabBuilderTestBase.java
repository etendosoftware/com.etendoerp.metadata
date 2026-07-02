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
package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.codehaus.jettison.json.JSONObject;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.openbravo.base.model.Entity;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.data.TabProcessor;

/**
 * Shared mock setup and execution helpers for {@link TabBuilder} unit tests.
 * <p>
 * Extracted out of {@link TabBuilderTest} so that class stays under the
 * SonarQube method-count threshold without duplicating mocking boilerplate.
 */
abstract class TabBuilderTestBase {

    protected static final String CREATED_ID = "Created";
    protected static final String CREATED_BY_ID = "CreatedBy";
    protected static final String UPDATED_ID = "Updated";
    protected static final String UPDATED_BY_ID = "UpdatedBy";

    protected static final String CREATION_DATE_NAME = "Creation Date";
    protected static final String CREATED_BY_NAME = "Created By";
    protected static final String UPDATED_NAME = "Updated";
    protected static final String UPDATED_BY_NAME = "Updated By";

    protected static final String TEST_TABLE_NAME = "TestTable";
    protected static final String TEST_DESCRIPTION = "Test description";
    protected static final String TEST_HELP = "Test help";
    protected static final String ENTITY_NAME_KEY = "entityName";

    /**
     * Executes a TabBuilder test with common mock setup
     */
    protected void executeTabBuilderTest(OBContext mockContext, KernelUtils mockKernelUtils,
            Tab mockTab, JSONObject tabFields,
            Consumer<JSONObject> assertions) {
        executeTabBuilderTest(mockContext, mockKernelUtils, mockTab, tabFields, false, null, null, assertions);
    }

    protected void executeTabBuilderTest(OBContext mockContext, KernelUtils mockKernelUtils,
            Tab mockTab, JSONObject tabFields,
            boolean isWindowReadOnly, TabAccess tabAccess,
            Consumer<JSONObject> assertions) {
        executeTabBuilderTest(mockContext, mockKernelUtils, mockTab, tabFields, isWindowReadOnly, tabAccess, null,
                assertions);
    }

    @SuppressWarnings("java:S107")
    protected void executeTabBuilderTest(OBContext mockContext, KernelUtils mockKernelUtils,
            Tab mockTab, JSONObject tabFields,
            boolean isWindowReadOnly, TabAccess tabAccess,
            Consumer<MockedStatic<TabProcessor>> extraMocking,
            Consumer<JSONObject> assertions) {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject tabJson = new JSONObject();
                            tabJson.put(ENTITY_NAME_KEY, TEST_TABLE_NAME);
                            when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);

            // Mock both because getFields() might fall back to tab if access is empty
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(any(Tab.class))).thenReturn(tabFields);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(any(TabAccess.class))).thenReturn(tabFields);

            if (extraMocking != null) {
                extraMocking.accept(mockedTabProcessor);
            }

            TabBuilder tabBuilder = new TabBuilder(mockTab, tabAccess, isWindowReadOnly);
            JSONObject result = tabBuilder.toJSON();

            assertions.accept(result);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            fail("Unexpected exception: " + msg);
        }
    }

    /**
     * Executes a TabBuilder test using the 4-arg constructor with a pre-loaded field access list,
     * as used by {@link WindowBuilder} to avoid per-tab field access queries.
     */
    protected void executeTabBuilderTestWithPreloadedFieldAccess(OBContext mockContext, KernelUtils mockKernelUtils,
            Tab mockTab, JSONObject tabFields, TabAccess tabAccess, List<FieldAccess> preloadedFieldAccessList,
            Consumer<JSONObject> assertions) {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject tabJson = new JSONObject();
                            tabJson.put(ENTITY_NAME_KEY, TEST_TABLE_NAME);
                            when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);

            mockedTabProcessor.when(() -> TabProcessor.getTabFields(any(Tab.class))).thenReturn(tabFields);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(any(TabAccess.class), any(List.class)))
                    .thenReturn(tabFields);

            TabBuilder tabBuilder = new TabBuilder(mockTab, tabAccess, false, preloadedFieldAccessList);
            JSONObject result = tabBuilder.toJSON();

            assertions.accept(result);

            verify(tabAccess, never()).getADFieldAccessList();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            fail("Unexpected exception: " + msg);
        }
    }

    protected TestContext setupTestContext() {
        OBContext mockContext = mock(OBContext.class);
        return new TestContext(mockContext, mock(Language.class), mock(Tab.class), mock(Table.class),
                mock(KernelUtils.class));
    }

    protected Column createMockColumn(String id, String dbName, String name) {
        Column column = mock(Column.class);
        when(column.getId()).thenReturn(id);
        when(column.getDBColumnName()).thenReturn(dbName);
        when(column.getName()).thenReturn(name);
        when(column.getDescription()).thenReturn(TEST_DESCRIPTION);
        when(column.getHelpComment()).thenReturn(TEST_HELP);
        when(column.isMandatory()).thenReturn(true);
        when(column.getIdentifier()).thenReturn(name);
        return column;
    }

    protected Column createMockColumnLenient(String id, String dbName, String name) {
        Column column = mock(Column.class);
        lenient().when(column.getId()).thenReturn(id);
        lenient().when(column.getDBColumnName()).thenReturn(dbName);
        lenient().when(column.getName()).thenReturn(name);
        lenient().when(column.getDescription()).thenReturn(TEST_DESCRIPTION);
        lenient().when(column.getHelpComment()).thenReturn(TEST_HELP);
        lenient().when(column.isMandatory()).thenReturn(true);
        lenient().when(column.getIdentifier()).thenReturn(name);
        return column;
    }

    protected List<Column> createAllAuditColumns() {
        return List.of(
                createMockColumn(CREATED_ID, CREATED_ID, CREATION_DATE_NAME),
                createMockColumn(CREATED_BY_ID, CREATED_BY_ID, CREATED_BY_NAME),
                createMockColumn(UPDATED_ID, UPDATED_ID, UPDATED_NAME),
                createMockColumn(UPDATED_BY_ID, UPDATED_BY_ID, UPDATED_BY_NAME));
    }

    protected List<Column> createAllAuditColumnsLenient() {
        return List.of(
                createMockColumnLenient(CREATED_ID, CREATED_ID, CREATION_DATE_NAME),
                createMockColumnLenient(CREATED_BY_ID, CREATED_BY_ID, CREATED_BY_NAME),
                createMockColumnLenient(UPDATED_ID, UPDATED_ID, UPDATED_NAME),
                createMockColumnLenient(UPDATED_BY_ID, UPDATED_BY_ID, UPDATED_BY_NAME));
    }

    protected void setupBasicMocks(OBContext mockContext, Language mockLanguage, Tab mockTab,
            Table mockTable, KernelUtils mockKernelUtils, List<Column> columns) {
        Entity mockEntity = mock(Entity.class);
        lenient().when(mockTab.getEntity()).thenReturn(mockEntity);
        lenient().when(mockTab.getIdentifier()).thenReturn("MockTabIdentifier");
        lenient().when(mockTab.getId()).thenReturn("MockTabId");
        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TEST_TABLE_NAME);
        when(mockTable.getADColumnList()).thenReturn(columns);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);
    }

    protected void setupBasicMocksLenient(OBContext mockContext, Language mockLanguage, Tab mockTab,
            Table mockTable, KernelUtils mockKernelUtils, List<Column> columns) {
        lenient().when(mockContext.getLanguage()).thenReturn(mockLanguage);
        lenient().when(mockTab.getFilterClause()).thenReturn("");
        lenient().when(mockTab.getDisplayLogic()).thenReturn("");
        lenient().when(mockTab.getTable()).thenReturn(mockTable);
        lenient().when(mockTable.getName()).thenReturn(TEST_TABLE_NAME);
        lenient().when(mockTable.getADColumnList()).thenReturn(columns);
        lenient().when(mockTab.getTabLevel()).thenReturn(0L);
        lenient().when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);
    }

    /**
     * Context holder for test mocks
     */
    protected static class TestContext {
        final OBContext context;
        final Language language;
        final Tab tab;
        final Table table;
        final KernelUtils kernelUtils;

        TestContext(OBContext context, Language language, Tab tab, Table table, KernelUtils kernelUtils) {
            this.context = context;
            this.language = language;
            this.tab = tab;
            this.table = table;
            this.kernelUtils = kernelUtils;
        }
    }
}

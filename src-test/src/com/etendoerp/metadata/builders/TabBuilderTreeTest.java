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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.TableTree;
import org.openbravo.service.datasource.DataSource;

import com.etendoerp.metadata.utils.Constants;

/**
 * Unit tests for the tree metadata emitted by {@link TabBuilder}: tabs with a table tree
 * configured (AD_Tab.AD_Table_Tree_ID) offer tree mode, as in the Classic UI.
 */
@ExtendWith(MockitoExtension.class)
class TabBuilderTreeTest extends TabBuilderTestBase {

    private static final String JSON_EXCEPTION = "JSON exception";
    private static final String HAS_TREE_KEY = "hasTree";
    private static final String TABLE_TREE_ID_KEY = "tableTreeId";
    private static final String TREE_STRUCTURE_KEY = "treeStructure";
    private static final String HQL_WHERE_KEY = "hqlWhereClauseForRootNodes";
    private static final String HAS_TREE_SHOULD_BE_TRUE = "hasTree should be true";
    private static final String TABLE_TREE_ID_SHOULD_BE_SET = "tableTreeId should be set";
    private static final String TABLE_ID_KEY = "tableId";
    private static final String TREE_DATASOURCE_ID_KEY = "treeDatasourceId";
    private static final String TREE_ID = "tree-001";

    /**
     * Creates a mocked {@link TableTree} with the given id and tree structure.
     *
     * @param treeStructure
     *     the tree structure returned by the mock, may be null
     * @return the mocked table tree
     */
    private static TableTree mockTableTree(String treeStructure) {
        TableTree tableTree = mock(TableTree.class);
        when(tableTree.getId()).thenReturn(TREE_ID);
        when(tableTree.getTreeStructure()).thenReturn(treeStructure);
        return tableTree;
    }

    /**
     * Stubs the tree configuration of the tab under test.
     *
     * @param ctx
     *     the test context holding the mocked tab
     * @param tableTree
     *     the table tree configured in the tab
     * @param readOnlyTree
     *     value returned by {@link Tab#isReadOnlyTree()}
     * @param hqlWhere
     *     value returned by {@link Tab#getHQLWhereClauseForRootNodes()}
     */
    private static void stubTreeTab(TestContext ctx, TableTree tableTree, boolean readOnlyTree, String hqlWhere) {
        when(ctx.tab.getTableTree()).thenReturn(tableTree);
        when(ctx.tab.isReadOnlyTree()).thenReturn(readOnlyTree);
        when(ctx.tab.isShowTreeNodeIcons()).thenReturn(!readOnlyTree);
        when(ctx.tab.getHQLWhereClauseForRootNodes()).thenReturn(hqlWhere);
    }

    /**
     * Builds the JSON of a tab configured with the given table tree and asserts the emitted
     * tree datasource id.
     *
     * @param tableTree
     *     the table tree configured in the tab
     * @param expectedDatasourceId
     *     the expected {@code treeDatasourceId}, or null when it must be absent
     * @throws Exception
     *     when mock setup or JSON building fails
     */
    private void assertTreeDatasourceId(TableTree tableTree, String expectedDatasourceId) throws Exception {
        TestContext ctx = setupTestContext();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        lenient().when(ctx.table.getId()).thenReturn("table-003");
        stubTreeTab(ctx, tableTree, false, null);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                assertTrue(result.getBoolean(HAS_TREE_KEY), HAS_TREE_SHOULD_BE_TRUE);
                assertEquals(expectedDatasourceId, result.optString(TREE_DATASOURCE_ID_KEY, null),
                        "treeDatasourceId should match the table tree structure");
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that hasTree and all tree-related properties are included in the JSON
     * when the tab has a full TableTree configuration (ADTree structure).
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONIncludesFullTreePropertiesWhenTableTreeIsConfigured() throws Exception {
        TestContext ctx = setupTestContext();
        TableTree mockTableTree = mockTableTree(Constants.AD_TREE_STRUCTURE);
        String hqlWhere = "it.parent is null";
        String tableId = "table-001";

        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        when(ctx.table.getId()).thenReturn(tableId);
        stubTreeTab(ctx, mockTableTree, false, hqlWhere);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                assertTrue(result.getBoolean(HAS_TREE_KEY), HAS_TREE_SHOULD_BE_TRUE);
                assertEquals(tableId, result.getString(TABLE_ID_KEY), "tableId should be set");
                assertEquals(TREE_ID, result.getString(TABLE_TREE_ID_KEY), TABLE_TREE_ID_SHOULD_BE_SET);
                assertEquals(Constants.AD_TREE_STRUCTURE, result.getString(TREE_STRUCTURE_KEY),
                        "treeStructure should be set");
                assertEquals(Constants.TREE_DATASOURCE, result.getString(TREE_DATASOURCE_ID_KEY),
                        "treeDatasourceId should be the ADTree datasource");
                assertFalse(result.getBoolean("isReadOnlyTree"), "isReadOnlyTree should be false");
                assertTrue(result.getBoolean("showTreeNodeIcons"), "showTreeNodeIcons should be true");
                assertEquals(hqlWhere, result.getString(HQL_WHERE_KEY),
                        "hqlWhereClauseForRootNodes should be set");
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that tree properties are emitted for a tab with a table tree even when the
     * AD_Tab.HasTree flag is not set, matching the Classic UI criterion (OBViewTab.isTree).
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONIncludesTreePropertiesRegardlessOfHasTreeFlag() throws Exception {
        TestContext ctx = setupTestContext();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        lenient().when(ctx.table.getId()).thenReturn("table-004");
        stubTreeTab(ctx, mockTableTree(Constants.AD_TREE_STRUCTURE), true, null);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                assertTrue(result.getBoolean(HAS_TREE_KEY), HAS_TREE_SHOULD_BE_TRUE);
                assertEquals(TREE_ID, result.getString(TABLE_TREE_ID_KEY), TABLE_TREE_ID_SHOULD_BE_SET);
                assertTrue(result.getBoolean("isReadOnlyTree"), "isReadOnlyTree should be true");
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
        verify(ctx.tab, never()).isTreeIncluded();
    }

    /**
     * Tests that tree properties are omitted when the tab has no table tree.
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONOmitsTreePropertiesWhenTableTreeIsNull() throws Exception {
        TestContext ctx = setupTestContext();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        when(ctx.tab.getTableTree()).thenReturn(null);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(),
                TabBuilderTreeTest::assertNoTreeProperties);
    }

    /**
     * Tests that a tab flagged with HasTree = 'Y' but without a table tree does not offer
     * tree mode, as in the Classic UI.
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONOmitsTreePropertiesWhenHasTreeFlagIsSetWithoutTableTree() throws Exception {
        TestContext ctx = setupTestContext();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        lenient().when(ctx.tab.isTreeIncluded()).thenReturn(true);
        when(ctx.tab.getTableTree()).thenReturn(null);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(),
                TabBuilderTreeTest::assertNoTreeProperties);
    }

    /**
     * Asserts that none of the tree-related properties are present in the tab JSON.
     *
     * @param result
     *     the tab JSON built by TabBuilder
     */
    private static void assertNoTreeProperties(JSONObject result) {
        assertFalse(result.has(HAS_TREE_KEY), "hasTree should be absent");
        assertFalse(result.has(TABLE_TREE_ID_KEY), "tableTreeId should be absent");
        assertFalse(result.has(TREE_STRUCTURE_KEY), "treeStructure should be absent");
        assertFalse(result.has(TREE_DATASOURCE_ID_KEY), "treeDatasourceId should be absent");
    }

    /**
     * Tests that treeStructure, treeDatasourceId and hqlWhereClauseForRootNodes are omitted when
     * the table tree has neither structure nor datasource and the HQL clause is blank.
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONHandlesTreeWithNullTreeStructure() throws Exception {
        TestContext ctx = setupTestContext();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        lenient().when(ctx.table.getId()).thenReturn("table-003");
        stubTreeTab(ctx, mockTableTree(null), false, "");

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                assertTrue(result.getBoolean(HAS_TREE_KEY), HAS_TREE_SHOULD_BE_TRUE);
                assertEquals(TREE_ID, result.getString(TABLE_TREE_ID_KEY), TABLE_TREE_ID_SHOULD_BE_SET);
                assertFalse(result.has(TREE_STRUCTURE_KEY), "treeStructure should be absent when null");
                assertFalse(result.has(TREE_DATASOURCE_ID_KEY),
                        "treeDatasourceId should be absent when the tree has no datasource");
                assertFalse(result.has(HQL_WHERE_KEY),
                        "hqlWhereClauseForRootNodes should be absent when blank");
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that a LinkToParent table tree emits the LinkToParent tree datasource.
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONEmitsLinkToParentDatasourceForLinkToParentTree() throws Exception {
        assertTreeDatasourceId(mockTableTree(Constants.LINK_TO_PARENT_STRUCTURE),
                Constants.LINK_TO_PARENT_DATASOURCE);
    }

    /**
     * Tests that a custom table tree emits the datasource configured in the table tree.
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONEmitsConfiguredDatasourceForCustomTree() throws Exception {
        String customDatasourceId = "custom-datasource-001";
        TableTree tableTree = mockTableTree("Custom");
        DataSource datasource = mock(DataSource.class);
        when(datasource.getId()).thenReturn(customDatasourceId);
        when(tableTree.getDatasource()).thenReturn(datasource);

        assertTreeDatasourceId(tableTree, customDatasourceId);
    }

    /**
     * Tests that a custom table tree without a configured datasource omits treeDatasourceId.
     *
     * @throws Exception when mock setup fails
     */
    @Test
    void toJSONOmitsDatasourceForCustomTreeWithoutDatasource() throws Exception {
        assertTreeDatasourceId(mockTableTree("Custom"), null);
    }
}

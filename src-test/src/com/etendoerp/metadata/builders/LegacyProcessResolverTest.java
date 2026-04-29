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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.BooleanDomainType;
import org.openbravo.base.model.domaintype.DateDomainType;
import org.openbravo.base.model.domaintype.StringDomainType;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;

/**
 * Unit tests for {@link LegacyProcessResolver}, focused on the manual-process URL
 * resolution cascade:
 * <ol>
 *   <li>{@code AD_MODEL_OBJECT_MAPPING} (primary canonical source)</li>
 *   <li>{@code AD_Process.Classname} or {@code AD_MODEL_OBJECT.Classname} +
 *       {@link LegacyProcessResolver#javaClassToUrl(String)} (legacy fallback)</li>
 *   <li>{@code null} — the field is skipped and the frontend falls back to
 *       {@code data.json}</li>
 * </ol>
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class LegacyProcessResolverTest {

    private static final String UIPATTERN_MANUAL = "M";
    private static final String UIPATTERN_STANDARD = "S";
    private static final String PROCESS_ID = "468156B616E04DB39CBAD3592316FD50";
    private static final String RESCHEDULE_FQCN = "org.openbravo.erpCommon.ad_process.RescheduleProcess";
    private static final String RESCHEDULE_URL = "/ad_process/RescheduleProcess.html";
    private static final String PROCESS_INVOICE_FQCN = "org.openbravo.advpaymentmngt.ad_actionbutton.ProcessInvoice";
    private static final String PROCESS_INVOICE_DERIVED_URL =
            "/org.openbravo.advpaymentmngt.ad_actionbutton/ProcessInvoice.html";
    private static final String KEY_COLUMN = "AD_Process_Request_ID";
    private static final String FIELD_ID = "57A2B365BDC69F57E040007F010171B4";
    private static final String COMMAND_DEFAULT = "DEFAULT";
    private static final String ACTION_PROCESS = "P";
    private static final String ACTION_REPORT = "R";
    private static final String ACTION_CALLOUT = "C";

    private static final String RESCHEDULE_KEY = "Reschedule";
    private static final String C_INVOICE_ID = "C_Invoice_ID";
    private static final String FIN_FINACC_TRANSACTION_ID = "Fin_Finacc_Transaction_ID";
    private static final String PROCESSED = "Processed";
    private static final String INP_PROCESSED = "inpprocessed";
    private static final String PROCESSED_LOWER = "processed";
    private static final String EM_APRM_PROCESSED = "EM_APRM_Processed";
    private static final String ADDITIONAL_PARAMETERS = "additionalParameters";

    /**
     * Representative case — fieldId 57A2B365BDC69F57E040007F010171B4 (Reschedule Process):
     * AD_Process.Classname is null; classname and URL live in AD_MODEL_OBJECT /
     * AD_MODEL_OBJECT_MAPPING. The resolver must pick the mapping as the canonical URL.
     */
    @Test
    void resolveReturnsMappingUrlWhenProcessHasNoInlineClassname() {
        ModelImplementationMapping mapping = mockMapping(RESCHEDULE_URL, true);
        ModelImplementation impl = mockImpl(ACTION_PROCESS, RESCHEDULE_FQCN, true, List.of(mapping));
        Process process = mockManualProcess(null, List.of(impl));
        Field field = mockFieldWithProcess(process, RESCHEDULE_KEY, KEY_COLUMN);

        Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

        assertTrue(params.isPresent());
        assertEquals(RESCHEDULE_URL, extractUrl(params));
        assertEquals(COMMAND_DEFAULT, extractCommand(params));
        assertEquals(KEY_COLUMN, extractKeyColumn(params));
    }

    /**
     * Fallback — a manual process with no ModelImplementation but with a classname
     * inline in AD_Process. Preserves backward-compat for processes like ProcessInvoice.
     */
    @Test
    void resolveFallsBackToInlineClassnameWhenNoMappingsPresent() {
        Process process = mockManualProcess(PROCESS_INVOICE_FQCN, Collections.emptyList());
        Field field = mockFieldWithProcess(process, "EM_APRM_Processinvoice", C_INVOICE_ID);

        Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

        assertTrue(params.isPresent());
        assertEquals(PROCESS_INVOICE_DERIVED_URL, extractUrl(params));
        assertEquals(COMMAND_DEFAULT, extractCommand(params));
        assertEquals(C_INVOICE_ID, extractKeyColumn(params));
    }

    /**
     * Fallback — AD_Process.Classname is empty but AD_MODEL_OBJECT holds the FQCN.
     * With no mapping row, the resolver must still derive the URL from the classname.
     */
    @Test
    void resolveFallsBackToModelImplementationClassnameWhenNoMappings() {
        ModelImplementation impl = mockImpl(ACTION_PROCESS, PROCESS_INVOICE_FQCN, true, Collections.emptyList());
        Process process = mockManualProcess(null, List.of(impl));
        Field field = mockFieldWithProcess(process, "EM_APRM_Processinvoice", C_INVOICE_ID);

        Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

        assertTrue(params.isPresent());
        assertEquals(PROCESS_INVOICE_DERIVED_URL, extractUrl(params));
    }

    /**
     * Default-flag priority — when multiple implementations exist, the one flagged as
     * {@code isDefault=true} must win over the non-default one.
     */
    @Test
    void resolvePrefersDefaultImplementationOverNonDefault() {
        ModelImplementationMapping nonDefaultMapping = mockMapping("/obsolete/Legacy.html", true);
        ModelImplementation nonDefault =
                mockImpl(ACTION_PROCESS, "org.openbravo.legacy.Legacy", false, List.of(nonDefaultMapping));

        ModelImplementationMapping defaultMapping = mockMapping(RESCHEDULE_URL, true);
        ModelImplementation defaultImpl =
                mockImpl(ACTION_PROCESS, RESCHEDULE_FQCN, true, List.of(defaultMapping));

        // Intentionally place non-default first to verify sorting matters.
        Process process = mockManualProcess(null, List.of(nonDefault, defaultImpl));
        Field field = mockFieldWithProcess(process, RESCHEDULE_KEY, KEY_COLUMN);

        Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

        assertTrue(params.isPresent());
        assertEquals(RESCHEDULE_URL, extractUrl(params));
    }

    /**
     * No implementations with action P or R — the resolver returns empty and logs
     * a warning. A callout-only row (action='C') must not be considered.
     */
    @Test
    void resolveReturnsEmptyWhenNoProcessOrReportImplementation() {
        ModelImplementation calloutOnly =
                mockImpl(ACTION_CALLOUT, "org.openbravo.erpCommon.ad_callouts.SomeCallout", true, Collections.emptyList());
        Process process = mockManualProcess(null, List.of(calloutOnly));
        Field field = mockFieldWithProcess(process, RESCHEDULE_KEY, KEY_COLUMN);

        Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

        assertFalse(params.isPresent());
    }

    /**
     * Report action — action='R' entries are valid and must be resolved the same way
     * as process ('P') entries. Covers the "Display Jasper Report" row in section 2.B.6.
     */
    @Test
    void resolveAcceptsReportActionImplementations() {
        ModelImplementationMapping mapping = mockMapping("/utility/JasperReport.html", true);
        ModelImplementation reportImpl =
                mockImpl(ACTION_REPORT, "org.openbravo.erpCommon.utility.JasperReport", true, List.of(mapping));
        Process process = mockManualProcess(null, List.of(reportImpl));
        Field field = mockFieldWithProcess(process, "Result", KEY_COLUMN);

        Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

        assertTrue(params.isPresent());
        assertEquals("/utility/JasperReport.html", extractUrl(params));
    }

    /**
     * No default mapping flagged — the resolver still returns the first non-blank
     * mapping name so that runtime data inconsistencies don't break the feature.
     */
    @Test
    void resolvePicksFirstValidMappingWhenNoneIsFlaggedAsDefault() {
        ModelImplementationMapping mapping = mockMapping(RESCHEDULE_URL, false);
        ModelImplementation impl = mockImpl(ACTION_PROCESS, RESCHEDULE_FQCN, false, List.of(mapping));
        Process process = mockManualProcess(null, List.of(impl));
        Field field = mockFieldWithProcess(process, RESCHEDULE_KEY, KEY_COLUMN);

        Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

        assertTrue(params.isPresent());
        assertEquals(RESCHEDULE_URL, extractUrl(params));
    }

    /**
     * Non-manual processes (uipattern='S') must continue to use the tab URL via
     * {@link Utility#getTabURL}; the mapping cascade only applies to uipattern='M'.
     */
    @Test
    void resolveUsesTabUrlForStandardProcesses() {
        Process process = mock(Process.class);
        when(process.getUIPattern()).thenReturn(UIPATTERN_STANDARD);
        when(process.getId()).thenReturn(PROCESS_ID);
        Field field = mockFieldWithProcess(process, "DocAction", "C_Order_ID");
        Tab tab = field.getTab();

        try (MockedStatic<Utility> utility = mockStatic(Utility.class)) {
            utility.when(() -> Utility.getTabURL(tab, null, false))
                    .thenReturn("/SalesOrder/Header_Edition.html");

            Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

            assertTrue(params.isPresent());
            assertEquals("/SalesOrder/Header_Edition.html", extractUrl(params));
            assertEquals("BUTTONDocAction" + PROCESS_ID, extractCommand(params));
        }
    }

    // -------------------------------------------------------------------------
    // javaClassToUrl — direct tests of the package-based derivation helper
    // -------------------------------------------------------------------------

    @Test
    void javaClassToUrlConvertsFullyQualifiedNames() {
        assertEquals(PROCESS_INVOICE_DERIVED_URL, LegacyProcessResolver.javaClassToUrl(PROCESS_INVOICE_FQCN));
    }

    @Test
    void javaClassToUrlHandlesClassnameWithoutPackage() {
        assertEquals("/SimpleClass.html", LegacyProcessResolver.javaClassToUrl("SimpleClass"));
    }

    // -------------------------------------------------------------------------
    // toInpKey — must mirror columnNameToInpKey in the client (utils.ts:135)
    // -------------------------------------------------------------------------

    @Test
    void toInpKeyConvertsKnownColumnSamples() {
        assertEquals("inpcOrderId", LegacyProcessResolver.toInpKey("C_Order_ID"));
        assertEquals("inpfinFinaccTransactionId", LegacyProcessResolver.toInpKey(FIN_FINACC_TRANSACTION_ID));
        assertEquals("inpfinPaymentProposalId", LegacyProcessResolver.toInpKey("Fin_Payment_Proposal_ID"));
        assertEquals(INP_PROCESSED, LegacyProcessResolver.toInpKey(PROCESSED));
        assertEquals("inpadClientId", LegacyProcessResolver.toInpKey("AD_Client_ID"));
    }

    // -------------------------------------------------------------------------
    // buildPlaceholder — pure unit tests of the placeholder grammar
    // -------------------------------------------------------------------------

    @Test
    void buildPlaceholderReturnsRecordIdForPrimaryKey() {
        Property pk = mock(Property.class);
        when(pk.isId()).thenReturn(true);
        assertEquals("$record.id", LegacyProcessResolver.buildPlaceholder(pk));
    }

    @Test
    void buildPlaceholderUsesPropertyNameForString() {
        Property prop = mock(Property.class);
        when(prop.isId()).thenReturn(false);
        when(prop.getName()).thenReturn("docstatus");
        when(prop.getDomainType()).thenReturn(new StringDomainType());
        assertEquals("$record.docstatus", LegacyProcessResolver.buildPlaceholder(prop));
    }

    @Test
    void buildPlaceholderUsesPropertyNameForFK() {
        // FKs expose a non-PrimitiveDomainType (TableDir/Search/Table). buildPlaceholder
        // must NOT invoke Property#getPrimitiveType() on them — that throws ClassCast.
        Property prop = mock(Property.class);
        when(prop.isId()).thenReturn(false);
        when(prop.getName()).thenReturn("client");
        when(prop.getDomainType()).thenReturn(null);
        assertEquals("$record.client", LegacyProcessResolver.buildPlaceholder(prop));
    }

    @Test
    void buildPlaceholderAddsYnSuffixForBooleanColumn() {
        Property prop = mock(Property.class);
        when(prop.isId()).thenReturn(false);
        when(prop.getName()).thenReturn(PROCESSED_LOWER);
        when(prop.getDomainType()).thenReturn(new BooleanDomainType());
        assertEquals("$record.processed!yn", LegacyProcessResolver.buildPlaceholder(prop));
    }

    @Test
    void buildPlaceholderAddsDateSuffixForDateColumn() {
        Property prop = mock(Property.class);
        when(prop.isId()).thenReturn(false);
        when(prop.getName()).thenReturn("dateAcct");
        when(prop.getDomainType()).thenReturn(new DateDomainType());
        assertEquals("$record.dateAcct!date", LegacyProcessResolver.buildPlaceholder(prop));
    }

    // -------------------------------------------------------------------------
    // resolveAdditionalParameters — full pipeline through ModelProvider
    // -------------------------------------------------------------------------

    @Test
    void resolveEmitsPropertyNamePlaceholderForEachIncludedColumn() throws Exception {
        Column processed = mockDataColumn(PROCESSED, "20");                  // Yes-No
        Column financialAcct = mockDataColumn("FIN_Financial_Account_ID", "19"); // TableDir (FK)
        Map<String, Property> props = new HashMap<>();
        props.put(PROCESSED, booleanProperty(PROCESSED_LOWER));
        props.put("FIN_Financial_Account_ID", fkProperty("financialAccount"));
        props.put(FIN_FINACC_TRANSACTION_ID, idProperty());

        Field field = mockFieldWithProcessAndColumns(
                mockManualProcess(PROCESS_INVOICE_FQCN, Collections.emptyList()),
                EM_APRM_PROCESSED,
                FIN_FINACC_TRANSACTION_ID,
                List.of(processed, financialAcct));

        try (MockedStatic<ModelProvider> mp = mockModelProvider(props)) {
            Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

            JSONObject additional = extractAdditionalParameters(params);
            assertEquals("$record.processed!yn", additional.get(INP_PROCESSED));
            assertEquals("$record.financialAccount", additional.get("inpfinFinancialAccountId"));
        }
    }

    /**
     * Classic submits every hidden input including Button-typed columns whose value is
     * stored in DB (e.g. {@code EM_Aprm_Processed=P}). The new resolver must mirror that.
     */
    @Test
    void resolveIncludesButtonColumnsInAdditionalParameters() throws Exception {
        Column normal = mockDataColumn(PROCESSED, "20");
        Column button = mockDataColumn(EM_APRM_PROCESSED, "28"); // Button
        Map<String, Property> props = new HashMap<>();
        props.put(PROCESSED, booleanProperty(PROCESSED_LOWER));
        props.put(EM_APRM_PROCESSED, stringProperty("emAprmProcessed"));
        props.put(FIN_FINACC_TRANSACTION_ID, idProperty());

        Field field = mockFieldWithProcessAndColumns(
                mockManualProcess(PROCESS_INVOICE_FQCN, Collections.emptyList()),
                EM_APRM_PROCESSED,
                FIN_FINACC_TRANSACTION_ID,
                List.of(normal, button));

        try (MockedStatic<ModelProvider> mp = mockModelProvider(props)) {
            Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

            JSONObject additional = extractAdditionalParameters(params);
            assertTrue(additional.has(INP_PROCESSED));
            assertTrue(additional.has("inpemAprmProcessed"));
            assertEquals("$record.emAprmProcessed", additional.get("inpemAprmProcessed"));
        }
    }

    @Test
    void resolveExcludesPasswordAndImageColumnsFromAdditionalParameters() throws Exception {
        Column normal = mockDataColumn(PROCESSED, "20");
        Column password = mockDataColumn("Secret", "24");  // Password
        Column image = mockDataColumn("Logo", "32");       // Image
        Map<String, Property> props = new HashMap<>();
        props.put(PROCESSED, booleanProperty(PROCESSED_LOWER));
        // Password/Image columns are filtered before ModelProvider is consulted, so they
        // don't need property entries.
        props.put(FIN_FINACC_TRANSACTION_ID, idProperty());

        Field field = mockFieldWithProcessAndColumns(
                mockManualProcess(PROCESS_INVOICE_FQCN, Collections.emptyList()),
                EM_APRM_PROCESSED,
                FIN_FINACC_TRANSACTION_ID,
                List.of(normal, password, image));

        try (MockedStatic<ModelProvider> mp = mockModelProvider(props)) {
            Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

            JSONObject additional = extractAdditionalParameters(params);
            assertTrue(additional.has(INP_PROCESSED));
            assertFalse(additional.has("inpsecret"));
            assertFalse(additional.has("inplogo"));
        }
    }

    @Test
    void resolveExcludesInactiveColumnsFromAdditionalParameters() throws Exception {
        Column active = mockDataColumn(PROCESSED, "20");
        Column inactive = mockDataColumn("OldFlag", "20");
        when(inactive.isActive()).thenReturn(false);
        Map<String, Property> props = new HashMap<>();
        props.put(PROCESSED, booleanProperty(PROCESSED_LOWER));
        props.put(FIN_FINACC_TRANSACTION_ID, idProperty());

        Field field = mockFieldWithProcessAndColumns(
                mockManualProcess(PROCESS_INVOICE_FQCN, Collections.emptyList()),
                EM_APRM_PROCESSED,
                FIN_FINACC_TRANSACTION_ID,
                List.of(active, inactive));

        try (MockedStatic<ModelProvider> mp = mockModelProvider(props)) {
            Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

            JSONObject additional = extractAdditionalParameters(params);
            assertTrue(additional.has(INP_PROCESSED));
            assertFalse(additional.has("inpoldflag"));
        }
    }

    @Test
    void resolveSkipsColumnsWhoseEntityHasNoProperty() throws Exception {
        Column known = mockDataColumn(PROCESSED, "20");
        Column orphan = mockDataColumn("OrphanColumn", "10");
        Map<String, Property> props = new HashMap<>();
        props.put(PROCESSED, booleanProperty(PROCESSED_LOWER));
        // OrphanColumn deliberately absent — getPropertyByColumnName returns null.
        props.put(FIN_FINACC_TRANSACTION_ID, idProperty());

        Field field = mockFieldWithProcessAndColumns(
                mockManualProcess(PROCESS_INVOICE_FQCN, Collections.emptyList()),
                EM_APRM_PROCESSED,
                FIN_FINACC_TRANSACTION_ID,
                List.of(known, orphan));

        try (MockedStatic<ModelProvider> mp = mockModelProvider(props)) {
            Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

            JSONObject additional = extractAdditionalParameters(params);
            assertTrue(additional.has(INP_PROCESSED));
            assertFalse(additional.has("inporphancolumn"));
        }
    }

    @Test
    void resolveEmitsRecordIdPlaceholderForPrimaryKey() throws Exception {
        Column pk = mockDataColumn(FIN_FINACC_TRANSACTION_ID, "13"); // ID reference
        when(pk.isKeyColumn()).thenReturn(true);
        Map<String, Property> props = new HashMap<>();
        props.put(FIN_FINACC_TRANSACTION_ID, idProperty());

        Field field = mockFieldWithProcessAndColumns(
                mockManualProcess(PROCESS_INVOICE_FQCN, Collections.emptyList()),
                EM_APRM_PROCESSED,
                FIN_FINACC_TRANSACTION_ID,
                List.of(pk));

        try (MockedStatic<ModelProvider> mp = mockModelProvider(props)) {
            Optional<LegacyProcessParams> params = LegacyProcessResolver.resolve(field);

            JSONObject additional = extractAdditionalParameters(params);
            assertEquals("$record.id", additional.get("inpfinFinaccTransactionId"));
        }
    }

    // -------------------------------------------------------------------------
    // LegacyProcessParams.toJson — additionalParameters key omitted when empty
    // -------------------------------------------------------------------------

    @Test
    void toJsonOmitsAdditionalParametersWhenEmpty() throws Exception {
        LegacyProcessParams params =
                new LegacyProcessParams(RESCHEDULE_URL, COMMAND_DEFAULT, KEY_COLUMN, KEY_COLUMN);
        JSONObject json = params.toJson();
        assertFalse(json.has(ADDITIONAL_PARAMETERS));
    }

    @Test
    void toJsonIncludesAdditionalParametersWhenPopulated() throws Exception {
        LegacyProcessParams params = new LegacyProcessParams(
                RESCHEDULE_URL, COMMAND_DEFAULT, KEY_COLUMN, KEY_COLUMN,
                java.util.Map.of(INP_PROCESSED, "$record.Processed"));
        JSONObject json = params.toJson();
        assertTrue(json.has(ADDITIONAL_PARAMETERS));
        assertEquals("$record.Processed",
                json.getJSONObject(ADDITIONAL_PARAMETERS).get(INP_PROCESSED));
    }

    // -------------------------------------------------------------------------
    // Test fixture helpers — keep individual tests compact and readable
    // -------------------------------------------------------------------------

    private Process mockManualProcess(String inlineClassname, List<ModelImplementation> impls) {
        Process process = mock(Process.class);
        when(process.getUIPattern()).thenReturn(UIPATTERN_MANUAL);
        when(process.getJavaClassName()).thenReturn(inlineClassname);
        when(process.getADModelImplementationList()).thenReturn(impls);
        when(process.getId()).thenReturn(PROCESS_ID);
        return process;
    }

    private ModelImplementation mockImpl(String action, String classname, boolean isDefault,
                                         List<ModelImplementationMapping> mappings) {
        ModelImplementation impl = mock(ModelImplementation.class);
        when(impl.getAction()).thenReturn(action);
        when(impl.getJavaClassName()).thenReturn(classname);
        when(impl.isDefault()).thenReturn(isDefault);
        when(impl.getADModelImplementationMappingList()).thenReturn(mappings);
        return impl;
    }

    private ModelImplementationMapping mockMapping(String name, boolean isDefault) {
        ModelImplementationMapping mapping = mock(ModelImplementationMapping.class);
        when(mapping.getMappingName()).thenReturn(name);
        when(mapping.isDefault()).thenReturn(isDefault);
        return mapping;
    }

    private Field mockFieldWithProcess(Process process, String columnName, String keyColumnName) {
        Field field = mock(Field.class);
        Column column = mock(Column.class);
        Tab tab = mock(Tab.class);
        Table table = mock(Table.class);
        Column keyColumn = mock(Column.class);

        when(field.getId()).thenReturn(FIELD_ID);
        when(field.getColumn()).thenReturn(column);
        when(field.getTab()).thenReturn(tab);

        when(column.getDBColumnName()).thenReturn(columnName);
        when(column.getProcess()).thenReturn(process);

        when(tab.getTable()).thenReturn(table);
        when(table.getADColumnList()).thenReturn(List.of(keyColumn));

        when(keyColumn.isKeyColumn()).thenReturn(true);
        when(keyColumn.getDBColumnName()).thenReturn(keyColumnName);

        return field;
    }

    /**
     * Variant of {@link #mockFieldWithProcess} where the tab's table exposes a custom
     * column list. The key column is appended automatically so {@code resolveKeyColumnName}
     * still finds it.
     */
    private Field mockFieldWithProcessAndColumns(Process process, String columnName,
                                                 String keyColumnName, List<Column> dataColumns) {
        Field field = mock(Field.class);
        Column buttonColumn = mock(Column.class);
        Tab tab = mock(Tab.class);
        Table table = mock(Table.class);
        Column keyColumn = mock(Column.class);

        when(field.getId()).thenReturn(FIELD_ID);
        when(field.getColumn()).thenReturn(buttonColumn);
        when(field.getTab()).thenReturn(tab);

        when(buttonColumn.getDBColumnName()).thenReturn(columnName);
        when(buttonColumn.getProcess()).thenReturn(process);

        List<Column> allColumns = new ArrayList<>(dataColumns);
        allColumns.add(keyColumn);
        when(tab.getTable()).thenReturn(table);
        when(table.getADColumnList()).thenReturn(allColumns);
        when(table.getDBTableName()).thenReturn("FIN_Finacc_Transaction");

        when(keyColumn.isKeyColumn()).thenReturn(true);
        when(keyColumn.getDBColumnName()).thenReturn(keyColumnName);
        // Key column is excluded from additional parameters because isActive() defaults to
        // false under LENIENT mocking, which keeps existing fixtures intact.

        return field;
    }

    private Column mockDataColumn(String dbColumnName, String referenceId) {
        Column col = mock(Column.class);
        when(col.getDBColumnName()).thenReturn(dbColumnName);
        when(col.isActive()).thenReturn(true);
        when(col.isKeyColumn()).thenReturn(false);
        if (referenceId != null) {
            Reference ref = mock(Reference.class);
            when(ref.getId()).thenReturn(referenceId);
            when(col.getReference()).thenReturn(ref);
        } else {
            when(col.getReference()).thenReturn(null);
        }
        return col;
    }

    /**
     * Stubs {@link ModelProvider#getInstance()} so that {@code getEntityByTableName(any)}
     * returns an Entity whose {@code getPropertyByColumnName(name)} answers from the given
     * map. Columns absent from the map produce {@code null}, mirroring the orphan-column
     * scenario.
     */
    private MockedStatic<ModelProvider> mockModelProvider(Map<String, Property> propertiesByColumn) {
        MockedStatic<ModelProvider> mocked = mockStatic(ModelProvider.class);
        ModelProvider provider = mock(ModelProvider.class);
        Entity entity = mock(Entity.class);
        when(entity.getName()).thenReturn("FIN_FinaccTransaction");
        when(entity.getPropertyByColumnName(anyString()))
                .thenAnswer(invocation -> propertiesByColumn.get(invocation.<String>getArgument(0)));
        when(provider.getEntityByTableName(anyString())).thenReturn(entity);
        mocked.when(ModelProvider::getInstance).thenReturn(provider);
        return mocked;
    }

    private Property idProperty() {
        Property p = mock(Property.class);
        when(p.isId()).thenReturn(true);
        return p;
    }

    private Property booleanProperty(String name) {
        Property p = mock(Property.class);
        when(p.isId()).thenReturn(false);
        when(p.getName()).thenReturn(name);
        when(p.getDomainType()).thenReturn(new BooleanDomainType());
        return p;
    }

    private Property stringProperty(String name) {
        Property p = mock(Property.class);
        when(p.isId()).thenReturn(false);
        when(p.getName()).thenReturn(name);
        when(p.getDomainType()).thenReturn(new StringDomainType());
        return p;
    }

    private Property fkProperty(String name) {
        Property p = mock(Property.class);
        when(p.isId()).thenReturn(false);
        when(p.getName()).thenReturn(name);
        // FK domain types (TableDir/Search/Table) are NOT PrimitiveDomainType — use null
        // here so the resolver follows the non-primitive path without throwing on cast.
        when(p.getDomainType()).thenReturn(null);
        return p;
    }

    private JSONObject extractAdditionalParameters(Optional<LegacyProcessParams> params) {
        try {
            JSONObject json = params.orElseThrow().toJson();
            return json.getJSONObject(ADDITIONAL_PARAMETERS);
        } catch (Exception e) {
            throw new AssertionError("Could not extract additionalParameters from params", e);
        }
    }

    private String extractUrl(Optional<LegacyProcessParams> params) {
        try {
            return (String) params.orElseThrow()
                    .toJson()
                    .get("url");
        } catch (Exception e) {
            throw new AssertionError("Could not extract url from params", e);
        }
    }

    private String extractCommand(Optional<LegacyProcessParams> params) {
        try {
            return (String) params.orElseThrow()
                    .toJson()
                    .get("command");
        } catch (Exception e) {
            throw new AssertionError("Could not extract command from params", e);
        }
    }

    private String extractKeyColumn(Optional<LegacyProcessParams> params) {
        try {
            return (String) params.orElseThrow()
                    .toJson()
                    .get("keyColumnName");
        } catch (Exception e) {
            throw new AssertionError("Could not extract keyColumnName from params", e);
        }
    }
}

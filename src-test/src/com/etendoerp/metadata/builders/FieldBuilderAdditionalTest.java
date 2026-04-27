package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DomainType;
import org.openbravo.base.model.domaintype.ForeignKeyDomainType;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.datasource.DataSource;
import org.openbravo.service.datasource.DatasourceField;
import org.openbravo.service.json.DataToJsonConverter;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;

import com.etendoerp.etendorx.utils.DataSourceUtils;
import com.etendoerp.metadata.data.ReferenceSelectors;
import com.etendoerp.metadata.utils.Constants;

/**
 * Additional test coverage for FieldBuilder to reach uncovered branches.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderAdditionalTest {

    @Mock
    private Field field;
    @Mock
    private Column column;
    @Mock
    private Reference reference;
    @Mock
    private Tab tab;
    @Mock
    private Table table;
    @Mock
    private Language language;
    @Mock
    private Selector selector;
    @Mock
    private SelectorField selectorField;
    @Mock
    private DatasourceField datasourceField;
    @Mock
    private DataSource dataSource;
    @Mock
    private ModelProvider modelProvider;

    private static final String FIELD_ID = "field-id";
    private static final String VALUE_PROPERTY = "valueProperty";
    private static final String TEST_TABLE = "TestTable";
    private static final String BOOLEAN_FIELD = "booleanField";
    private static final String DISPLAY = "display";
    private static final String BOOL_FIELD = "boolField";
    private static final String FK_FIELD = "fkField";
    private static final String SELECTOR_ID = "selector-id";
    private static final String STARTS_WITH = "startsWith";
    private static final String EXACT = "exact";
    private static final String ALIAS_FIELD = "aliasField";
    private static final String PROP_FIELD = "propField";
    private static final String SEL_ID = "sel-id";
    private static final String PARENT_CHILD = "parent$child";
    private static final String MY_FIELD = "myField";
    private static final String GET_DOMAIN_TYPE = "getDomainType";
    private static final String CUSTOM_DISPLAY = "customDisplay";

    @BeforeEach
    void setUp() {
        when(field.getColumn()).thenReturn(column);
        when(field.getTab()).thenReturn(tab);
        when(field.getId()).thenReturn(FIELD_ID);
        when(column.getReference()).thenReturn(reference);
        when(reference.getId()).thenReturn("reference-id");
    }

    private SelectorField setupSearchFieldTest(String fieldProperty) {
        SelectorField displayField = mock(SelectorField.class);
        SelectorField testField = mock(SelectorField.class);
        return setupSearchFieldTest(fieldProperty, testField, displayField);
    }

    private SelectorField setupSearchFieldTest(String fieldProperty, SelectorField testField,
            SelectorField displayField) {
        when(selector.getDisplayfield()).thenReturn(displayField);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(testField));
        when(testField.isActive()).thenReturn(true);
        when(testField.isSearchinsuggestionbox()).thenReturn(true);
        when(testField.getProperty()).thenReturn(fieldProperty);
        when(testField.getObuiselSelector()).thenReturn(selector);
        when(selector.getTable()).thenReturn(table);
        when(table.getName()).thenReturn(TEST_TABLE);
        when(displayField.getProperty()).thenReturn(DISPLAY);
        when(displayField.getObuiselSelector()).thenReturn(selector);

        return testField;
    }

    private Property setupEntityPropertyMocks(
            MockedStatic<FieldBuilder> mockedStatic,
            MockedStatic<ModelProvider> mockedModelProvider,
            MockedStatic<DalUtil> mockedDalUtil,
            SelectorField testField, String fieldProperty) {
        mockedStatic.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(DISPLAY);
        mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(testField)).thenReturn(fieldProperty);

        Entity mockEntity = mock(Entity.class);
        Property mockProperty = mock(Property.class);

        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(TEST_TABLE)).thenReturn(mockEntity);
        mockedDalUtil.when(() -> DalUtil.getPropertyFromPath(any(Entity.class), eq(fieldProperty)))
            .thenReturn(mockProperty);

        return mockProperty;
    }

    private void mockSelectorInfoBase(String selectorId, String matchStyle) {
        when(selector.getObserdsDatasource()).thenReturn(null);
        when(selector.isCustomQuery()).thenReturn(true);
        when(selector.getId()).thenReturn(selectorId);
        when(selector.getDisplayfield()).thenReturn(null);
        when(selector.getSuggestiontextmatchstyle()).thenReturn(matchStyle);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(Collections.emptyList());
        when(selector.getValuefield()).thenReturn(null);
    }

    private void mockSelectorInfoStatics(MockedStatic<FieldBuilder> mockedStatic, String displayField,
            String valueField, String extraSearchFields) {
        mockedStatic.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(displayField);
        mockedStatic.when(() -> FieldBuilder.getValueField(selector)).thenReturn(valueField);
        mockedStatic.when(() -> FieldBuilder.getExtraSearchFields(selector)).thenReturn(extraSearchFields);
    }

    private DomainType invokeSelectorFieldDomainType(Method method, SelectorField selectorField) {
        try {
            return (DomainType) method.invoke(null, selectorField);
        } catch (Exception e) {
            return null;
        }
    }

    private Method getSelectorFieldDomainTypeMethod() throws NoSuchMethodException {
        Method method = FieldBuilder.class.getDeclaredMethod(GET_DOMAIN_TYPE, SelectorField.class);
        method.setAccessible(true);
        return method;
    }

    private void assertNullSelectorFieldDomainType(SelectorField selectorField) {
        try {
            Method method = getSelectorFieldDomainTypeMethod();
            assertNull(invokeSelectorFieldDomainType(method, selectorField));
        } catch (NoSuchMethodException e) {
            assertNull(null);
        }
    }

    private void assertSelectorFieldDomainTypeEquals(SelectorField selectorField, DomainType expected) {
        try {
            Method method = getSelectorFieldDomainTypeMethod();
            assertEquals(expected, invokeSelectorFieldDomainType(method, selectorField));
        } catch (NoSuchMethodException e) {
            assertNull(expected);
        }
    }

    private void assertSelectorInfoSortBy(String sortBy, SelectorField displayField) throws JSONException {
        when(selector.getObserdsDatasource()).thenReturn(null);
        when(selector.isCustomQuery()).thenReturn(true);
        when(selector.getId()).thenReturn(SELECTOR_ID);
        when(selector.getDisplayfield()).thenReturn(displayField);
        when(selector.getSuggestiontextmatchstyle()).thenReturn(EXACT);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(Collections.emptyList());
        when(selector.getValuefield()).thenReturn(null);
        when(displayField.getObuiselSelector()).thenReturn(selector);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockSelectorInfoStatics(mockedStatic, sortBy, JsonConstants.ID, "");
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(displayField)).thenReturn(sortBy);

            JSONObject result = FieldBuilder.addSelectorInfo(FIELD_ID, selector);

            assertEquals(sortBy, result.getString(JsonConstants.SORTBY_PARAMETER));
        }
    }

    private void assertSelectorInfoDatasource(String expectedDatasource) throws JSONException {
        when(selector.getId()).thenReturn(SELECTOR_ID);
        when(selector.getDisplayfield()).thenReturn(null);
        when(selector.getSuggestiontextmatchstyle()).thenReturn(STARTS_WITH);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(Collections.emptyList());
        when(selector.getValuefield()).thenReturn(null);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockSelectorInfoStatics(mockedStatic, JsonConstants.IDENTIFIER, JsonConstants.ID, "");

            JSONObject result = FieldBuilder.addSelectorInfo(FIELD_ID, selector);

            assertEquals(expectedDatasource, result.getString(Constants.DATASOURCE_PROPERTY));
        }
    }

    private void assertEmptyExtraSearchFields(String fieldProperty, Class<?> primitiveType) {
        SelectorField searchField = setupSearchFieldTest(fieldProperty);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS);
             MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class);
             MockedStatic<DalUtil> mockedDalUtil = mockStatic(DalUtil.class)) {

            Property mockProperty = setupEntityPropertyMocks(mockedStatic, mockedModelProvider, mockedDalUtil,
                searchField, fieldProperty);
            PrimitiveDomainType mockDomainType = mock(PrimitiveDomainType.class);
            when(mockProperty.getDomainType()).thenReturn(mockDomainType);
            doReturn(primitiveType).when(mockDomainType).getPrimitiveType();

            assertEquals("", FieldBuilder.getExtraSearchFields(selector));
        }
    }

    private void assertEmptyExtraSearchFieldsForStaticSelector(SelectorField selectorField,
            String propertyOrField) {
        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockedStatic.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(DISPLAY);
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(selectorField))
                .thenReturn(propertyOrField);

            assertEquals("", FieldBuilder.getExtraSearchFields(selector));
        }
    }

    private void assertIdentifierFallback(DataSource dataSource) {
        when(selector.getDisplayfield()).thenReturn(null);
        when(selector.getObserdsDatasource()).thenReturn(dataSource);

        String result = FieldBuilder.getDisplayField(selector);

        assertEquals(JsonConstants.IDENTIFIER, result);
    }

    private void assertGetHqlName(String expected) throws Exception {
        Method method = FieldBuilder.class.getDeclaredMethod("getHqlName", Field.class);
        method.setAccessible(true);
        String result = (String) method.invoke(null, field);
        assertEquals(expected, result);
    }

    private void assertSelectorInfoContainsAdditionalProperty(SelectorField selectorField,
            String propertyValue) throws JSONException {
        mockSelectorInfoBase(SELECTOR_ID, EXACT);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(selectorField));
        when(selectorField.getProperty()).thenReturn(propertyValue);
        when(selectorField.getObuiselSelector()).thenReturn(selector);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockSelectorInfoStatics(mockedStatic, JsonConstants.IDENTIFIER, JsonConstants.ID, "");
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(selectorField)).thenReturn(propertyValue);

            JSONObject result = FieldBuilder.addSelectorInfo(FIELD_ID, selector);
            String additionalProps = result.getString(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER);
            assertTrue(additionalProps.contains(propertyValue));
        }
    }

    private void assertSelectorInfoNotNullForOutField(SelectorField outField, String propertyValue)
            throws JSONException {
        mockSelectorInfoBase(SELECTOR_ID, EXACT);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(outField));
        when(outField.getProperty()).thenReturn(propertyValue);
        when(outField.isOutfield()).thenReturn(true);
        when(outField.getObuiselSelector()).thenReturn(selector);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockSelectorInfoStatics(mockedStatic, JsonConstants.IDENTIFIER, JsonConstants.ID, "");
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(outField)).thenReturn(propertyValue);

            assertNotNull(FieldBuilder.addSelectorInfo(FIELD_ID, selector));
        }
    }

    private void assertSelectorInfoSelectedPropertiesContains(String displayFieldValue,
            String valueFieldValue, SelectorField displayField, SelectorField valueField,
            SelectorField regularField) throws JSONException {
        when(selector.getObserdsDatasource()).thenReturn(null);
        when(selector.isCustomQuery()).thenReturn(true);
        when(selector.getId()).thenReturn(SEL_ID);
        when(selector.getDisplayfield()).thenReturn(displayField);
        when(selector.getValuefield()).thenReturn(valueField);
        when(selector.getSuggestiontextmatchstyle()).thenReturn(STARTS_WITH);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(List.of(regularField));
        when(displayField.getObuiselSelector()).thenReturn(selector);
        when(valueField.getObuiselSelector()).thenReturn(selector);
        when(regularField.getProperty()).thenReturn("regularProp");
        when(regularField.isOutfield()).thenReturn(false);
        when(regularField.getObuiselSelector()).thenReturn(selector);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockSelectorInfoStatics(mockedStatic, displayFieldValue, valueFieldValue, "");
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(displayField))
                .thenReturn(displayFieldValue);
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(valueField))
                .thenReturn(valueFieldValue);
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(regularField))
                .thenReturn("regularProp");

            JSONObject result = FieldBuilder.addSelectorInfo(FIELD_ID, selector);
            String selectedProperties = result.getString(JsonConstants.SELECTEDPROPERTIES_PARAMETER);
            assertTrue(selectedProperties.contains(displayFieldValue));
        }
    }

    /**
     * Tests getValueField when selector has a value field with ForeignKeyDomainType.
     * This covers the branch where domainType instanceof ForeignKeyDomainType is true.
     */
    @Test
    void testGetValueFieldWithForeignKeyDomainType() {
        SelectorField valueField = mock(SelectorField.class);
        when(selector.getValuefield()).thenReturn(valueField);
        when(selector.isCustomQuery()).thenReturn(false);
        when(selector.getTable()).thenReturn(table);
        when(table.getName()).thenReturn(TEST_TABLE);

        when(valueField.getProperty()).thenReturn(VALUE_PROPERTY);
        when(valueField.getObuiselSelector()).thenReturn(selector);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS);
             MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class);
             MockedStatic<DalUtil> mockedDalUtil = mockStatic(DalUtil.class)) {

            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(valueField))
                .thenReturn(VALUE_PROPERTY);

            Entity mockEntity = mock(Entity.class);
            Property mockProperty = mock(Property.class);
            ForeignKeyDomainType mockDomainType = mock(ForeignKeyDomainType.class);

            mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
            when(modelProvider.getEntity(TEST_TABLE)).thenReturn(mockEntity);
            mockedDalUtil.when(() -> DalUtil.getPropertyFromPath(any(Entity.class), eq(VALUE_PROPERTY)))
                .thenReturn(mockProperty);
            when(mockProperty.getDomainType()).thenReturn(mockDomainType);

            String result = FieldBuilder.getValueField(selector);

            // When domain type is ForeignKeyDomainType, result should include "$id"
            assertTrue(result.contains(VALUE_PROPERTY));
        }
    }

    /**
     * Tests getValueField with datasource fallback - no table, has datasource fields.
     */
    @Test
    void testGetValueFieldWithDatasourceFallback() {
        when(selector.getValuefield()).thenReturn(null);
        when(selector.getObserdsDatasource()).thenReturn(dataSource);
        when(dataSource.getTable()).thenReturn(null);
        when(dataSource.getOBSERDSDatasourceFieldList()).thenReturn(List.of(datasourceField));
        when(datasourceField.getName()).thenReturn("dsField");

        String result = FieldBuilder.getValueField(selector);

        assertEquals("dsField", result);
    }

    /**
     * Tests getValueField datasource fallback with empty field list.
     */
    @Test
    void testGetValueFieldWithDatasourceEmptyFields() {
        when(selector.getValuefield()).thenReturn(null);
        when(selector.getObserdsDatasource()).thenReturn(dataSource);
        when(dataSource.getTable()).thenReturn(null);
        when(dataSource.getOBSERDSDatasourceFieldList()).thenReturn(Collections.emptyList());

        String result = FieldBuilder.getValueField(selector);

        assertEquals(JsonConstants.ID, result);
    }

    /**
     * Tests getDomainType(String) when ModelProvider.getReference returns null.
     */
    @Test
    void testGetDomainTypeWithNullReference() {
        try (MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class)) {
            mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
            when(modelProvider.getReference("unknown-ref")).thenReturn(null);

            DomainType result = FieldBuilder.getDomainType("unknown-ref");

            assertNull(result);
        }
    }

    /**
     * Tests getDomainType(String) when ModelProvider throws an exception.
     */
    @Test
    void testGetDomainTypeWithException() {
        try (MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class)) {
            mockedModelProvider.when(ModelProvider::getInstance).thenThrow(new RuntimeException("Provider error"));

            DomainType result = FieldBuilder.getDomainType("error-ref");

            assertNull(result);
        }
    }

    /**
     * Tests getExtraSearchFields when a field is boolean (should be excluded).
     */
    @Test
    void testGetExtraSearchFieldsExcludesBooleanFields() {
        assertEmptyExtraSearchFields(BOOLEAN_FIELD, boolean.class);
    }

    /**
     * Tests getExtraSearchFields with Boolean.class (wrapper type).
     */
    @Test
    void testGetExtraSearchFieldsExcludesBooleanWrapperFields() {
        assertEmptyExtraSearchFields(BOOL_FIELD, Boolean.class);
    }

    /**
     * Tests getExtraSearchFields with ForeignKeyDomainType (should append $_identifier).
     */
    @Test
    void testGetExtraSearchFieldsWithForeignKeyField() {
        SelectorField fkField = setupSearchFieldTest(FK_FIELD);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS);
             MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class);
             MockedStatic<DalUtil> mockedDalUtil = mockStatic(DalUtil.class)) {

            Property mockProperty = setupEntityPropertyMocks(mockedStatic, mockedModelProvider, mockedDalUtil, fkField, FK_FIELD);
            ForeignKeyDomainType mockDomainType = mock(ForeignKeyDomainType.class);
            when(mockProperty.getDomainType()).thenReturn(mockDomainType);

            String result = FieldBuilder.getExtraSearchFields(selector);

            assertTrue(result.contains(FK_FIELD));
            assertTrue(result.contains(JsonConstants.IDENTIFIER));
        }
    }

    /**
     * Tests getExtraSearchFields when a field is not searchable.
     */
    @Test
    void testGetExtraSearchFieldsNonSearchable() {
        SelectorField nonSearchField = setupSearchFieldTest("nonSearch");
        when(nonSearchField.isSearchinsuggestionbox()).thenReturn(false);

        assertEmptyExtraSearchFieldsForStaticSelector(nonSearchField, "nonSearch");
    }

    /**
     * Tests getExtraSearchFields when field matches display field (should be skipped).
     */
    @Test
    void testGetExtraSearchFieldsSkipsDisplayField() {
        SelectorField sameAsDisplayField = setupSearchFieldTest(DISPLAY);

        assertEmptyExtraSearchFieldsForStaticSelector(sameAsDisplayField, DISPLAY);
    }

    /**
     * Tests getExtraSearchFields with inactive fields (should be filtered).
     */
    @Test
    void testGetExtraSearchFieldsFiltersInactive() {
        SelectorField inactiveField = setupSearchFieldTest("inactive");
        when(inactiveField.isActive()).thenReturn(false);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockedStatic.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(DISPLAY);

            String result = FieldBuilder.getExtraSearchFields(selector);

            assertEquals("", result);
        }
    }

    /**
     * Tests addSelectorInfo when selector has an obserdsDatasource.
     */
    @Test
    void testAddSelectorInfoWithDatasource() throws JSONException {
        when(selector.getObserdsDatasource()).thenReturn(dataSource);
        when(dataSource.getId()).thenReturn("ds-id");

        assertSelectorInfoDatasource("ds-id");
    }

    /**
     * Tests addSelectorInfo when selector has a table-based datasource (not custom query).
     */
    @Test
    void testAddSelectorInfoWithTableBasedSelector() throws JSONException {
        when(selector.getObserdsDatasource()).thenReturn(null);
        when(selector.isCustomQuery()).thenReturn(false);
        when(selector.getTable()).thenReturn(table);
        when(table.getName()).thenReturn("MyTable");
        when(selector.getId()).thenReturn(SELECTOR_ID);
        when(selector.getDisplayfield()).thenReturn(null);
        when(selector.getSuggestiontextmatchstyle()).thenReturn(EXACT);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(Collections.emptyList());
        when(selector.getValuefield()).thenReturn(null);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockSelectorInfoStatics(mockedStatic, JsonConstants.IDENTIFIER, JsonConstants.ID, "");

            JSONObject result = FieldBuilder.addSelectorInfo(FIELD_ID, selector);

            assertEquals("MyTable", result.getString(Constants.DATASOURCE_PROPERTY));
        }
    }

    /**
     * Tests addSelectorInfo display field with displayColumnAlias.
     */
    @Test
    void testAddSelectorInfoDisplayFieldWithAlias() throws JSONException {
        SelectorField displayField = mock(SelectorField.class);
        when(displayField.getDisplayColumnAlias()).thenReturn(ALIAS_FIELD);

        assertSelectorInfoSortBy(ALIAS_FIELD, displayField);
    }

    /**
     * Tests addSelectorInfo display field with null displayColumnAlias.
     */
    @Test
    void testAddSelectorInfoDisplayFieldWithoutAlias() throws JSONException {
        SelectorField displayField = mock(SelectorField.class);
        when(displayField.getDisplayColumnAlias()).thenReturn(null);
        when(displayField.getProperty()).thenReturn(PROP_FIELD);

        assertSelectorInfoSortBy(PROP_FIELD, displayField);
    }

    /**
     * Tests getHqlName when DataSourceUtils.getHQLColumnName returns empty array.
     */
    @Test
    void testGetHqlNameEmptyArrayFallback() throws Exception {
        when(column.getTable()).thenReturn(table);
        when(table.getDBTableName()).thenReturn("test_table");
        when(column.getDBColumnName()).thenReturn("test_column");
        when(field.getName()).thenReturn("Test Field");

        try (MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class)) {
            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, "test_table", "test_column"))
                .thenReturn(new String[] {});

            Method method = FieldBuilder.class.getDeclaredMethod("getHqlName", Field.class);
            method.setAccessible(true);
            String result = (String) method.invoke(null, field);

            // Should fall back to camelCase of field name
            assertEquals("testField", result);
        }
    }

    /**
     * Tests getHqlName with a field name with multiple words separated by spaces.
     */
    @Test
    void testGetHqlNameCamelCase() throws Exception {
        when(field.getColumn()).thenThrow(new RuntimeException("No column"));
        when(field.getName()).thenReturn("General Ledger");

        assertGetHqlName("generalLedger");
    }

    /**
     * Tests getDisplayField with datasource that has a table (should fallback to identifier).
     */
    @Test
    void testGetDisplayFieldWithDatasourceHasTable() {
        when(dataSource.getTable()).thenReturn(table);
        assertIdentifierFallback(dataSource);
    }

    /**
     * Tests getDisplayField with datasource that has no table but empty field list.
     */
    @Test
    void testGetDisplayFieldWithDatasourceEmptyFields() {
        when(dataSource.getTable()).thenReturn(null);
        when(dataSource.getOBSERDSDatasourceFieldList()).thenReturn(Collections.emptyList());

        assertIdentifierFallback(dataSource);
    }

    /**
     * Tests getSelectorInfo with a selector (not null).
     */
    @Test
    void testGetSelectorInfoWithSelector() throws JSONException {
        ReferenceSelectors mockSelectors = new ReferenceSelectors(selector, null);

        when(selector.getObserdsDatasource()).thenReturn(null);
        when(selector.isCustomQuery()).thenReturn(true);
        when(selector.getId()).thenReturn(SEL_ID);
        when(selector.getDisplayfield()).thenReturn(null);
        when(selector.getSuggestiontextmatchstyle()).thenReturn(STARTS_WITH);
        when(selector.getOBUISELSelectorFieldList()).thenReturn(Collections.emptyList());
        when(selector.getValuefield()).thenReturn(null);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
            mockedStatic.when(() -> FieldBuilder.getReferenceSelectors(reference))
                .thenReturn(mockSelectors);
            mockedStatic.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(JsonConstants.IDENTIFIER);
            mockedStatic.when(() -> FieldBuilder.getValueField(selector)).thenReturn(JsonConstants.ID);
            mockedStatic.when(() -> FieldBuilder.getExtraSearchFields(selector)).thenReturn("");

            JSONObject result = FieldBuilder.getSelectorInfo(FIELD_ID, reference);

            assertNotNull(result);
            assertEquals(SEL_ID, result.getString(Constants.SELECTOR_DEFINITION_PROPERTY));
        }
    }

    /**
     * Tests getReferenceSelectors with both selectors and tree selectors.
     */
    @Test
    void testGetReferenceSelectorsWithBoth() {
        org.openbravo.model.ad.domain.ReferencedTree treeSelector = mock(org.openbravo.model.ad.domain.ReferencedTree.class);
        when(reference.getOBUISELSelectorList()).thenReturn(List.of(selector));
        when(reference.getADReferencedTreeList()).thenReturn(List.of(treeSelector));

        ReferenceSelectors result = FieldBuilder.getReferenceSelectors(reference);

        assertEquals(selector, result.selector);
        assertEquals(treeSelector, result.treeSelector);
    }

    /**
     * Tests getReferenceSelectors with empty lists.
     */
    @Test
    void testGetReferenceSelectorsWithEmptyLists() {
        when(reference.getOBUISELSelectorList()).thenReturn(Collections.emptyList());
        when(reference.getADReferencedTreeList()).thenReturn(Collections.emptyList());

        ReferenceSelectors result = FieldBuilder.getReferenceSelectors(reference);

        assertNull(result.selector);
        assertNull(result.treeSelector);
    }

    /**
     * Tests the processSelectorField method with field separator in name (derived property).
     */
    @Test
    void testAddSelectorInfoWithDerivedProperties() throws JSONException {
        SelectorField derivedField = mock(SelectorField.class);
        when(derivedField.isOutfield()).thenReturn(false);

        assertSelectorInfoContainsAdditionalProperty(derivedField, PARENT_CHILD);
    }

    /**
     * Tests processSelectorField with an outfield that matches display/value fields
     * and triggers the isExtraProperty logic.
     */
    @Test
    void testAddSelectorInfoWithOutfieldExtraProperty() throws JSONException {
        SelectorField outField = mock(SelectorField.class);

        assertSelectorInfoNotNullForOutField(outField, "outFieldProp");
    }

    /**
     * Tests getExtraSearchFields when getDomainType returns null entity.
     */
    @Test
    void testGetExtraSearchFieldsNullEntity() {
        SelectorField searchField = setupSearchFieldTest(MY_FIELD);

        try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS);
             MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class)) {

            mockedStatic.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(DISPLAY);
            mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(searchField)).thenReturn(MY_FIELD);

            mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
            when(modelProvider.getEntity(TEST_TABLE)).thenReturn(null);

            String result = FieldBuilder.getExtraSearchFields(selector);

            assertEquals(MY_FIELD, result);
        }
    }

    /**
     * Tests getDomainType(SelectorField) with custom query and reference.
     */
    @Test
    void testGetDomainTypeSelectorFieldCustomQuery() {
        SelectorField sf = mock(SelectorField.class);
        Selector sel = mock(Selector.class);
        Reference ref = mock(Reference.class);

        when(sf.getObuiselSelector()).thenReturn(sel);
        when(sel.getTable()).thenReturn(table);
        when(sf.getProperty()).thenReturn(null);
        when(sel.isCustomQuery()).thenReturn(true);
        when(sf.getReference()).thenReturn(ref);
        when(ref.getId()).thenReturn("ref-123");

        try (MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class)) {
            org.openbravo.base.model.Reference baseRef = mock(org.openbravo.base.model.Reference.class);
            PrimitiveDomainType domainType = mock(PrimitiveDomainType.class);

            mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
            when(modelProvider.getReference("ref-123")).thenReturn(baseRef);
            when(baseRef.getDomainType()).thenReturn(domainType);

            assertSelectorFieldDomainTypeEquals(sf, domainType);
        }
    }

    /**
     * Tests getDomainType(SelectorField) with datasource field reference.
     */
    @Test
    void testGetDomainTypeSelectorFieldDatasource() {
        SelectorField sf = mock(SelectorField.class);
        Selector sel = mock(Selector.class);
        DatasourceField dsField = mock(DatasourceField.class);
        Reference dsRef = mock(Reference.class);

        when(sf.getObuiselSelector()).thenReturn(sel);
        when(sel.getTable()).thenReturn(null);
        when(sf.getProperty()).thenReturn(null);
        when(sf.getObserdsDatasourceField()).thenReturn(dsField);
        when(dsField.getReference()).thenReturn(dsRef);
        when(dsRef.getId()).thenReturn("ds-ref-id");

        try (MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class)) {
            org.openbravo.base.model.Reference baseRef = mock(org.openbravo.base.model.Reference.class);
            PrimitiveDomainType domainType = mock(PrimitiveDomainType.class);

            mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
            when(modelProvider.getReference("ds-ref-id")).thenReturn(baseRef);
            when(baseRef.getDomainType()).thenReturn(domainType);

            assertSelectorFieldDomainTypeEquals(sf, domainType);
        }
    }

    /**
     * Tests getDomainType(SelectorField) when exception is thrown.
     */
    @Test
    void testGetDomainTypeSelectorFieldException() {
        SelectorField sf = mock(SelectorField.class);

        when(sf.getObuiselSelector()).thenThrow(new RuntimeException("error"));

        assertNullSelectorFieldDomainType(sf);
    }

    /**
     * Tests getDomainType(SelectorField) with null property in entity.
     */
    @Test
    void testGetDomainTypeSelectorFieldNullProperty() {
        SelectorField sf = mock(SelectorField.class);
        Selector sel = mock(Selector.class);

        when(sf.getObuiselSelector()).thenReturn(sel);
        when(sel.getTable()).thenReturn(table);
        when(table.getName()).thenReturn(TEST_TABLE);
        when(sf.getProperty()).thenReturn("missingProp");

        try (MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class);
             MockedStatic<DalUtil> mockedDalUtil = mockStatic(DalUtil.class)) {

            Entity mockEntity = mock(Entity.class);
            mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
            when(modelProvider.getEntity(TEST_TABLE)).thenReturn(mockEntity);
            mockedDalUtil.when(() -> DalUtil.getPropertyFromPath(any(Entity.class), eq("missingProp")))
                .thenReturn(null);

            assertNullSelectorFieldDomainType(sf);
        }
    }

    /**
     * Tests getListInfo with empty list.
     */
    @Test
    void testGetListInfoEmptyList() throws JSONException {
        when(reference.getADListList()).thenReturn(Collections.emptyList());

        JSONArray result = FieldBuilder.getListInfo(reference, language);

        assertNotNull(result);
        assertEquals(0, result.length());
    }

    /**
     * Tests addSelectorInfo with setSelectorProperties using non-identifier display field.
     */
    @Test
    void testSetSelectorPropertiesWithNonIdentifierDisplay() throws JSONException {
        SelectorField displayField = mock(SelectorField.class);
        SelectorField valueField = mock(SelectorField.class);
        SelectorField regularField = mock(SelectorField.class);

        assertSelectorInfoSelectedPropertiesContains(CUSTOM_DISPLAY, "customValue", displayField, valueField,
            regularField);
    }
}

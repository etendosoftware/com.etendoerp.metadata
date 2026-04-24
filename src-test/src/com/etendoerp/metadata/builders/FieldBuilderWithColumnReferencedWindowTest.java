package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.etendorx.utils.DataSourceUtils;
import com.etendoerp.metadata.utils.Utils;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderWithColumnReferencedWindowTest {

    @Mock
    private Field field;
    @Mock
    private Column column;
    @Mock
    private Table table;
    @Mock
    private Tab currentTab;
    @Mock
    private Window currentWindow;
    @Mock
    private Tab defaultReferencedTab;
    @Mock
    private Tab resolvedReferencedTab;
    @Mock
    private Window salesOrderWindow;
    @Mock
    private Window purchaseOrderWindow;
    @Mock
    private Property fieldProperty;
    @Mock
    private Property referencedProperty;
    @Mock
    private Entity referencedEntity;
    @Mock
    private OBContext obContext;
    @Mock
    private Language language;
    @Mock
    private OBDal obDal;
    @Mock
    private OBCriteria<Tab> tabCriteria;

    private static final String FIELD_ID = "field-id";
    private static final String COLUMN_ID = "column-id";
    private static final String TABLE_ID = "table-id";
    private static final String SO_WINDOW_ID = "SO_WINDOW_ID";
    private static final String PO_WINDOW_ID = "PO_WINDOW_ID";
    private static final String SO_TAB_ID = "SO_TAB_ID";
    private static final String ENTITY_ORDER = "Order";
    private static final String REFERENCED_WINDOW_ID_KEY = REFERENCED_WINDOW_ID_KEY;

    @BeforeEach
    void setUp() {
        when(field.getId()).thenReturn(FIELD_ID);
        when(field.getColumn()).thenReturn(column);
        when(field.getTab()).thenReturn(currentTab);
        when(field.getName()).thenReturn("TestField");

        when(column.getId()).thenReturn(COLUMN_ID);
        when(column.getTable()).thenReturn(table);
        when(column.getDBColumnName()).thenReturn("C_Order_ID");

        when(currentTab.getWindow()).thenReturn(currentWindow);
        when(currentTab.getTable()).thenReturn(table);

        when(salesOrderWindow.getId()).thenReturn(SO_WINDOW_ID);
        when(salesOrderWindow.isSalesTransaction()).thenReturn(true);

        when(purchaseOrderWindow.getId()).thenReturn(PO_WINDOW_ID);
        when(purchaseOrderWindow.isSalesTransaction()).thenReturn(false);

        when(obContext.getLanguage()).thenReturn(language);
    }

    @Test
    void testResolvesToPurchaseOrderWindowInPurchaseContext() throws JSONException {
        when(currentWindow.isSalesTransaction()).thenReturn(false);
        when(defaultReferencedTab.getWindow()).thenReturn(salesOrderWindow);
        when(defaultReferencedTab.getId()).thenReturn(SO_TAB_ID);
        when(resolvedReferencedTab.getWindow()).thenReturn(purchaseOrderWindow);
        when(resolvedReferencedTab.getId()).thenReturn("PO_TAB_ID");

        JSONObject result = buildWithCriteriaResult(resolvedReferencedTab);

        assertEquals(PO_WINDOW_ID, result.getString(REFERENCED_WINDOW_ID_KEY));
    }

    @Test
    void testFallsBackToUtilsTabWhenCriteriaFindsNoTab() throws JSONException {
        when(currentWindow.isSalesTransaction()).thenReturn(true);
        when(defaultReferencedTab.getWindow()).thenReturn(salesOrderWindow);
        when(defaultReferencedTab.getId()).thenReturn(SO_TAB_ID);

        JSONObject result = buildWithCriteriaResult(null);

        assertEquals(SO_WINDOW_ID, result.getString(REFERENCED_WINDOW_ID_KEY));
    }

    @Test
    void testFallsBackToUtilsTabWhenReferencedWindowIdNeverSet() throws JSONException {
        when(currentWindow.isSalesTransaction()).thenReturn(true);
        when(defaultReferencedTab.getWindow()).thenReturn(salesOrderWindow);
        when(defaultReferencedTab.getId()).thenReturn(SO_TAB_ID);

        JSONObject result = buildWithAddReferencedPropertyThrowing();

        assertEquals(SO_WINDOW_ID, result.getString(REFERENCED_WINDOW_ID_KEY));
    }

    private JSONObject buildWithCriteriaResult(Tab criteriaResult) throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
             MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
             MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                     DataToJsonConverter.class,
                     (mock, ctx) -> {
                         when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                 .thenReturn(new JSONObject().put("id", FIELD_ID));
                         when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                 .thenReturn(new JSONObject().put("id", COLUMN_ID));
                     })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
            when(fieldProperty.getReferencedProperty()).thenReturn(referencedProperty);
            when(referencedProperty.getEntity()).thenReturn(referencedEntity);
            when(referencedEntity.getTableId()).thenReturn(TABLE_ID);
            when(referencedEntity.getName()).thenReturn(ENTITY_ORDER);

            mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(Table.class, TABLE_ID)).thenReturn(table);
            when(obDal.createCriteria(Tab.class)).thenReturn(tabCriteria);
            when(tabCriteria.createAlias(anyString(), anyString())).thenReturn(tabCriteria);
            when(tabCriteria.add(any())).thenReturn(tabCriteria);
            when(tabCriteria.setMaxResults(1)).thenReturn(tabCriteria);
            when(tabCriteria.uniqueResult()).thenReturn(criteriaResult);

            mockedUtils.when(() -> Utils.getReferencedTab(any())).thenReturn(defaultReferencedTab);
            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(anyBoolean(), anyString(), anyString()))
                    .thenReturn(new String[]{"order"});

            return new FieldBuilderWithColumn(field, null).toJSON();
        }
    }

    /**
     * Builds a FieldBuilderWithColumn JSON where addReferencedProperty exits early via its
     * catch block (KernelUtils throws on the first call), so REFERENCED_WINDOW_ID is never
     * set by that method. The second call (in addReferencedTableInfo) succeeds, exercising
     * the !json.has(REFERENCED_WINDOW_ID) == true branch of the guard.
     */
    private JSONObject buildWithAddReferencedPropertyThrowing() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
             MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
             MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                     DataToJsonConverter.class,
                     (mock, ctx) -> {
                         when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                 .thenReturn(new JSONObject().put("id", FIELD_ID));
                         when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                 .thenReturn(new JSONObject().put("id", COLUMN_ID));
                     })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);

            // First call (addReferencedProperty) throws — REFERENCED_WINDOW_ID never set.
            // Second call (addReferencedTableInfo) returns fieldProperty.
            mockedKernelUtils.when(() -> KernelUtils.getProperty(field))
                    .thenThrow(new RuntimeException("addReferencedProperty fails"))
                    .thenReturn(fieldProperty);
            when(fieldProperty.getReferencedProperty()).thenReturn(referencedProperty);
            when(referencedProperty.getEntity()).thenReturn(referencedEntity);
            when(referencedEntity.getName()).thenReturn(ENTITY_ORDER);

            mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);

            mockedUtils.when(() -> Utils.getReferencedTab(any())).thenReturn(defaultReferencedTab);
            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(anyBoolean(), anyString(), anyString()))
                    .thenReturn(new String[]{"order"});

            return new FieldBuilderWithColumn(field, null).toJSON();
        }
    }
}

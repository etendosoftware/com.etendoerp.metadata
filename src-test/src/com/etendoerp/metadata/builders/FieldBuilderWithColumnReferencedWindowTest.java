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
        // Context: Purchase Invoice (isSalesTransaction = false)
        when(currentWindow.isSalesTransaction()).thenReturn(false);

        // Default resolution returns Sales Order window
        when(defaultReferencedTab.getWindow()).thenReturn(salesOrderWindow);
        when(defaultReferencedTab.getId()).thenReturn("SO_TAB_ID");
        
        // Resolved resolution returns Purchase Order window
        when(resolvedReferencedTab.getWindow()).thenReturn(purchaseOrderWindow);
        when(resolvedReferencedTab.getId()).thenReturn("PO_TAB_ID");

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
             MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
             MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                     DataToJsonConverter.class,
                     (mock, context) -> {
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
            when(referencedEntity.getName()).thenReturn("Order");

            mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(Table.class, TABLE_ID)).thenReturn(table);
            
            // Mock resolution for Tab in the resolveReferencedTab method
            when(obDal.createCriteria(Tab.class)).thenReturn(tabCriteria);
            when(tabCriteria.createAlias(anyString(), anyString())).thenReturn(tabCriteria);
            when(tabCriteria.add(any())).thenReturn(tabCriteria);
            when(tabCriteria.setMaxResults(1)).thenReturn(tabCriteria);
            when(tabCriteria.uniqueResult()).thenReturn(resolvedReferencedTab);

            mockedUtils.when(() -> Utils.getReferencedTab(any())).thenReturn(defaultReferencedTab);

            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(anyBoolean(), anyString(), anyString()))
                    .thenReturn(new String[]{"order"});

            FieldBuilderWithColumn builder = new FieldBuilderWithColumn(field, null);
            JSONObject result = builder.toJSON();

            assertEquals(PO_WINDOW_ID, result.getString("referencedWindowId"));
        }
    }
}

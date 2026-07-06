package com.etendoerp.metadata.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.window.ApplicationDictionaryCachedStructures;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Field;

import java.util.List;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ADCacheProviderTest {

    private static final String WELD_NOT_AVAILABLE = "Weld not available";

    @Test
    void getWindowReturnsFromCache() {
        var adcs = mock(ApplicationDictionaryCachedStructures.class);
        var window = mock(Window.class);
        when(adcs.getWindow("123")).thenReturn(window);

        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class)).thenReturn(adcs);

            Window result = ADCacheProvider.getWindow("123");
            assertEquals(window, result);
        }
    }

    @Test
    void getTabReturnsFromCache() {
        var adcs = mock(ApplicationDictionaryCachedStructures.class);
        var tab = mock(Tab.class);
        when(adcs.getTab("456")).thenReturn(tab);

        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class)).thenReturn(adcs);

            Tab result = ADCacheProvider.getTab("456");
            assertEquals(tab, result);
        }
    }

    @Test
    void getFieldsOfTabReturnsFromCache() {
        var adcs = mock(ApplicationDictionaryCachedStructures.class);
        var tab = mock(Tab.class);
        var fields = List.of(mock(Field.class), mock(Field.class));
        when(adcs.getFieldsOfTab(tab)).thenReturn(fields);

        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class)).thenReturn(adcs);

            List<Field> result = ADCacheProvider.getFieldsOfTab(tab);
            assertEquals(2, result.size());
        }
    }

    @Test
    void getTableReturnsFromCache() {
        var adcs = mock(ApplicationDictionaryCachedStructures.class);
        var table = mock(Table.class);
        when(adcs.getTable("789")).thenReturn(table);

        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class)).thenReturn(adcs);

            Table result = ADCacheProvider.getTable("789");
            assertEquals(table, result);
        }
    }

    @Test
    void getWindowFallsBackOnWeldException() {
        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class))
                .thenThrow(new RuntimeException(WELD_NOT_AVAILABLE));

            assertNull(ADCacheProvider.getWindow("123"));
        }
    }

    @Test
    void getTabFallsBackOnWeldException() {
        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class))
                .thenThrow(new RuntimeException(WELD_NOT_AVAILABLE));

            assertNull(ADCacheProvider.getTab("456"));
        }
    }

    @Test
    void getFieldsOfTabFallsBackOnWeldException() {
        var tab = mock(Tab.class);

        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class))
                .thenThrow(new RuntimeException(WELD_NOT_AVAILABLE));

            assertNull(ADCacheProvider.getFieldsOfTab(tab));
        }
    }

    @Test
    void getTableFallsBackOnWeldException() {
        try (MockedStatic<WeldUtils> weld = mockStatic(WeldUtils.class)) {
            weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class))
                .thenThrow(new RuntimeException(WELD_NOT_AVAILABLE));

            assertNull(ADCacheProvider.getTable("789"));
        }
    }
}

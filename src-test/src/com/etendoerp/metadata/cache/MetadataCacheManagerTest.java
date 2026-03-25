package com.etendoerp.metadata.cache;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.etendoerp.metadata.builders.FieldBuilderWithColumn;
import com.etendoerp.metadata.builders.WindowBuilder;
import com.etendoerp.metadata.data.TabProcessor;

/**
 * Unit tests for {@link MetadataCacheManager}.
 * Verifies that invalidateAll() delegates to all four cache clearing methods.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class MetadataCacheManagerTest {

  @Test
  void invalidateAllClearsAllCaches() {
    try (
        MockedStatic<TabProcessor> tabProcessorMock = mockStatic(TabProcessor.class);
        MockedStatic<WindowBuilder> windowBuilderMock = mockStatic(WindowBuilder.class);
        MockedStatic<FieldBuilderWithColumn> fieldBuilderMock = mockStatic(FieldBuilderWithColumn.class)
    ) {
      MetadataCacheManager.invalidateAll();

      tabProcessorMock.verify(TabProcessor::clearFieldCache, times(1));
      tabProcessorMock.verify(TabProcessor::clearFieldAccessCache, times(1));
      windowBuilderMock.verify(WindowBuilder::clearTabAllowedCache, times(1));
      fieldBuilderMock.verify(FieldBuilderWithColumn::clearWindowAccessCache, times(1));
    }
  }

  @Test
  void invalidateAllCanBeCalledMultipleTimes() {
    try (
        MockedStatic<TabProcessor> tabProcessorMock = mockStatic(TabProcessor.class);
        MockedStatic<WindowBuilder> windowBuilderMock = mockStatic(WindowBuilder.class);
        MockedStatic<FieldBuilderWithColumn> fieldBuilderMock = mockStatic(FieldBuilderWithColumn.class)
    ) {
      MetadataCacheManager.invalidateAll();
      MetadataCacheManager.invalidateAll();

      tabProcessorMock.verify(TabProcessor::clearFieldCache, times(2));
      tabProcessorMock.verify(TabProcessor::clearFieldAccessCache, times(2));
      windowBuilderMock.verify(WindowBuilder::clearTabAllowedCache, times(2));
      fieldBuilderMock.verify(FieldBuilderWithColumn::clearWindowAccessCache, times(2));
    }
  }
}

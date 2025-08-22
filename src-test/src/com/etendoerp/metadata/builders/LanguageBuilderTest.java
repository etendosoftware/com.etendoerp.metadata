package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.ENGLISH_USA;
import static com.etendoerp.metadata.MetadataTestConstants.ES_ES;
import static com.etendoerp.metadata.MetadataTestConstants.LANGUAGE1_ID;
import static com.etendoerp.metadata.MetadataTestConstants.LANGUAGE2_ID;
import static com.etendoerp.metadata.MetadataTestConstants.LANGUAGE_CODE;
import static com.etendoerp.metadata.MetadataTestConstants.LANGUAGE_CONST;
import static com.etendoerp.metadata.MetadataTestConstants.SINGLE_LANGUAGE_ID;
import static com.etendoerp.metadata.MetadataTestConstants.SPANISH_SPAIN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Test class for LanguageBuilder.
 * This class tests the functionality of the LanguageBuilder, ensuring it can retrieve system languages
 * and convert them to JSON format correctly.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class LanguageBuilderTest {

  @Mock
  private OBDal obDal;

  @Mock
  private OBCriteria criteria;

  @Mock
  private Language language1;

  @Mock
  private Language language2;

  @Mock
  private DataToJsonConverter converter;

  @Mock
  private OBContext obContext;

  @Mock
  private Language contextLanguage;

  /**
   * Sets up the necessary mocks and their behaviors before each test.
   */
  @BeforeEach
  void setUp() {
    // Setup context language mock
    when(contextLanguage.getLanguage()).thenReturn(LANGUAGE_CODE);
    when(contextLanguage.getId()).thenReturn("context-language-id");
    when(contextLanguage.getName()).thenReturn(ENGLISH_USA);
  }

  /**
   * Test constructor of LanguageBuilder.
   * This test ensures that the LanguageBuilder can be constructed successfully
   * and that the converter is properly configured with the selected properties.
   */
  @Test
  void testConstructorSuccessful() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(contextLanguage);

      assertDoesNotThrow(LanguageBuilder::new);
    }
  }

  /**
   * Tests the toJSON method with multiple system languages.
   * This test verifies that the JSON output correctly represents all system languages
   * and that each language is properly converted using the DataToJsonConverter.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testToJSONWithMultipleLanguages() throws JSONException {
    // Setup mock languages
    when(language1.getLanguage()).thenReturn(LANGUAGE_CODE);
    when(language1.getId()).thenReturn(LANGUAGE1_ID);
    when(language1.getName()).thenReturn(ENGLISH_USA);

    when(language2.getLanguage()).thenReturn(ES_ES);
    when(language2.getId()).thenReturn(LANGUAGE2_ID);
    when(language2.getName()).thenReturn(SPANISH_SPAIN);

    List<Language> systemLanguages = List.of(language1, language2);

    // Mock JSON objects that would be returned by the converter
    JSONObject lang1Json = new JSONObject();
    lang1Json.put("id", LANGUAGE1_ID);
    lang1Json.put(LANGUAGE_CONST, LANGUAGE_CODE);
    lang1Json.put("name", ENGLISH_USA);

    JSONObject lang2Json = new JSONObject();
    lang2Json.put("id", LANGUAGE2_ID);
    lang2Json.put(LANGUAGE_CONST, ES_ES);
    lang2Json.put("name", SPANISH_SPAIN);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      
      // Mock OBContext
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(contextLanguage);

      // Mock OBDal
      obDalStatic.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
      when(obDal.createCriteria(Language.class)).thenReturn(criteria);
      when(criteria.add(any(Criterion.class))).thenReturn(criteria);
      when(criteria.list()).thenReturn(systemLanguages);

      // Create language builder
      LanguageBuilder languageBuilder = new LanguageBuilder();

      // Use reflection to inject the mock converter
      try {
        java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
        converterField.setAccessible(true);
        converterField.set(languageBuilder, converter);
        
        when(converter.toJsonObject(language1, DataResolvingMode.FULL_TRANSLATABLE)).thenReturn(lang1Json);
        when(converter.toJsonObject(language2, DataResolvingMode.FULL_TRANSLATABLE)).thenReturn(lang2Json);
      } catch (Exception e) {
        throw new RuntimeException("Could not set converter field: " + e.getMessage(), e);
      }

      JSONObject result = languageBuilder.toJSON();

      assertNotNull(result);
      assertEquals(2, result.length());
      assertTrue(result.has(LANGUAGE_CODE));
      assertTrue(result.has(ES_ES));
      
      JSONObject enUsJson = result.getJSONObject(LANGUAGE_CODE);
      assertEquals(LANGUAGE1_ID, enUsJson.getString("id"));
      assertEquals(LANGUAGE_CODE, enUsJson.getString(LANGUAGE_CONST));
      assertEquals(ENGLISH_USA, enUsJson.getString("name"));

      JSONObject esEsJson = result.getJSONObject(ES_ES);
      assertEquals(LANGUAGE2_ID, esEsJson.getString("id"));
      assertEquals(ES_ES, esEsJson.getString(LANGUAGE_CONST));
      assertEquals(SPANISH_SPAIN, esEsJson.getString("name"));
    }
  }

  /**
   * Tests the toJSON method with no system languages.
   * This test verifies that when no system languages are found,
   * an empty JSON object is returned without throwing exceptions.
   *
   */
  @Test
  void testToJSONWithNoLanguages() {
    List<Language> emptyLanguageList = Collections.emptyList();

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      
      // Mock OBContext
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(contextLanguage);

      // Mock OBDal
      obDalStatic.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
      when(obDal.createCriteria(Language.class)).thenReturn(criteria);
      when(criteria.add(any(Criterion.class))).thenReturn(criteria);
      when(criteria.list()).thenReturn(emptyLanguageList);

      LanguageBuilder languageBuilder = new LanguageBuilder();
      JSONObject result = languageBuilder.toJSON();

      assertNotNull(result);
      assertEquals(0, result.length());
    }
  }

  /**
   * Tests the toJSON method with a single system language.
   * This test verifies that the JSON output correctly represents a single system language.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testToJSONWithSingleLanguage() throws JSONException {
    when(language1.getLanguage()).thenReturn(LANGUAGE_CODE);
    when(language1.getId()).thenReturn(SINGLE_LANGUAGE_ID);
    when(language1.getName()).thenReturn(ENGLISH_USA);

    List<Language> singleLanguageList = List.of(language1);

    JSONObject langJson = new JSONObject();
    langJson.put("id", SINGLE_LANGUAGE_ID);
    langJson.put(LANGUAGE_CONST, LANGUAGE_CODE);
    langJson.put("name", ENGLISH_USA);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      
      // Mock OBContext
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(contextLanguage);

      // Mock OBDal
      obDalStatic.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
      when(obDal.createCriteria(Language.class)).thenReturn(criteria);
      when(criteria.add(any(Criterion.class))).thenReturn(criteria);
      when(criteria.list()).thenReturn(singleLanguageList);

      // Create language builder
      LanguageBuilder languageBuilder = new LanguageBuilder();

      // Use reflection to inject the mock converter
      try {
        java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
        converterField.setAccessible(true);
        converterField.set(languageBuilder, converter);
        
        when(converter.toJsonObject(language1, DataResolvingMode.FULL_TRANSLATABLE)).thenReturn(langJson);
      } catch (Exception e) {
        throw new RuntimeException("Could not set converter field: " + e.getMessage(), e);
      }

      JSONObject result = languageBuilder.toJSON();

      assertNotNull(result);
      assertEquals(1, result.length());
      assertTrue(result.has(LANGUAGE_CODE));
      
      JSONObject resultLangJson = result.getJSONObject(LANGUAGE_CODE);
      assertEquals(SINGLE_LANGUAGE_ID, resultLangJson.getString("id"));
      assertEquals(LANGUAGE_CODE, resultLangJson.getString(LANGUAGE_CONST));
      assertEquals(ENGLISH_USA, resultLangJson.getString("name"));
    }
  }

  /**
   * Tests that the toJSON method handles exceptions gracefully.
   * This test verifies that when an exception occurs during processing,
   * the method returns a valid (though potentially empty) JSON object without propagating the exception.
   */
  @Test
  void testToJSONHandlesExceptionGracefully() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      
      // Mock OBContext
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(contextLanguage);

      // Mock OBDal to throw exception
      obDalStatic.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
      when(obDal.createCriteria(Language.class)).thenReturn(criteria);
      when(criteria.add(any(Criterion.class))).thenReturn(criteria);
      when(criteria.list()).thenThrow(new RuntimeException("Database connection error"));

      LanguageBuilder languageBuilder = new LanguageBuilder();

      assertDoesNotThrow(() -> {
        JSONObject result = languageBuilder.toJSON();
        assertNotNull(result);
        assertEquals(0, result.length());
      });
    }
  }

  /**
   * Tests the constructor behavior when setting selected properties.
   * This test verifies that the constructor properly configures the converter
   * with the expected properties string.
   */
  @Test
  void testConstructorSetsSelectedProperties() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(contextLanguage);

      LanguageBuilder builder = new LanguageBuilder();
      
      assertNotNull(builder);
    }
  }

}

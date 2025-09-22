package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.ACTION;
import static com.etendoerp.metadata.MetadataTestConstants.ACTIVE;
import static com.etendoerp.metadata.MetadataTestConstants.BUTTON_TYPE;
import static com.etendoerp.metadata.MetadataTestConstants.CLIENT;
import static com.etendoerp.metadata.MetadataTestConstants.CLIENT_ID;
import static com.etendoerp.metadata.MetadataTestConstants.DESCRIPTION;
import static com.etendoerp.metadata.MetadataTestConstants.END_ROW;
import static com.etendoerp.metadata.MetadataTestConstants.ENTITY_NAME_PROPERTY;
import static com.etendoerp.metadata.MetadataTestConstants.ETMETA_ACTION_HANDLER;
import static com.etendoerp.metadata.MetadataTestConstants.IDENTIFIER_PROPERTY;
import static com.etendoerp.metadata.MetadataTestConstants.LANGUAGE_CODE;
import static com.etendoerp.metadata.MetadataTestConstants.MODULE;
import static com.etendoerp.metadata.MetadataTestConstants.NAME_KEY;
import static com.etendoerp.metadata.MetadataTestConstants.ORGANIZATION;
import static com.etendoerp.metadata.MetadataTestConstants.ORG_ID;
import static com.etendoerp.metadata.MetadataTestConstants.RESPONSE;
import static com.etendoerp.metadata.MetadataTestConstants.SECTION;
import static com.etendoerp.metadata.MetadataTestConstants.SEQNO;
import static com.etendoerp.metadata.MetadataTestConstants.START_ROW;
import static com.etendoerp.metadata.MetadataTestConstants.STATUS;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_DATE;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_TOOLBAR_1;
import static com.etendoerp.metadata.MetadataTestConstants.TOOLBAR1_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TOTAL_ROWS;
import static com.etendoerp.metadata.MetadataTestConstants.USER_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
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
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Message;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.data.Toolbar;

/**
 * Test class for ToolbarBuilder.
 * This class tests the functionality of the ToolbarBuilder, ensuring it can build toolbar
 * JSON representations correctly and handle various scenarios including error cases.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ToolbarBuilderTest {

  @Mock
  private OBContext obContext;

  @Mock
  private Language language;

  @Mock
  private OBDal obDal;

  @Mock
  private OBCriteria<Toolbar> criteria;

  @Mock
  private Toolbar toolbar1;

  @Mock
  private Toolbar toolbar2;

  @Mock
  private Client client;

  @Mock
  private Organization organization;

  @Mock
  private User user;

  @Mock
  private Module module;

  @Mock
  private Message nameKey1;

  /**
   * Sets up the necessary mocks and their behaviors before each test.
   */
  @BeforeEach
  void setUp() {
    // Setup context mocks
    when(obContext.getLanguage()).thenReturn(language);
    when(language.getLanguage()).thenReturn(LANGUAGE_CODE);

    // Setup toolbar1 mock
    when(toolbar1.getId()).thenReturn(TOOLBAR1_ID);
    when(toolbar1.getClient()).thenReturn(client);
    when(toolbar1.getOrganization()).thenReturn(organization);
    when(toolbar1.isActive()).thenReturn(true);
    when(toolbar1.getCreationDate()).thenReturn(TEST_DATE);
    when(toolbar1.getCreatedBy()).thenReturn(user);
    when(toolbar1.getUpdated()).thenReturn(TEST_DATE);
    when(toolbar1.getUpdatedBy()).thenReturn(user);
    when(toolbar1.getIdentifier()).thenReturn("toolbar1-identifier");
    when(toolbar1.getEntityName()).thenReturn("Toolbar1");
    when(toolbar1.getName()).thenReturn(TEST_TOOLBAR_1);
    when(toolbar1.getIcon()).thenReturn("icon1.png");
    when(toolbar1.getSeqno()).thenReturn(10L);
    when(toolbar1.getDescription()).thenReturn("Test toolbar description 1");
    when(toolbar1.getEtmetaActionHandler()).thenReturn("actionHandler1");
    when(toolbar1.getNameKey()).thenReturn(nameKey1);
    when(toolbar1.getAction()).thenReturn("action1");
    when(toolbar1.getButtontype()).thenReturn("button");
    when(toolbar1.getSection()).thenReturn("section1");
    when(toolbar1.getModule()).thenReturn(module);

    // Setup toolbar2 mock
    when(toolbar2.getId()).thenReturn("toolbar2-id");
    when(toolbar2.getClient()).thenReturn(client);
    when(toolbar2.getOrganization()).thenReturn(organization);
    when(toolbar2.isActive()).thenReturn(false);
    when(toolbar2.getCreationDate()).thenReturn(TEST_DATE);
    when(toolbar2.getCreatedBy()).thenReturn(user);
    when(toolbar2.getUpdated()).thenReturn(TEST_DATE);
    when(toolbar2.getUpdatedBy()).thenReturn(user);
    when(toolbar2.getIdentifier()).thenReturn("toolbar2-identifier");
    when(toolbar2.getEntityName()).thenReturn("Toolbar2");
    when(toolbar2.getName()).thenReturn("Test Toolbar 2");
    when(toolbar2.getIcon()).thenReturn("icon2.png");
    when(toolbar2.getSeqno()).thenReturn(20L);
    when(toolbar2.getDescription()).thenReturn("Test toolbar description 2");
    when(toolbar2.getEtmetaActionHandler()).thenReturn("actionHandler2");
    when(toolbar2.getNameKey()).thenReturn(null);
    when(toolbar2.getAction()).thenReturn("action2");
    when(toolbar2.getButtontype()).thenReturn("link");
    when(toolbar2.getSection()).thenReturn("section2");
    when(toolbar2.getModule()).thenReturn(null);

    // Setup entity mocks
    when(client.getId()).thenReturn(CLIENT_ID);
    when(organization.getId()).thenReturn(ORG_ID);
    when(user.getId()).thenReturn(USER_ID);
    when(module.getId()).thenReturn("module-id");
    when(nameKey1.toString()).thenReturn("toolbar1.name");
  }

  /**
   * Verifies that toJSON builds a correct response when multiple Toolbar entries exist.
   *
   * Ensures:
   * - Response wrapper contains expected fields and counts.
   * - Each Toolbar is mapped to JSON with all expected properties.
   * - Optional fields like nameKey and module are properly handled across entries.
   *
   * @throws JSONException if JSON parsing or construction fails during assertions
   */
  @Test
  void testToJSONSuccessfulWithMultipleToolbars() throws JSONException {
    List<Toolbar> toolbarList = Arrays.asList(toolbar1, toolbar2);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.createCriteria(Toolbar.class)).thenReturn(criteria);
      when(criteria.list()).thenReturn(toolbarList);

      ToolbarBuilder toolbarBuilder = new ToolbarBuilder();
      JSONObject result = toolbarBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(RESPONSE));

      JSONObject response = result.getJSONObject(RESPONSE);
      assertTrue(response.has("data"));
      assertTrue(response.has(START_ROW));
      assertTrue(response.has(END_ROW));
      assertTrue(response.has(TOTAL_ROWS));
      assertTrue(response.has(STATUS));

      assertEquals(0, response.getInt(START_ROW));
      assertEquals(1, response.getInt(END_ROW));
      assertEquals(2, response.getInt(TOTAL_ROWS));
      assertEquals(0, response.getInt(STATUS));

      JSONArray data = response.getJSONArray("data");
      assertEquals(2, data.length());

      // Verify first toolbar
      JSONObject toolbar1Json = data.getJSONObject(0);
      assertEquals(TOOLBAR1_ID, toolbar1Json.getString("id"));
      assertEquals(CLIENT_ID, toolbar1Json.getString(CLIENT));
      assertEquals(ORG_ID, toolbar1Json.getString(ORGANIZATION));
      assertTrue(toolbar1Json.getBoolean(ACTIVE));
      assertEquals("toolbar1-identifier", toolbar1Json.getString(IDENTIFIER_PROPERTY));
      assertEquals("Toolbar1", toolbar1Json.getString(ENTITY_NAME_PROPERTY));
      assertEquals(TEST_TOOLBAR_1, toolbar1Json.getString("name"));
      assertEquals("icon1.png", toolbar1Json.getString("icon"));
      assertEquals(10, toolbar1Json.getLong(SEQNO));
      assertEquals("Test toolbar description 1", toolbar1Json.getString(DESCRIPTION));
      assertEquals("actionHandler1", toolbar1Json.getString(ETMETA_ACTION_HANDLER));
      assertEquals("toolbar1.name", toolbar1Json.getString(NAME_KEY));
      assertEquals("action1", toolbar1Json.getString(ACTION));
      assertEquals("button", toolbar1Json.getString(BUTTON_TYPE));
      assertEquals("section1", toolbar1Json.getString(SECTION));
      assertEquals("module-id", toolbar1Json.getString(MODULE));

      // Verify second toolbar
      JSONObject toolbar2Json = data.getJSONObject(1);
      assertEquals("toolbar2-id", toolbar2Json.getString("id"));
      assertEquals(CLIENT_ID, toolbar2Json.getString(CLIENT));
      assertEquals(ORG_ID, toolbar2Json.getString(ORGANIZATION));
      assertEquals(false, toolbar2Json.getBoolean(ACTIVE));
      assertEquals("toolbar2-identifier", toolbar2Json.getString(IDENTIFIER_PROPERTY));
      assertEquals("Toolbar2", toolbar2Json.getString(ENTITY_NAME_PROPERTY));
      assertEquals("Test Toolbar 2", toolbar2Json.getString("name"));
      assertEquals("icon2.png", toolbar2Json.getString("icon"));
      assertEquals(20, toolbar2Json.getLong(SEQNO));
      assertEquals("Test toolbar description 2", toolbar2Json.getString(DESCRIPTION));
      assertEquals("actionHandler2", toolbar2Json.getString(ETMETA_ACTION_HANDLER));
      assertTrue(toolbar2Json.isNull(NAME_KEY));
      assertEquals("action2", toolbar2Json.getString(ACTION));
      assertEquals("link", toolbar2Json.getString(BUTTON_TYPE));
      assertEquals("section2", toolbar2Json.getString(SECTION));
      assertTrue(toolbar2Json.isNull(MODULE));
    }
  }

  /**
   * Verifies that toJSON returns an empty data array and proper response metadata
   * when the Toolbar list is empty.
   *
   * Ensures startRow, endRow, totalRows and status are consistent with no results.
   *
   * @throws JSONException if JSON parsing or construction fails during assertions
   */
  @Test
  void testToJSONWithEmptyToolbarList() throws JSONException {
    List<Toolbar> emptyList = Collections.emptyList();

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.createCriteria(Toolbar.class)).thenReturn(criteria);
      when(criteria.list()).thenReturn(emptyList);

      ToolbarBuilder toolbarBuilder = new ToolbarBuilder();
      JSONObject result = toolbarBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(RESPONSE));

      JSONObject response = result.getJSONObject(RESPONSE);
      assertEquals(0, response.getInt(START_ROW));
      assertEquals(-1, response.getInt(END_ROW));
      assertEquals(0, response.getInt(TOTAL_ROWS));
      assertEquals(0, response.getInt(STATUS));

      JSONArray data = response.getJSONArray("data");
      assertEquals(0, data.length());
    }
  }

  /**
   * Verifies that toJSON returns a response with exactly one element when a single
   * Toolbar record is present, and that key fields are correctly mapped.
   *
   * @throws JSONException if JSON parsing or construction fails during assertions
   */
  @Test
  void testToJSONWithSingleToolbar() throws JSONException {
    List<Toolbar> singleToolbarList = Arrays.asList(toolbar1);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.createCriteria(Toolbar.class)).thenReturn(criteria);
      when(criteria.list()).thenReturn(singleToolbarList);

      ToolbarBuilder toolbarBuilder = new ToolbarBuilder();
      JSONObject result = toolbarBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(RESPONSE));

      JSONObject response = result.getJSONObject(RESPONSE);
      assertEquals(0, response.getInt(START_ROW));
      assertEquals(0, response.getInt(END_ROW));
      assertEquals(1, response.getInt(TOTAL_ROWS));
      assertEquals(0, response.getInt(STATUS));

      JSONArray data = response.getJSONArray("data");
      assertEquals(1, data.length());

      JSONObject toolbarJson = data.getJSONObject(0);
      assertEquals(TOOLBAR1_ID, toolbarJson.getString("id"));
      assertEquals(TEST_TOOLBAR_1, toolbarJson.getString("name"));
    }
  }

  /**
   * Verifies that the module field is serialized as null when the Toolbar has no associated Module.
   *
   * @throws JSONException if JSON parsing or construction fails during assertions
   */
  @Test
  void testToolbarToJSONWithNullModule() throws JSONException {
    when(toolbar2.getModule()).thenReturn(null);
    List<Toolbar> toolbarList = Arrays.asList(toolbar2);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.createCriteria(Toolbar.class)).thenReturn(criteria);
      when(criteria.list()).thenReturn(toolbarList);

      ToolbarBuilder toolbarBuilder = new ToolbarBuilder();
      JSONObject result = toolbarBuilder.toJSON();

      assertNotNull(result);
      JSONObject response = result.getJSONObject(RESPONSE);
      JSONArray data = response.getJSONArray("data");
      JSONObject toolbarJson = data.getJSONObject(0);

      assertTrue(toolbarJson.isNull(MODULE));
    }
  }

  /**
   * Test that ToolbarBuilder constructor works correctly.
   */
  @Test
  void testConstructorSuccessful() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);

      assertDoesNotThrow(ToolbarBuilder::new);
    }
  }

  /**
   * Ensures that all expected Toolbar properties are included in the JSON output and that
   * date fields are formatted as strings.
   *
   * @throws JSONException if JSON parsing or construction fails during assertions
   */
  @Test
  void testToolbarPropertiesMapping() throws JSONException {
    List<Toolbar> toolbarList = Arrays.asList(toolbar1);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.createCriteria(Toolbar.class)).thenReturn(criteria);
      when(criteria.list()).thenReturn(toolbarList);

      ToolbarBuilder toolbarBuilder = new ToolbarBuilder();
      JSONObject result = toolbarBuilder.toJSON();

      JSONObject response = result.getJSONObject(RESPONSE);
      JSONArray data = response.getJSONArray("data");
      JSONObject toolbarJson = data.getJSONObject(0);

      // Verify all expected properties are present
      assertTrue(toolbarJson.has("id"));
      assertTrue(toolbarJson.has(CLIENT));
      assertTrue(toolbarJson.has(ORGANIZATION));
      assertTrue(toolbarJson.has(ACTIVE));
      assertTrue(toolbarJson.has("creationDate"));
      assertTrue(toolbarJson.has("createdBy"));
      assertTrue(toolbarJson.has("updated"));
      assertTrue(toolbarJson.has("updatedBy"));
      assertTrue(toolbarJson.has(IDENTIFIER_PROPERTY));
      assertTrue(toolbarJson.has(ENTITY_NAME_PROPERTY));
      assertTrue(toolbarJson.has("name"));
      assertTrue(toolbarJson.has("icon"));
      assertTrue(toolbarJson.has(SEQNO));
      assertTrue(toolbarJson.has(DESCRIPTION));
      assertTrue(toolbarJson.has(ETMETA_ACTION_HANDLER));
      assertTrue(toolbarJson.has(NAME_KEY));
      assertTrue(toolbarJson.has(ACTION));
      assertTrue(toolbarJson.has(BUTTON_TYPE));
      assertTrue(toolbarJson.has(SECTION));
      assertTrue(toolbarJson.has(MODULE));

      // Verify date fields are converted to strings
      assertEquals(TEST_DATE.toString(), toolbarJson.getString("creationDate"));
      assertEquals(TEST_DATE.toString(), toolbarJson.getString("updated"));
    }
  }

  /**
   * Validates the overall response structure produced by toJSON, including presence of
   * wrapper fields (data, startRow, endRow, totalRows, status) and correct data length.
   *
   * @throws JSONException if JSON parsing or construction fails during assertions
   */
  @Test
  void testResponseStructure() throws JSONException {
    List<Toolbar> toolbarList = Arrays.asList(toolbar1, toolbar2);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.createCriteria(Toolbar.class)).thenReturn(criteria);
      when(criteria.list()).thenReturn(toolbarList);

      ToolbarBuilder toolbarBuilder = new ToolbarBuilder();
      JSONObject result = toolbarBuilder.toJSON();

      assertTrue(result.has(RESPONSE));
      JSONObject response = result.getJSONObject(RESPONSE);

      // Verify response structure
      assertTrue(response.has("data"));
      assertTrue(response.has(START_ROW));
      assertTrue(response.has(END_ROW));
      assertTrue(response.has(TOTAL_ROWS));
      assertTrue(response.has(STATUS));

      // Verify response values
      assertEquals(0, response.getInt(START_ROW));
      assertEquals(1, response.getInt(END_ROW)); // length - 1
      assertEquals(2, response.getInt(TOTAL_ROWS));
      assertEquals(0, response.getInt(STATUS));

      // Verify data is an array
      assertTrue(response.get("data") instanceof JSONArray);
      JSONArray data = response.getJSONArray("data");
      assertEquals(2, data.length());
    }
  }
}

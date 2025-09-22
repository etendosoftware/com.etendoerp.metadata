package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.CLIENT_ID;
import static com.etendoerp.metadata.MetadataTestConstants.LANGUAGE_CODE;
import static com.etendoerp.metadata.MetadataTestConstants.ORG_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_DATE;
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
    when(toolbar1.getId()).thenReturn("toolbar1-id");
    when(toolbar1.getClient()).thenReturn(client);
    when(toolbar1.getOrganization()).thenReturn(organization);
    when(toolbar1.isActive()).thenReturn(true);
    when(toolbar1.getCreationDate()).thenReturn(TEST_DATE);
    when(toolbar1.getCreatedBy()).thenReturn(user);
    when(toolbar1.getUpdated()).thenReturn(TEST_DATE);
    when(toolbar1.getUpdatedBy()).thenReturn(user);
    when(toolbar1.getIdentifier()).thenReturn("toolbar1-identifier");
    when(toolbar1.getEntityName()).thenReturn("Toolbar1");
    when(toolbar1.getName()).thenReturn("Test Toolbar 1");
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
      assertTrue(result.has("response"));

      JSONObject response = result.getJSONObject("response");
      assertTrue(response.has("data"));
      assertTrue(response.has("startRow"));
      assertTrue(response.has("endRow"));
      assertTrue(response.has("totalRows"));
      assertTrue(response.has("status"));

      assertEquals(0, response.getInt("startRow"));
      assertEquals(1, response.getInt("endRow"));
      assertEquals(2, response.getInt("totalRows"));
      assertEquals(0, response.getInt("status"));

      JSONArray data = response.getJSONArray("data");
      assertEquals(2, data.length());

      // Verify first toolbar
      JSONObject toolbar1Json = data.getJSONObject(0);
      assertEquals("toolbar1-id", toolbar1Json.getString("id"));
      assertEquals(CLIENT_ID, toolbar1Json.getString("client"));
      assertEquals(ORG_ID, toolbar1Json.getString("organization"));
      assertTrue(toolbar1Json.getBoolean("active"));
      assertEquals("toolbar1-identifier", toolbar1Json.getString("identifier"));
      assertEquals("Toolbar1", toolbar1Json.getString("entityName"));
      assertEquals("Test Toolbar 1", toolbar1Json.getString("name"));
      assertEquals("icon1.png", toolbar1Json.getString("icon"));
      assertEquals(10, toolbar1Json.getLong("seqno"));
      assertEquals("Test toolbar description 1", toolbar1Json.getString("description"));
      assertEquals("actionHandler1", toolbar1Json.getString("etmetaActionHandler"));
      assertEquals("toolbar1.name", toolbar1Json.getString("nameKey"));
      assertEquals("action1", toolbar1Json.getString("action"));
      assertEquals("button", toolbar1Json.getString("buttonType"));
      assertEquals("section1", toolbar1Json.getString("section"));
      assertEquals("module-id", toolbar1Json.getString("module"));

      // Verify second toolbar
      JSONObject toolbar2Json = data.getJSONObject(1);
      assertEquals("toolbar2-id", toolbar2Json.getString("id"));
      assertEquals(CLIENT_ID, toolbar2Json.getString("client"));
      assertEquals(ORG_ID, toolbar2Json.getString("organization"));
      assertEquals(false, toolbar2Json.getBoolean("active"));
      assertEquals("toolbar2-identifier", toolbar2Json.getString("identifier"));
      assertEquals("Toolbar2", toolbar2Json.getString("entityName"));
      assertEquals("Test Toolbar 2", toolbar2Json.getString("name"));
      assertEquals("icon2.png", toolbar2Json.getString("icon"));
      assertEquals(20, toolbar2Json.getLong("seqno"));
      assertEquals("Test toolbar description 2", toolbar2Json.getString("description"));
      assertEquals("actionHandler2", toolbar2Json.getString("etmetaActionHandler"));
      assertTrue(toolbar2Json.isNull("nameKey"));
      assertEquals("action2", toolbar2Json.getString("action"));
      assertEquals("link", toolbar2Json.getString("buttonType"));
      assertEquals("section2", toolbar2Json.getString("section"));
      assertTrue(toolbar2Json.isNull("module"));
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
      assertTrue(result.has("response"));

      JSONObject response = result.getJSONObject("response");
      assertEquals(0, response.getInt("startRow"));
      assertEquals(-1, response.getInt("endRow"));
      assertEquals(0, response.getInt("totalRows"));
      assertEquals(0, response.getInt("status"));

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
      assertTrue(result.has("response"));

      JSONObject response = result.getJSONObject("response");
      assertEquals(0, response.getInt("startRow"));
      assertEquals(0, response.getInt("endRow"));
      assertEquals(1, response.getInt("totalRows"));
      assertEquals(0, response.getInt("status"));

      JSONArray data = response.getJSONArray("data");
      assertEquals(1, data.length());

      JSONObject toolbarJson = data.getJSONObject(0);
      assertEquals("toolbar1-id", toolbarJson.getString("id"));
      assertEquals("Test Toolbar 1", toolbarJson.getString("name"));
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
      JSONObject response = result.getJSONObject("response");
      JSONArray data = response.getJSONArray("data");
      JSONObject toolbarJson = data.getJSONObject(0);

      assertTrue(toolbarJson.isNull("module"));
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

      JSONObject response = result.getJSONObject("response");
      JSONArray data = response.getJSONArray("data");
      JSONObject toolbarJson = data.getJSONObject(0);

      // Verify all expected properties are present
      assertTrue(toolbarJson.has("id"));
      assertTrue(toolbarJson.has("client"));
      assertTrue(toolbarJson.has("organization"));
      assertTrue(toolbarJson.has("active"));
      assertTrue(toolbarJson.has("creationDate"));
      assertTrue(toolbarJson.has("createdBy"));
      assertTrue(toolbarJson.has("updated"));
      assertTrue(toolbarJson.has("updatedBy"));
      assertTrue(toolbarJson.has("identifier"));
      assertTrue(toolbarJson.has("entityName"));
      assertTrue(toolbarJson.has("name"));
      assertTrue(toolbarJson.has("icon"));
      assertTrue(toolbarJson.has("seqno"));
      assertTrue(toolbarJson.has("description"));
      assertTrue(toolbarJson.has("etmetaActionHandler"));
      assertTrue(toolbarJson.has("nameKey"));
      assertTrue(toolbarJson.has("action"));
      assertTrue(toolbarJson.has("buttonType"));
      assertTrue(toolbarJson.has("section"));
      assertTrue(toolbarJson.has("module"));

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

      assertTrue(result.has("response"));
      JSONObject response = result.getJSONObject("response");

      // Verify response structure
      assertTrue(response.has("data"));
      assertTrue(response.has("startRow"));
      assertTrue(response.has("endRow"));
      assertTrue(response.has("totalRows"));
      assertTrue(response.has("status"));

      // Verify response values
      assertEquals(0, response.getInt("startRow"));
      assertEquals(1, response.getInt("endRow")); // length - 1
      assertEquals(2, response.getInt("totalRows"));
      assertEquals(0, response.getInt("status"));

      // Verify data is an array
      assertTrue(response.get("data") instanceof JSONArray);
      JSONArray data = response.getJSONArray("data");
      assertEquals(2, data.length());
    }
  }
}

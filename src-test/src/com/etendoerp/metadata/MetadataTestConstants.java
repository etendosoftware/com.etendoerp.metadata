package com.etendoerp.metadata;

import java.util.Date;

/**
 * Constants used across test classes in the metadata module.
 * This class centralizes commonly used test values to improve maintainability
 * and consistency across all test cases.
 */
public final class MetadataTestConstants {

  public static final String USERNAME = "username";
  public static final String TEST_USER = "testuser";
  public static final String PASSWORD = "password";
  public static final String TEST_PASSWORD = "testpass";
  public static final String TOKEN = "token";
  public static final String TEST_JWT_TOKEN = "test-jwt-token";
  public static final String AUTHORIZATION = "Authorization";
  public static final String USER_ID = "user-id";
  public static final String ROLE_ID = "role-id";
  public static final String DEFAULT_ROLE_ID = "default-role-id";

  public static final String CLIENT_ID = "client-id";
  public static final String ORG_ID = "org-id";
  public static final String WAREHOUSE_ID = "warehouse-id";

  public static final String SESSION_ID = "session-id";
  public static final String FIELD_ID = "field-id";
  public static final String DISPLAY_PROPERTY = "displayProperty";
  public static final String TEST_FIELD = "testField";
  public static final String PARENT_ID = "parent-id";
  public static final String GRANDCHILD_ID = "grandchild-id";
  public static final String CHILDREN = "children";
  public static final String READ_ONLY_LOGIC_EXPRESSION = "readOnlyLogicExpression";
  public static final String SELECTOR = "selector";
  public static final String REF_LIST = "refList";
  public static final String WINDOW = "window";
  public static final String DATASOURCE_NAME = "datasourceName";
  public static final String TEST_PROCESS_ID = "testProcessId";
  public static final String TEST_PROCESS = "Test Process";
  public static final String SELECTOR_ID = "selectorId";
  public static final String SELECTOR_123 = "selector123";
  public static final String PARAMETERS = "parameters";
  public static final String SELECTOR_PARAM_ID = "selectorParamId";
  public static final String PARAM1_COLUMN = "param1Column";
  public static final String PARAM2_COLUMN = "param2Column";
  public static final String CONVERTER = "converter";
  public static final String COULD_NOT_SET_CONVERTER_FIELD = "Could not set converter field: ";
  public static final String ON_LOAD = "onLoad";
  public static final String ON_PROCESS = "onProcess";
  public static final String FIELD1 = "field1";
  public static final String FILTER = "filter";
  public static final String DISPLAY_LOGIC = "displayLogic";
  public static final String ENTITY_NAME = "entityName";
  public static final String PARENT_COLUMNS = "parentColumns";
  public static final String FIELDS = "fields";
  public static final String TAB_ID_HYPHEN = "tab-id";
  public static final String TEST_CONTEXT = "#testContext";
  public static final String TEST_EXCEPTION = "Test exception";
  public static final String PARENT_TAB_ID_CAMEL = "parentTabId";
  public static final String SALES_INVOICE_HEADER_EDITION_HTML = "/SalesInvoice/Header_Edition.html";
  public static final String JWT_TOKEN_HASH = "#JWT_TOKEN";
  public static final String VALUE2 = "Value2";
  public static final String CUSTOM_VALUE = "Custom-Value";
  public static final String VALUE1 = "Value1";
  public static final String MULTI_HEADER = "Multi-Header";
  public static final String SHOULD_HAVE_NO_MORE_VALUES = "Should have no more values";
  public static final String ORIGINAL_HEADER = "Original-Header";
  public static final String LAST_MODIFIED = "Last-Modified";
  public static final String APPLICATION_JSON_CONSTANT = "application/json";
  public static final String CONTENT_TYPE_CONSTANT = "Content-Type";
  public static final String WORLD = "World";
  public static final String ISC_DATA_FORMAT = "isc_dataFormat";
  public static final String TEST_PRIVATE_KEY = "test-private-key";
  public static final String MANAGER = "manager";
  public static final String TEST_ATTRIBUTE = "testAttribute";
  public static final String TEST_VALUE = "testValue";
  public static final String VALUE1_LOWER = "value1";
  public static final String ATTR1 = "attr1";
  public static final String ATTR2 = "attr2";
  public static final String ATTR3 = "attr3";
  public static final String VALUE = "value";
  public static final String OBJECT = "object";
  public static final String JSON_ADDRESS_SAMPLE = "{\"address1\":\"123 Main St\",\"city\":\"Springfield\",\"countryId\":\"US\"}";
  public static final String CREATE = "create";
  public static final String ADDRESS_123_MAIN_STREET = "123 Main Street";
  public static final String ADDRESS1 = "address1";
  public static final String ZIP_CODE_12345 = "12345";
  public static final String SPRINGFIELD = "Springfield";
  public static final String APT_1 = "Apt 1";
  public static final String ADDRESS_123_MAIN_ST = "123 Main St";
  public static final String TEST_ID = "test-id";
  public static final String MAIN_ST = "Main St";
  public static final String IDENTIFIER = "_identifier";
  public static final String REGION_ID = "regionId";
  public static final String COUNTRY_ID = "countryId";
  public static final String TEST_LOCATION = "test-location-id";
  public static final String REGION_ID_HYPHEN = "region-id";
  public static final String COUNTRY_ID_HYPHEN = "country-id";
  public static final String APT_4B = "Apt 4B";
  public static final String MESSAGE = "message";
  public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String ORIGIN = "Origin";
  public static final String HTTP_LOCALHOST_8080 = "http://localhost:8080";
  public static final String TEST_PATH_WILDCARD = "/test/*";
  public static final String TEST_PATH = "/test";
  public static final String FALSE = "false";
  public static final String INVALID_SCRIPT = "invalid script";
  public static final String TEST_IO_EXCEPTION = "Test IO Exception";
  public static final String LANGUAGE = "language";
  public static final String TEST_ERROR_MESSAGE = "Test error message";
  public static final String ERROR = "error";

  public static final String WINDOW_ID = "test-window-id";
  public static final String ROLE_NAME = "Test Role";

  public static final String PARAMETER_ID = "test-parameter-id";
  public static final String READONLY_LOGIC = "@SQL=SELECT 1 FROM DUAL";
  public static final String JS_EXPRESSION = "1==1";

  public static final String TAB_ID = "test-tab-id";
  public static final String PARENT_TAB_ID = "parent-tab-id";
  public static final String TABLE_NAME = "TestTable";
  public static final String ENTITY_COLUMN_NAME = "testColumn";

  public static final String ENTITY_PROVIDER = "entityProvider";
  public static final String TEST_TAB_ID = "testTabId";
  public static final String TEST_COLUMN_NAME = "testColumn";
  public static final String TEST_TABLE_NAME = "testTable";
  public static final String TEST_PROPERTY_NAME = "testProperty";
  public static final String TEST_WINDOW_ID = "testWindowId";
  public static final Date TEST_DATE = new Date();

  public static final String TEST_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.token";
  public static final String TEST_SESSION_ID = "test-session-id";
  public static final String TEST_USER_ID = "test-user-id";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String BEARER_PREFIX = "Bearer ";
  public static final String API_DATA_PATH = "/api/data";

  public static final String TEST_CONTENT = "Test content for response";
  public static final String UTF8_ENCODING = "UTF-8";
  public static final String ISO_ENCODING = "ISO-8859-1";
  public static final int DEFAULT_BUFFER_SIZE = 1024;

  public static final String TEST_MESSAGE = "Test message with {} and {}";
  public static final String TEST_PARAM_1 = "param1";
  public static final String TEST_PARAM_2 = "param2";
  public static final String EXPECTED_FORMATTED_MESSAGE = "Test message with param1 and param2";
  public static final String TABLE_ID = "test-table-id";
  public static final String COLUMN_ID = "test-column-id";
  public static final String PROCESS_ID = "test-process-id";
  public static final String LANGUAGE_CODE = "en_US";

  public static final String LIST_ID = "list-id";
  public static final String ENGLISH_USA = "English (USA)";
  public static final String LANGUAGE1_ID = "language1-id";
  public static final String ES_ES = "es_ES";
  public static final String LANGUAGE2_ID = "language2-id";
  public static final String SPANISH_SPAIN = "Spanish (Spain)";
  public static final String LANGUAGE_CONST = "language";
  public static final String SINGLE_LANGUAGE_ID = "single-language-id";

  public static final String CURRENT_ROLE = "currentRole";
  public static final String CURRENT_CLIENT = "currentClient";
  public static final String CURRENT_ORGANIZATION = "currentOrganization";
  public static final String CURRENT_WAREHOUSE = "currentWarehouse";
  public static final String ROLES = "roles";
  public static final String DATABASE_ERROR = "Database error";
  public static final String WAREHOUSES = "warehouses";
  public static final String TOOLBAR1_ID = "toolbar1-id";
  public static final String TEST_TOOLBAR_1 = "Test Toolbar 1";
  public static final String RESPONSE = "response";
  public static final String START_ROW = "startRow";
  public static final String END_ROW = "endRow";
  public static final String TOTAL_ROWS = "totalRows";
  public static final String STATUS = "status";
  public static final String CLIENT = "client";
  public static final String ORGANIZATION = "organization";
  public static final String ORGANIZATIONS = "organizations";
  public static final String ACTIVE = "active";
  public static final String SEQNO = "seqno";
  public static final String DESCRIPTION = "description";
  public static final String ETMETA_ACTION_HANDLER = "etmetaActionHandler";
  public static final String NAME_KEY = "nameKey";
  public static final String ACTION = "action";
  public static final String BUTTON_TYPE = "buttonType";
  public static final String SECTION = "section";
  public static final String MODULE = "module";

  public static final String WELD_CONTAINER_NOT_INITIALIZED_ERROR = "IllegalStateException due to Weld container not being initialized is expected in unit tests";
  public static final String SINGLETON_NOT_SET_ERROR = "Singleton not set for STATIC_INSTANCE";
  public static final String TAB_PATH = "/tab/";
  public static final String LEGACY_REQUEST_FAILED = "Failed to process legacy request";
  public static final String ENTITY_NAME_PROPERTY = "entityName";
  public static final String IDENTIFIER_PROPERTY = "identifier";

  /**
   * Private constructor to prevent instantiation of this utility class.
   * This class is not meant to be instantiated, as it only contains static constants.
   */
  private MetadataTestConstants() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}

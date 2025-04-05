package com.etendoerp.metadata;

import java.util.Arrays;
import java.util.List;

/**
 * @author luuchorocha
 */
public class Constants {
    public static final String HTTP_METHOD_GET = "GET";
    public static final String HTTP_METHOD_POST = "POST";
    public static final String HTTP_METHOD_DELETE = "DELETE";
    public static final String SERVLET_DO_POST_METHOD = "doPost";
    public static final String SERVLET_DO_GET_METHOD = "doGet";
    public static final String SERVLET_DO_DELETE_METHOD = "doDelete";
    public static final String TOOLBAR_PATH = "/toolbar";
    public static final String SESSION_PATH = "/session";
    public static final String MENU_PATH = "/menu";
    public static final String WINDOW_PATH = "/window/";
    public static final String TAB_PATH = "/tab/";
    public static final String LANGUAGE_PATH = "/language";
    public static final String MESSAGE_PATH = "/message";
    public static final String DELEGATED_SERVLET_PATH = "/servlets";
    public static final boolean DEFAULT_CHECK_ON_SAVE = true;
    public static final boolean DEFAULT_EDITABLE_FIELD = true;
    public static final String LIST_REFERENCE_ID = "17";
    public static final String CUSTOM_QUERY_DS = "F8DD408F2F3A414188668836F84C21AF";
    public static final String TABLE_DATASOURCE = "ComboTableDatasourceService";
    public static final String TREE_DATASOURCE = "90034CAE96E847D78FBEF6D38CB1930D";
    public static final String DATASOURCE_PROPERTY = "datasourceName";
    public static final String SELECTOR_DEFINITION_PROPERTY = "_selectorDefinitionId";
    public static final String FIELD_ID_PROPERTY = "fieldId";
    public static final String DISPLAY_FIELD_PROPERTY = "displayField";
    public static final String VALUE_FIELD_PROPERTY = "valueField";
    private static final String SELECTOR_REFERENCE_ID = "95E2A8B50A254B2AAE6774B8C2F28120";
    private static final String SEARCH_REFERENCE_ID = "30";
    private static final String TABLE_DIR_REFERENCE_ID = "19";
    private static final String TABLE_REFERENCE_ID = "18";
    private static final String TREE_REFERENCE_ID = "8C57A4A2E05F4261A1FADF47C30398AD";
    public static final List<String> SELECTOR_REFERENCES = Arrays.asList(TABLE_REFERENCE_ID,
                                                                         TABLE_DIR_REFERENCE_ID,
                                                                         SEARCH_REFERENCE_ID,
                                                                         SELECTOR_REFERENCE_ID,
                                                                         TREE_REFERENCE_ID);
}

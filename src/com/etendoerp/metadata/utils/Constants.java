/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.utils;

import java.util.Arrays;
import java.util.List;

/**
 * @author luuchorocha
 */
public class Constants {
    public static final String MODULE_BASE_PATH = "/meta";
    public static final String SERVLET_PATH = "/forward";
    public static final String LEGACY_PATH = "/legacy";
    public static final String SERVLET_FULL_PATH = MODULE_BASE_PATH.concat(SERVLET_PATH);
    public static final String SESSION_PATH = "/session";
    public static final String MENU_PATH = "/menu";
    public static final String WINDOW_PATH = "/window/";
    public static final String TAB_PATH = "/tab/";
    public static final String LANGUAGE_PATH = "/language";
    public static final String MESSAGE_PATH = "/message";
    public static final String LABELS_PATH = "/labels";
    public static final boolean DEFAULT_CHECKON_SAVE = true;
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
    public static final String TAB_ID = "tabId";
    public static final String SWS_SWS_ARE_MISCONFIGURED = "SWS - SWS are misconfigured";
    public static final String SWS_INVALID_CREDENTIALS = "SWS - You must specify a username and password or a valid token";
    public static final int SERVLET_PATH_LENGTH = SERVLET_PATH.length();
    public static final String INPUT_NAME_PREFIX = "inp";
    private static final String SELECTOR_REFERENCE_ID = "95E2A8B50A254B2AAE6774B8C2F28120";
    public static final String BUTTON_REFERENCE_ID = "28";
    private static final String SEARCH_REFERENCE_ID = "30";
    private static final String TABLE_DIR_REFERENCE_ID = "19";
    private static final String TABLE_REFERENCE_ID = "18";
    private static final String TREE_REFERENCE_ID = "8C57A4A2E05F4261A1FADF47C30398AD";
    public static final List<String> SELECTOR_REFERENCES = Arrays.asList(TABLE_REFERENCE_ID, TABLE_DIR_REFERENCE_ID,
        SEARCH_REFERENCE_ID, SELECTOR_REFERENCE_ID, TREE_REFERENCE_ID);
    public static final String WINDOW_REFERENCE_ID = "FF80818132D8F0F30132D9BC395D0038";
    public static final String FORM_CLOSE_TAG = "</FORM>";
    public static final String FRAMESET_CLOSE_TAG = "</FRAMESET>";
    public static final String HEAD_CLOSE_TAG = "</HEAD>";
    public static final String OPTIONS = "OPTIONS";
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String PATCH = "PATCH";
    public static final String DELETE = "DELETE";
    public static final String LOCATION_PATH = "/location/";
    public static final String TOOLBAR_PATH = "/toolbar";
    public static final String REPORT_AND_PROCESS_PATH = "/report-and-process/";
    public static final String LOCALE_KEY = "Locale";
    public static final String DEFAULT_LOCALE = "en_US";
    public static final String PUBLIC_JS_PATH = "/web/js/";
}

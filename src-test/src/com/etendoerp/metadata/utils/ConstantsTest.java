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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for Constants utility class.
 * Verifies that all constants are properly defined and have expected values.
 */
public class ConstantsTest {

    /**
     * Tests all path-related constants for correct values.
     */
    @Test
    public void testPathConstants() {
        assertEquals("/meta", Constants.MODULE_BASE_PATH);
        assertEquals("/forward", Constants.SERVLET_PATH);
        assertEquals("/legacy", Constants.LEGACY_PATH);
        assertEquals("/meta/forward", Constants.SERVLET_FULL_PATH);
        assertEquals("/session", Constants.SESSION_PATH);
        assertEquals("/menu", Constants.MENU_PATH);
        assertEquals("/window/", Constants.WINDOW_PATH);
        assertEquals("/tab/", Constants.TAB_PATH);
        assertEquals("/language", Constants.LANGUAGE_PATH);
        assertEquals("/message", Constants.MESSAGE_PATH);
        assertEquals("/labels", Constants.LABELS_PATH);
        assertEquals("/location/", Constants.LOCATION_PATH);
        assertEquals("/toolbar", Constants.TOOLBAR_PATH);
    }

    /**
     * Tests default boolean and string values.
     */
    @Test
    public void testDefaultValues() {
        assertTrue("Default check on save should be true", Constants.DEFAULT_CHECKON_SAVE);
        assertTrue("Default editable field should be true", Constants.DEFAULT_EDITABLE_FIELD);
        assertEquals("Default locale should be en_US", "en_US", Constants.DEFAULT_LOCALE);
        assertEquals("Locale key should be Locale", "Locale", Constants.LOCALE_KEY);
    }

    /**
     * Tests reference ID constants for correct values.
     */
    @Test
    public void testReferenceIds() {
        assertEquals("List reference ID", "17", Constants.LIST_REFERENCE_ID);
        assertEquals("Custom query DS", "F8DD408F2F3A414188668836F84C21AF", Constants.CUSTOM_QUERY_DS);
        assertEquals("Table datasource", "ComboTableDatasourceService", Constants.TABLE_DATASOURCE);
        assertEquals("Tree datasource", "90034CAE96E847D78FBEF6D38CB1930D", Constants.TREE_DATASOURCE);
        assertEquals("Window reference ID", "FF80818132D8F0F30132D9BC395D0038", Constants.WINDOW_REFERENCE_ID);
    }

    /**
     * Tests property name constants for correct values.
     */
    @Test
    public void testPropertyNames() {
        assertEquals("Datasource property", "datasourceName", Constants.DATASOURCE_PROPERTY);
        assertEquals("Selector definition property", "_selectorDefinitionId", Constants.SELECTOR_DEFINITION_PROPERTY);
        assertEquals("Field ID property", "fieldId", Constants.FIELD_ID_PROPERTY);
        assertEquals("Display field property", "displayField", Constants.DISPLAY_FIELD_PROPERTY);
        assertEquals("Value field property", "valueField", Constants.VALUE_FIELD_PROPERTY);
        assertEquals("Tab ID", "tabId", Constants.TAB_ID);
        assertEquals("Input name prefix", "inp", Constants.INPUT_NAME_PREFIX);
    }

    /**
     * Tests HTTP method constants for correct values.
     */
    @Test
    public void testHttpMethods() {
        assertEquals("OPTIONS method", "OPTIONS", Constants.OPTIONS);
        assertEquals("GET method", "GET", Constants.GET);
        assertEquals("POST method", "POST", Constants.POST);
        assertEquals("PUT method", "PUT", Constants.PUT);
        assertEquals("PATCH method", "PATCH", Constants.PATCH);
        assertEquals("DELETE method", "DELETE", Constants.DELETE);
    }

    /**
     * Tests HTML tag constants for correct values.
     */
    @Test
    public void testHtmlTags() {
        assertEquals("Form close tag", "</FORM>", Constants.FORM_CLOSE_TAG);
        assertEquals("Frameset close tag", "</FRAMESET>", Constants.FRAMESET_CLOSE_TAG);
        assertEquals("Head close tag", "</HEAD>", Constants.HEAD_CLOSE_TAG);
    }

    /**
     * Tests selector reference list for correct size and content.
     */
    @Test
    public void testSelectorReferences() {
        assertNotNull("Selector references should not be null", Constants.SELECTOR_REFERENCES);
        assertEquals("Should have 5 selector references", 5, Constants.SELECTOR_REFERENCES.size());
        assertTrue("Should contain table reference", Constants.SELECTOR_REFERENCES.contains("18"));
        assertTrue("Should contain table dir reference", Constants.SELECTOR_REFERENCES.contains("19"));
        assertTrue("Should contain search reference", Constants.SELECTOR_REFERENCES.contains("30"));
        assertTrue("Should contain selector reference", Constants.SELECTOR_REFERENCES.contains("95E2A8B50A254B2AAE6774B8C2F28120"));
        assertTrue("Should contain tree reference", Constants.SELECTOR_REFERENCES.contains("8C57A4A2E05F4261A1FADF47C30398AD"));
    }

    /**
     * Tests that servlet path length constant matches actual path length.
     */
    @Test
    public void testServletPathLength() {
        assertEquals("Servlet path length should match actual length", 
                     Constants.SERVLET_PATH.length(), Constants.SERVLET_PATH_LENGTH);
    }

    /**
     * Tests error message constants for correct values.
     */
    @Test
    public void testErrorMessages() {
        assertEquals("SWS misconfigured message", "SWS - SWS are misconfigured", Constants.SWS_SWS_ARE_MISCONFIGURED);
        assertEquals("SWS invalid credentials message", "SWS - You must specify a username and password or a valid token", Constants.SWS_INVALID_CREDENTIALS);
    }
}
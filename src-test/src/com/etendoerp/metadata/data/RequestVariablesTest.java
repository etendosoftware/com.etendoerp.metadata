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

package com.etendoerp.metadata.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test class for RequestVariables.
 */
@RunWith(MockitoJUnitRunner.class)
public class RequestVariablesTest {

    @Mock
    private HttpServletRequest mockRequest;

    private RequestVariables requestVariables;

    @Before
    public void setUp() {
        requestVariables = new RequestVariables(mockRequest);
    }

    /**
     * Tests RequestVariables constructor and initial state.
     */
    @Test
    public void testConstructor() {
        assertNotNull("RequestVariables should not be null", requestVariables);
        assertNotNull("Cased session attributes should not be null", requestVariables.getCasedSessionAttributes());
        assertTrue("Cased session attributes should be empty initially", requestVariables.getCasedSessionAttributes().isEmpty());
    }

    /**
     * Tests setSessionValue method stores values correctly.
     */
    @Test
    public void testSetSessionValue() {
        String attribute = "testAttribute";
        String value = "testValue";
        
        requestVariables.setSessionValue(attribute, value);
        
        Map<String, Object> casedAttributes = requestVariables.getCasedSessionAttributes();
        assertTrue("Cased attributes should contain the set attribute", casedAttributes.containsKey(attribute));
        assertEquals("Cased attribute value should match", value, casedAttributes.get(attribute));
    }

    /**
     * Tests setSessionValue method with multiple values.
     */
    @Test
    public void testSetMultipleSessionValues() {
        requestVariables.setSessionValue("attr1", "value1");
        requestVariables.setSessionValue("attr2", "value2");
        requestVariables.setSessionValue("attr3", "value3");
        
        Map<String, Object> casedAttributes = requestVariables.getCasedSessionAttributes();
        assertEquals("Should have 3 attributes", 3, casedAttributes.size());
        assertEquals("First attribute should match", "value1", casedAttributes.get("attr1"));
        assertEquals("Second attribute should match", "value2", casedAttributes.get("attr2"));
        assertEquals("Third attribute should match", "value3", casedAttributes.get("attr3"));
    }

    /**
     * Tests setSessionValue method with null value.
     */
    @Test
    public void testSetSessionValueWithNullValue() {
        String attribute = "testAttribute";
        
        requestVariables.setSessionValue(attribute, null);
        
        Map<String, Object> casedAttributes = requestVariables.getCasedSessionAttributes();
        assertTrue("Cased attributes should contain the attribute", casedAttributes.containsKey(attribute));
        assertNull("Cased attribute value should be null", casedAttributes.get(attribute));
    }

    /**
     * Tests setSessionValue method overwrites existing values.
     */
    @Test
    public void testSetSessionValueOverwrite() {
        String attribute = "testAttribute";
        String value1 = "value1";
        String value2 = "value2";
        
        requestVariables.setSessionValue(attribute, value1);
        requestVariables.setSessionValue(attribute, value2);
        
        Map<String, Object> casedAttributes = requestVariables.getCasedSessionAttributes();
        assertEquals("Should have 1 attribute", 1, casedAttributes.size());
        assertEquals("Attribute should have the latest value", value2, casedAttributes.get(attribute));
    }

    /**
     * Tests getCasedSessionAttributes returns a Map instance.
     */
    @Test
    public void testGetCasedSessionAttributesReturnsMap() {
        Map<String, Object> casedAttributes = requestVariables.getCasedSessionAttributes();
        
        assertNotNull("Cased session attributes should not be null", casedAttributes);
        assertTrue("Should be a Map", casedAttributes instanceof Map);
    }

    /**
     * Tests setSessionValue method with empty string value.
     */
    @Test
    public void testSetSessionValueWithEmptyString() {
        String attribute = "testAttribute";
        String value = "";
        
        requestVariables.setSessionValue(attribute, value);
        
        Map<String, Object> casedAttributes = requestVariables.getCasedSessionAttributes();
        assertTrue("Cased attributes should contain the attribute", casedAttributes.containsKey(attribute));
        assertEquals("Cased attribute value should be empty string", value, casedAttributes.get(attribute));
    }
}
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

package com.etendoerp.metadata.http.session;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test class for SessionAttributeStore.
 * Uses unique session IDs to avoid state conflicts between tests.
 */
public class SessionAttributeStoreTest {
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";
    private static final String ATTR_1 = "attr1";
    private static final String ATTR_2 = "attr2";
    private static final String REMOVE = "remove";

    /**
     * Tests setAttribute and getAttribute methods work correctly.
     */
    @Test
    public void testSetAndGetAttribute() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "test-session-" + System.nanoTime();
        String attributeName = "testAttribute";
        String attributeValue = "testValue";
        
        store.setAttribute(sessionId, attributeName, attributeValue);
        Object retrievedValue = store.getAttribute(sessionId, attributeName);
        
        assertEquals("Retrieved value should match set value", attributeValue, retrievedValue);
    }

    /**
     * Tests getAttributes returns empty map for new session.
     */
    @Test
    public void testGetAttributesReturnsEmptyMapForNewSession() {
        SessionAttributeStore store = new SessionAttributeStore();
        String newSessionId = "new-session-" + System.nanoTime();
        
        Map<String, Object> attributes = store.getAttributes(newSessionId);
        
        assertNotNull("Attributes map should not be null", attributes);
        assertTrue("Attributes map should be empty for new session", attributes.isEmpty());
    }

    /**
     * Tests getAttributes returns correct map with all stored attributes.
     */
    @Test
    public void testGetAttributesReturnsCorrectMap() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "unique-session-" + System.nanoTime();
        
        store.setAttribute(sessionId, ATTR_1, VALUE_1);
        store.setAttribute(sessionId, ATTR_2, VALUE_2);
        
        Map<String, Object> attributes = store.getAttributes(sessionId);
        
        assertNotNull("Attributes map should not be null", attributes);
        assertEquals("Should have 2 attributes", 2, attributes.size());
        assertEquals("First attribute should match", VALUE_1, attributes.get(ATTR_1));
        assertEquals("Second attribute should match", VALUE_2, attributes.get(ATTR_2));
    }

    /**
     * Tests removeAttribute method removes specific attribute only.
     */
    @Test
    public void testRemoveAttribute() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "remove-test-" + System.nanoTime();
        
        store.setAttribute(sessionId, "keep", "keepValue");
        store.setAttribute(sessionId, REMOVE, "removeValue");
        
        store.removeAttribute(sessionId, REMOVE);
        
        assertNull("Removed attribute should be null", store.getAttribute(sessionId, REMOVE));
        assertEquals("Other attribute should still exist", "keepValue", store.getAttribute(sessionId, "keep"));
    }

    /**
     * Tests removeAllAttributes method clears all session attributes.
     */
    @Test
    public void testRemoveAllAttributes() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "remove-all-" + System.nanoTime();
        
        store.setAttribute(sessionId, ATTR_1, VALUE_1);
        store.setAttribute(sessionId, ATTR_2, VALUE_2);
        
        store.removeAllAttributes(sessionId);
        
        Map<String, Object> attributes = store.getAttributes(sessionId);
        assertTrue("All attributes should be removed", attributes.isEmpty());
    }

    /**
     * Tests multiple sessions maintain separate attribute stores.
     */
    @Test
    public void testMultipleSessions() {
        SessionAttributeStore store = new SessionAttributeStore();
        String session1 = "session1-" + System.nanoTime();
        String session2 = "session2-" + System.nanoTime();
        
        store.setAttribute(session1, "attr", VALUE_1);
        store.setAttribute(session2, "attr", VALUE_2);
        
        assertEquals("Session 1 attribute should be correct", VALUE_1, store.getAttribute(session1, "attr"));
        assertEquals("Session 2 attribute should be correct", VALUE_2, store.getAttribute(session2, "attr"));
    }

    /**
     * Tests getAttribute returns null for non-existent attributes.
     */
    @Test
    public void testGetNonExistentAttribute() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "nonexistent-test-" + System.nanoTime();
        
        Object value = store.getAttribute(sessionId, "nonexistent");
        
        assertNull("Non-existent attribute should be null", value);
    }

    /**
     * Tests removeAttribute handles non-existent attributes gracefully.
     */
    @Test
    public void testRemoveNonExistentAttribute() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "clean-session-" + System.nanoTime();
        
        // Should not throw exception
        store.removeAttribute(sessionId, "nonexistent");
        
        // Verify no side effects
        Map<String, Object> attributes = store.getAttributes(sessionId);
        assertTrue("Attributes should still be empty", attributes.isEmpty());
    }
}
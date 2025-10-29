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

    @Test
    public void testGetAttributesReturnsEmptyMapForNewSession() {
        SessionAttributeStore store = new SessionAttributeStore();
        String newSessionId = "new-session-" + System.nanoTime();
        
        Map<String, Object> attributes = store.getAttributes(newSessionId);
        
        assertNotNull("Attributes map should not be null", attributes);
        assertTrue("Attributes map should be empty for new session", attributes.isEmpty());
    }

    @Test
    public void testGetAttributesReturnsCorrectMap() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "unique-session-" + System.nanoTime();
        
        store.setAttribute(sessionId, "attr1", "value1");
        store.setAttribute(sessionId, "attr2", "value2");
        
        Map<String, Object> attributes = store.getAttributes(sessionId);
        
        assertNotNull("Attributes map should not be null", attributes);
        assertEquals("Should have 2 attributes", 2, attributes.size());
        assertEquals("First attribute should match", "value1", attributes.get("attr1"));
        assertEquals("Second attribute should match", "value2", attributes.get("attr2"));
    }

    @Test
    public void testRemoveAttribute() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "remove-test-" + System.nanoTime();
        
        store.setAttribute(sessionId, "keep", "keepValue");
        store.setAttribute(sessionId, "remove", "removeValue");
        
        store.removeAttribute(sessionId, "remove");
        
        assertNull("Removed attribute should be null", store.getAttribute(sessionId, "remove"));
        assertEquals("Other attribute should still exist", "keepValue", store.getAttribute(sessionId, "keep"));
    }

    @Test
    public void testRemoveAllAttributes() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "remove-all-" + System.nanoTime();
        
        store.setAttribute(sessionId, "attr1", "value1");
        store.setAttribute(sessionId, "attr2", "value2");
        
        store.removeAllAttributes(sessionId);
        
        Map<String, Object> attributes = store.getAttributes(sessionId);
        assertTrue("All attributes should be removed", attributes.isEmpty());
    }

    @Test
    public void testMultipleSessions() {
        SessionAttributeStore store = new SessionAttributeStore();
        String session1 = "session1-" + System.nanoTime();
        String session2 = "session2-" + System.nanoTime();
        
        store.setAttribute(session1, "attr", "value1");
        store.setAttribute(session2, "attr", "value2");
        
        assertEquals("Session 1 attribute should be correct", "value1", store.getAttribute(session1, "attr"));
        assertEquals("Session 2 attribute should be correct", "value2", store.getAttribute(session2, "attr"));
    }

    @Test
    public void testGetNonExistentAttribute() {
        SessionAttributeStore store = new SessionAttributeStore();
        String sessionId = "nonexistent-test-" + System.nanoTime();
        
        Object value = store.getAttribute(sessionId, "nonexistent");
        
        assertNull("Non-existent attribute should be null", value);
    }

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
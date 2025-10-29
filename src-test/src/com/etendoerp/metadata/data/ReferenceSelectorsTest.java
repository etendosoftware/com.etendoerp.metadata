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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.userinterface.selector.Selector;

import static org.junit.Assert.*;

/**
 * Test class for ReferenceSelectors data class.
 */
@RunWith(MockitoJUnitRunner.class)
public class ReferenceSelectorsTest {

    @Mock
    private Selector mockSelector;

    @Mock
    private ReferencedTree mockTreeSelector;

    @Test
    public void testConstructor() {
        ReferenceSelectors referenceSelectors = new ReferenceSelectors(mockSelector, mockTreeSelector);
        
        assertNotNull("ReferenceSelectors should not be null", referenceSelectors);
        assertEquals("Selector should match", mockSelector, referenceSelectors.selector);
        assertEquals("Tree selector should match", mockTreeSelector, referenceSelectors.treeSelector);
    }

    @Test
    public void testConstructorWithNullValues() {
        ReferenceSelectors referenceSelectors = new ReferenceSelectors(null, null);
        
        assertNotNull("ReferenceSelectors should not be null", referenceSelectors);
        assertNull("Selector should be null", referenceSelectors.selector);
        assertNull("Tree selector should be null", referenceSelectors.treeSelector);
    }

    @Test
    public void testConstructorWithMixedNullValues() {
        // Test with null selector, non-null tree selector
        ReferenceSelectors referenceSelectors1 = new ReferenceSelectors(null, mockTreeSelector);
        assertNull("Selector should be null", referenceSelectors1.selector);
        assertEquals("Tree selector should match", mockTreeSelector, referenceSelectors1.treeSelector);

        // Test with non-null selector, null tree selector
        ReferenceSelectors referenceSelectors2 = new ReferenceSelectors(mockSelector, null);
        assertEquals("Selector should match", mockSelector, referenceSelectors2.selector);
        assertNull("Tree selector should be null", referenceSelectors2.treeSelector);
    }

    @Test
    public void testFieldsArePublicAndFinal() {
        ReferenceSelectors referenceSelectors = new ReferenceSelectors(mockSelector, mockTreeSelector);
        
        // Verify fields are accessible (public)
        Selector selector = referenceSelectors.selector;
        ReferencedTree treeSelector = referenceSelectors.treeSelector;
        
        assertEquals("Selector should be accessible", mockSelector, selector);
        assertEquals("Tree selector should be accessible", mockTreeSelector, treeSelector);
    }
}
package com.etendoerp.metadata.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for {@link CallAsyncProcess}.
 */
public class CallAsyncProcessTest extends OBBaseTest {

    /**
     * Tests that getInstance returns the same instance.
     */
    @Test
    public void testGetInstance() {
        CallAsyncProcess instance1 = CallAsyncProcess.getInstance();
        CallAsyncProcess instance2 = CallAsyncProcess.getInstance();
        
        assertNotNull("Instance should not be null", instance1);
        assertSame("Should return the same instance", instance1, instance2);
    }
}

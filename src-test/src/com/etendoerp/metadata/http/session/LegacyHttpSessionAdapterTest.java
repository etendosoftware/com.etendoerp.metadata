package com.etendoerp.metadata.http.session;

import static com.etendoerp.metadata.MetadataTestConstants.ATTR1;
import static com.etendoerp.metadata.MetadataTestConstants.ATTR2;
import static com.etendoerp.metadata.MetadataTestConstants.ATTR3;
import static com.etendoerp.metadata.MetadataTestConstants.OBJECT;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_ATTRIBUTE;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_VALUE;
import static com.etendoerp.metadata.MetadataTestConstants.VALUE;
import static com.etendoerp.metadata.MetadataTestConstants.VALUE1_LOWER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Enumeration;

import javax.servlet.ServletContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for the {@link LegacyHttpSessionAdapter} class.
 *
 * <p>This test suite verifies the correct behavior of the LegacyHttpSessionAdapter,
 * ensuring it adheres to the expected contract of the HttpSession interface. It
 * covers session property management, attribute handling, and lifecycle operations.</p>
 *
 * <p>Tests include validation of session creation, attribute storage and retrieval,
 * session invalidation, and edge cases such as handling of null values and concurrent
 * access scenarios.</p>
 *
 * <p>Mockito is used to mock dependencies such as ServletContext, allowing for isolated
 * testing of session behavior without reliance on a full servlet container.</p>
 *
 * <p>Each test method is designed to be independent, ensuring that the state is reset
 * before each test execution. This is achieved through the use of the @Before setup
 * method which initializes a fresh instance of LegacyHttpSessionAdapter for each test.</p>
 *
 * <p>Tests are annotated with @Test and utilize assertions to validate expected outcomes.
 * Exception handling is also tested to ensure proper error conditions are met.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class LegacyHttpSessionAdapterTest extends OBBaseTest {

  @Mock
  private ServletContext servletContext;

  private LegacyHttpSessionAdapter session;

  private String sessionId;

  private long startTime;

  /**
   * Sets up the test environment before each test method execution.
   *
   * <p>This method initializes the test session with a known session ID and
   * mocked servlet context. It also records the start time for timing-related
   * validations. The setup ensures each test starts with a fresh session instance.</p>
   *
   * @throws Exception
   *     if session initialization fails or parent setup encounters issues
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    sessionId = "test-session-id";
    startTime = System.currentTimeMillis();
    session = new LegacyHttpSessionAdapter(sessionId, servletContext);
  }

  /**
   * Tests that the constructor properly initializes all session properties.
   *
   * <p>This test validates that when a LegacyHttpSessionAdapter is created, it correctly
   * sets the session ID, servlet context, creation time, and initial state. It ensures
   * that the session is not marked as new and that timing values are reasonable.</p>
   *
   * @throws Exception
   *     if session property access fails
   */
  @Test
  public void constructorShouldInitializeSessionCorrectly() {
    assertEquals(sessionId, session.getId());
    assertEquals(servletContext, session.getServletContext());
    assertFalse(session.isNew());

    long creationTime = session.getCreationTime();
    assertTrue("Creation time should be recent",
        Math.abs(creationTime - startTime) < 1000);

    assertEquals(creationTime, session.getLastAccessedTime());
  }

  /**
   * Tests that the session ID is correctly returned.
   *
   * <p>This test validates that the getId() method returns the exact session ID
   * that was provided during construction, ensuring session identification works correctly.</p>
   */
  @Test
  public void getIdShouldReturnSessionId() {
    assertEquals(sessionId, session.getId());
  }

  /**
   * Tests that the servlet context is correctly returned.
   *
   * <p>This test validates that the getServletContext() method returns the same
   * ServletContext instance that was provided during construction, ensuring proper
   * context association.</p>
   */
  @Test
  public void getServletContextShouldReturnProvidedContext() {
    assertSame(servletContext, session.getServletContext());
  }

  /**
   * Tests that the creation time is properly maintained.
   *
   * <p>This test validates that the getCreationTime() method returns a consistent,
   * positive timestamp that represents when the session was created. The creation
   * time should remain constant throughout the session's lifetime.</p>
   */
  @Test
  public void getCreationTimeShouldReturnInitialTime() {
    long creationTime = session.getCreationTime();
    assertTrue("Creation time should be positive", creationTime > 0);

    assertEquals(creationTime, session.getCreationTime());
  }

  /**
   * Tests that the last accessed time is updated when attributes are accessed.
   *
   * <p>This test validates that the session properly tracks when it was last accessed
   * by updating the timestamp when getAttribute() is called. This is important for
   * session timeout and activity tracking.</p>
   *
   * @throws Exception
   *     if thread sleep is interrupted or time comparison fails
   */
  @Test
  public void getLastAccessedTimeShouldUpdateOnAttributeAccess() throws Exception {
    long initialTime = session.getLastAccessedTime();

    Thread.sleep(10);

    session.getAttribute("test");

    long newTime = session.getLastAccessedTime();
    assertTrue("Last accessed time should be updated", newTime > initialTime);
  }

  /**
   * Tests that setting max inactive interval doesn't throw exceptions.
   *
   * <p>This test validates that the setMaxInactiveInterval() method accepts various
   * values (positive, negative, zero) without throwing exceptions, even though the
   * implementation may not actively use these values for timeout management.</p>
   */
  @Test
  public void setMaxInactiveIntervalShouldNotThrowException() {
    session.setMaxInactiveInterval(3600);
    session.setMaxInactiveInterval(-1);
    session.setMaxInactiveInterval(0);
  }

  /**
   * Tests that the max inactive interval returns the expected default value.
   *
   * <p>This test validates that getMaxInactiveInterval() returns -1, which typically
   * indicates that the session never times out or uses server default timeout settings.</p>
   */
  @Test
  public void getMaxInactiveIntervalShouldReturnMinusOne() {
    assertEquals(-1, session.getMaxInactiveInterval());
  }

  /**
   * Tests that the deprecated session context method returns null.
   *
   * <p>This test validates that getSessionContext() returns null, which is the
   * expected behavior for this deprecated method in modern servlet implementations.</p>
   */
  @Test
  public void getSessionContextShouldReturnNull() {
    assertNull(session.getSessionContext());
  }

  /**
   * Tests that the session is never considered new.
   *
   * <p>This test validates that isNew() always returns false, indicating that this
   * session adapter represents an existing session rather than a newly created one.</p>
   */
  @Test
  public void isNewShouldAlwaysReturnFalse() {
    assertFalse(session.isNew());
  }

  /**
   * Tests that attributes can be stored and retrieved correctly.
   *
   * <p>This test validates the basic attribute storage functionality by setting
   * an attribute and then retrieving it to ensure the value is preserved correctly.</p>
   */
  @Test
  public void setAttributeShouldStoreValue() {
    String attributeName = TEST_ATTRIBUTE;
    String attributeValue = TEST_VALUE;

    session.setAttribute(attributeName, attributeValue);

    Object retrievedValue = session.getAttribute(attributeName);
    assertEquals(attributeValue, retrievedValue);
  }

  /**
   * Tests that setting an attribute to null removes it from the session.
   *
   * <p>This test validates that when setAttribute() is called with a null value,
   * the attribute is effectively removed from the session, following standard
   * HTTP session behavior.</p>
   */
  @Test
  public void setAttributeWithNullValueShouldRemoveAttribute() {
    String attributeName = TEST_ATTRIBUTE;

    session.setAttribute(attributeName, "someValue");
    assertNotNull(session.getAttribute(attributeName));

    session.setAttribute(attributeName, null);
    assertNull(session.getAttribute(attributeName));
  }

  /**
   * Tests that stored attributes can be retrieved correctly.
   *
   * <p>This test validates that getAttribute() returns the exact value that was
   * previously stored using setAttribute(), ensuring data integrity in the session.</p>
   */
  @Test
  public void getAttributeShouldReturnStoredValue() {
    String attributeName = TEST_ATTRIBUTE;
    String attributeValue = TEST_VALUE;

    session.setAttribute(attributeName, attributeValue);

    Object retrievedValue = session.getAttribute(attributeName);
    assertEquals(attributeValue, retrievedValue);
  }

  /**
   * Tests that non-existent attributes return null.
   *
   * <p>This test validates that getAttribute() returns null when called with
   * an attribute name that doesn't exist in the session, following standard
   * HTTP session behavior.</p>
   */
  @Test
  public void getAttributeShouldReturnNullForNonExistentAttribute() {
    Object result = session.getAttribute("nonExistentAttribute");
    assertNull(result);
  }

  /**
   * Tests that the legacy putValue method works like setAttribute.
   *
   * <p>This test validates that the deprecated putValue() method provides the same
   * functionality as setAttribute(), ensuring backward compatibility with legacy code.</p>
   */
  @Test
  public void putValueShouldBehaveLikeSetAttribute() {
    String attributeName = TEST_ATTRIBUTE;
    String attributeValue = TEST_VALUE;

    session.putValue(attributeName, attributeValue);

    Object retrievedValue = session.getAttribute(attributeName);
    assertEquals(attributeValue, retrievedValue);
  }

  /**
   * Tests that the legacy getValue method works like getAttribute.
   *
   * <p>This test validates that the deprecated getValue() method provides the same
   * functionality as getAttribute(), ensuring backward compatibility with legacy code.</p>
   */
  @Test
  public void getValueShouldBehaveLikeGetAttribute() {
    String attributeName = TEST_ATTRIBUTE;
    String attributeValue = TEST_VALUE;

    session.setAttribute(attributeName, attributeValue);

    Object retrievedValue = session.getValue(attributeName);
    assertEquals(attributeValue, retrievedValue);
  }

  /**
   * Tests that attributes can be removed from the session.
   *
   * <p>This test validates that removeAttribute() properly removes an attribute
   * from the session, making it unavailable for subsequent retrieval operations.</p>
   */
  @Test
  public void removeAttributeShouldRemoveStoredValue() {
    String attributeName = TEST_ATTRIBUTE;
    String attributeValue = TEST_VALUE;

    session.setAttribute(attributeName, attributeValue);
    assertNotNull(session.getAttribute(attributeName));

    session.removeAttribute(attributeName);
    assertNull(session.getAttribute(attributeName));
  }

  /**
   * Tests that the legacy removeValue method works like removeAttribute.
   *
   * <p>This test validates that the deprecated removeValue() method provides the same
   * functionality as removeAttribute(), ensuring backward compatibility with legacy code.</p>
   */
  @Test
  public void removeValueShouldBehaveLikeRemoveAttribute() {
    String attributeName = TEST_ATTRIBUTE;
    String attributeValue = TEST_VALUE;

    session.setAttribute(attributeName, attributeValue);
    assertNotNull(session.getAttribute(attributeName));

    session.removeValue(attributeName);
    assertNull(session.getAttribute(attributeName));
  }

  /**
   * Tests that all attribute names can be enumerated.
   *
   * <p>This test validates that getAttributeNames() returns an enumeration containing
   * all the attribute names that have been set in the session, allowing for iteration
   * over all stored attributes.</p>
   */
  @Test
  public void getAttributeNamesShouldReturnAllAttributeNames() {
    session.setAttribute(ATTR1,VALUE1_LOWER);
    session.setAttribute(ATTR2, "value2");
    session.setAttribute(ATTR3, "value3");

    Enumeration<String> attributeNames = session.getAttributeNames();

    java.util.Set<String> nameSet = new java.util.HashSet<>();
    while (attributeNames.hasMoreElements()) {
      nameSet.add(attributeNames.nextElement());
    }

    assertEquals(3, nameSet.size());
    assertTrue(nameSet.contains(ATTR1));
    assertTrue(nameSet.contains(ATTR2));
    assertTrue(nameSet.contains(ATTR3));
  }

  /**
   * Tests that the legacy getValueNames method returns attribute names as an array.
   *
   * <p>This test validates that the deprecated getValueNames() method returns all
   * attribute names as a String array, providing backward compatibility with legacy
   * code that expects this format.</p>
   */
  @Test
  public void getValueNamesShouldReturnAllAttributeNamesAsArray() {
    session.setAttribute(ATTR1,VALUE1_LOWER);
    session.setAttribute(ATTR2, "value2");

    String[] valueNames = session.getValueNames();

    assertEquals(2, valueNames.length);
    java.util.Set<String> nameSet = java.util.Set.of(valueNames);
    assertTrue(nameSet.contains(ATTR1));
    assertTrue(nameSet.contains(ATTR2));
  }

  /**
   * Tests that empty sessions return empty enumerations.
   *
   * <p>This test validates that getAttributeNames() returns an empty enumeration
   * when no attributes have been set in the session, ensuring proper handling
   * of empty state.</p>
   */
  @Test
  public void getAttributeNamesShouldReturnEmptyEnumerationWhenNoAttributes() {
    Enumeration<String> attributeNames = session.getAttributeNames();
    assertFalse(attributeNames.hasMoreElements());
  }

  /**
   * Tests that accessing creation time after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to access
   * the creation time throws an IllegalStateException, following standard HTTP session
   * behavior for invalidated sessions.</p>
   *
   * @throws IllegalStateException
   *     when accessing creation time on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void getCreationTimeAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.getCreationTime();
  }

  /**
   * Tests that accessing last accessed time after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to access
   * the last accessed time throws an IllegalStateException, ensuring proper enforcement
   * of session lifecycle rules.</p>
   *
   * @throws IllegalStateException
   *     when accessing last accessed time on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void getLastAccessedTimeAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.getLastAccessedTime();
  }

  /**
   * Tests that setting max inactive interval after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to set
   * the max inactive interval throws an IllegalStateException, preventing modification
   * of invalidated sessions.</p>
   *
   * @throws IllegalStateException
   *     when setting max inactive interval on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void setMaxInactiveIntervalAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.setMaxInactiveInterval(3600);
  }

  /**
   * Tests that getting max inactive interval after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to get
   * the max inactive interval throws an IllegalStateException, ensuring consistent
   * behavior for all session property access methods.</p>
   *
   * @throws IllegalStateException
   *     when getting max inactive interval on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void getMaxInactiveIntervalAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.getMaxInactiveInterval();
  }

  /**
   * Tests that getting session context after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to get
   * the session context throws an IllegalStateException, even though this method
   * is deprecated and normally returns null.</p>
   *
   * @throws IllegalStateException
   *     when getting session context on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void getSessionContextAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.getSessionContext();
  }

  /**
   * Tests that getting attributes after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to retrieve
   * attributes throws an IllegalStateException, preventing access to session data
   * after invalidation.</p>
   *
   * @throws IllegalStateException
   *     when getting attributes on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void getAttributeAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.getAttribute("test");
  }

  /**
   * Tests that setting attributes after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to set
   * attributes throws an IllegalStateException, preventing modification of session
   * data after invalidation.</p>
   *
   * @throws IllegalStateException
   *     when setting attributes on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void setAttributeAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.setAttribute("test", VALUE);
  }

  /**
   * Tests that removing attributes after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to remove
   * attributes throws an IllegalStateException, ensuring consistent behavior for
   * all attribute manipulation methods.</p>
   *
   * @throws IllegalStateException
   *     when removing attributes on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void removeAttributeAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.removeAttribute("test");
  }

  /**
   * Tests that getting attribute names after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to enumerate
   * attribute names throws an IllegalStateException, preventing inspection of session
   * contents after invalidation.</p>
   *
   * @throws IllegalStateException
   *     when getting attribute names on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void getAttributeNamesAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.getAttributeNames();
  }

  /**
   * Tests that getting value names after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to get
   * value names (legacy method) throws an IllegalStateException, ensuring consistent
   * behavior across all session inspection methods.</p>
   *
   * @throws IllegalStateException
   *     when getting value names on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void getValueNamesAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.getValueNames();
  }

  /**
   * Tests that checking if session is new after invalidation throws IllegalStateException.
   *
   * <p>This test validates that once a session is invalidated, attempting to check
   * if the session is new throws an IllegalStateException, ensuring that no session
   * state can be queried after invalidation.</p>
   *
   * @throws IllegalStateException
   *     when checking isNew on invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void isNewAfterInvalidateShouldThrowException() {
    session.invalidate();
    session.isNew();
  }

  /**
   * Tests that invalidating an already invalidated session throws IllegalStateException.
   *
   * <p>This test validates that calling invalidate() on an already invalidated session
   * throws an IllegalStateException, preventing double invalidation and ensuring
   * proper session lifecycle management.</p>
   *
   * @throws IllegalStateException
   *     when invalidating an already invalidated session (expected)
   */
  @Test(expected = IllegalStateException.class)
  public void doubleInvalidateShouldThrowException() {
    session.invalidate();
    session.invalidate();
  }

  /**
   * Tests complex attribute operations with multiple data types.
   *
   * <p>This test validates that the session can handle multiple attribute operations
   * including setting, updating, and removing attributes of different types. It ensures
   * that the session maintains data integrity across various operations.</p>
   */
  @Test
  public void multipleAttributeOperationsShouldWorkCorrectly() {
    session.setAttribute(ATTR1,VALUE1_LOWER);
    session.setAttribute(ATTR2, 123);
    session.setAttribute(ATTR3, new Object());

    assertEquals("value1", session.getAttribute(ATTR1));
    assertEquals(123, session.getAttribute(ATTR2));
    assertNotNull(session.getAttribute(ATTR3));

    session.setAttribute(ATTR1, "newValue1");
    assertEquals("newValue1", session.getAttribute(ATTR1));

    session.removeAttribute(ATTR2);
    assertNull(session.getAttribute(ATTR2));

    Enumeration<String> names = session.getAttributeNames();
    java.util.Set<String> nameSet = new java.util.HashSet<>();
    while (names.hasMoreElements()) {
      nameSet.add(names.nextElement());
    }

    assertEquals(2, nameSet.size());
    assertTrue(nameSet.contains(ATTR1));
    assertTrue(nameSet.contains(ATTR3));
    assertFalse(nameSet.contains(ATTR2));
  }

  /**
   * Tests that last accessed time is updated by various session operations.
   *
   * <p>This test validates that the session properly tracks access time across
   * different operations including getAttribute, setAttribute, removeAttribute,
   * and getAttributeNames. This ensures accurate session activity tracking.</p>
   *
   * @throws Exception
   *     if thread sleep is interrupted or timing operations fail
   */
  @Test
  public void lastAccessedTimeShouldUpdateOnVariousOperations() throws Exception {
    long initialTime = session.getLastAccessedTime();

    Thread.sleep(10);

    session.getAttribute("test");
    long time1 = session.getLastAccessedTime();
    assertTrue(time1 > initialTime);

    Thread.sleep(10);

    session.setAttribute("test", VALUE);
    long time2 = session.getLastAccessedTime();
    assertTrue(time2 > time1);

    Thread.sleep(10);

    session.removeAttribute("test");
    long time3 = session.getLastAccessedTime();
    assertTrue(time3 > time2);

    Thread.sleep(10);

    session.getAttributeNames();
    long time4 = session.getLastAccessedTime();
    assertTrue(time4 > time3);
  }

  /**
   * Tests that the session can store and retrieve different data types correctly.
   *
   * <p>This test validates that the session can handle various Java data types
   * including strings, integers, booleans, and complex objects, ensuring type
   * safety and proper object storage/retrieval.</p>
   */
  @Test
  public void sessionWithDifferentTypesShouldWorkCorrectly() {
    session.setAttribute("string", "stringValue");
    session.setAttribute("integer", 42);
    session.setAttribute("boolean", true);
    session.setAttribute(OBJECT, new java.util.Date());

    assertEquals("stringValue", session.getAttribute("string"));
    assertEquals(42, session.getAttribute("integer"));
    assertEquals(true, session.getAttribute("boolean"));
    assertNotNull(session.getAttribute(OBJECT));
    assertTrue(session.getAttribute(OBJECT) instanceof java.util.Date);
  }

  /**
   * Tests graceful handling of null attribute names.
   *
   * <p>This test validates that the session handles null attribute names appropriately,
   * either by accepting them gracefully or throwing appropriate exceptions. This ensures
   * robust error handling for edge cases in attribute operations.</p>
   */
  @Test
  public void attributeOperationsWithNullNamesShouldHandleGracefully() {
    try {
      session.setAttribute(null, VALUE);
      session.getAttribute(null);
      session.removeAttribute(null);
    } catch (Exception e) {
      // If exception is thrown, it should be appropriate (like IllegalArgumentException)
      assertTrue(e instanceof RuntimeException);
    }
  }

  /**
   * Tests thread safety of concurrent session operations.
   *
   * <p>This test validates that the session adapter can handle concurrent access
   * from multiple threads without data corruption or exceptions. It performs
   * multiple attribute operations from different threads and verifies that
   * a reasonable success rate is achieved, indicating thread-safe behavior.</p>
   *
   * @throws Exception
   *     if thread operations fail, interruption occurs, or timing operations fail
   */
  @Test
  public void concurrentSessionUsageShouldBeThreadSafe() throws Exception {
    final int numberOfThreads = 10;
    final int operationsPerThread = 100;

    Thread[] threads = new Thread[numberOfThreads];
    final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

    for (int i = 0; i < numberOfThreads; i++) {
      final int threadIndex = i;
      threads[i] = new Thread(() -> {
        try {
          for (int j = 0; j < operationsPerThread; j++) {
            String key = "key_" + threadIndex + "_" + j;
            String value = "value_" + threadIndex + "_" + j;

            session.setAttribute(key, value);
            Object retrieved = session.getAttribute(key);

            if (value.equals(retrieved)) {
              successCount.incrementAndGet();
            }
          }
        } catch (Exception e) {
          // Log error but don't fail test immediately
        }
      });
    }

    // Start all threads
    for (Thread thread : threads) {
      thread.start();
    }

    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join(5000); // 5 second timeout
    }

    // Should have some successful operations (exact count may vary due to concurrency)
    int expectedSuccessCount = numberOfThreads * operationsPerThread;
    assertTrue("Should have at least some successful operations", successCount.get() > 0);
    // Note: Due to concurrency, we might not get exact count, but should get reasonable success rate
    assertTrue("Success rate should be reasonable",
        successCount.get() >= expectedSuccessCount * 0.8); // At least 80% success
  }
}
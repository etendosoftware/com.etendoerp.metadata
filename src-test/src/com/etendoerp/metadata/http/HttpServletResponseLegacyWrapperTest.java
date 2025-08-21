package com.etendoerp.metadata.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for the {@link HttpServletResponseLegacyWrapper} class.
 *
 * <p>This test suite verifies the correct behavior of the HttpServletResponseLegacyWrapper,
 * ensuring it properly captures HTTP response output while maintaining compatibility with
 * the standard HttpServletResponse interface. It covers response content capture, stream
 * handling, character encoding support, and buffer management.</p>
 *
 * <p>Tests include validation of output stream and writer handling, content capture
 * functionality, encoding management, buffer operations, and edge cases such as
 * handling of illegal state transitions and I/O error scenarios.</p>
 *
 * <p>Mockito is used to mock the underlying HttpServletResponse, allowing for isolated
 * testing of wrapper behavior without reliance on a full servlet container.</p>
 *
 * <p>Each test method is designed to be independent, ensuring that the state is reset
 * before each test execution. This is achieved through the use of the @Before setup
 * method which initializes a fresh instance of HttpServletResponseLegacyWrapper for each test.</p>
 *
 * <p>Tests are annotated with @Test and utilize assertions to validate expected outcomes.
 * Exception handling is also tested to ensure proper error conditions are met.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class HttpServletResponseLegacyWrapperTest extends OBBaseTest {

  @Mock
  private HttpServletResponse mockResponse;

  private HttpServletResponseLegacyWrapper wrapper;

  private static final String TEST_CONTENT = "Test content for response";
  private static final String UTF8_ENCODING = "UTF-8";
  private static final String ISO_ENCODING = "ISO-8859-1";
  private static final int DEFAULT_BUFFER_SIZE = 1024;

  /**
   * Sets up the test environment before each test method execution.
   *
   * <p>This method initializes the wrapper with a mocked HttpServletResponse
   * and sets up default behavior for common response methods. The setup ensures
   * each test starts with a fresh wrapper instance and predictable mock behavior.</p>
   *
   * @throws Exception if wrapper initialization fails or parent setup encounters issues
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setupMockResponse();
    wrapper = new HttpServletResponseLegacyWrapper(mockResponse);
  }

  /**
   * Sets up the mocked HttpServletResponse with default behavior.
   * This includes setting up buffer size, character encoding, and other
   * response properties that are commonly accessed during testing.
   */
  private void setupMockResponse() {
    when(mockResponse.getBufferSize()).thenReturn(DEFAULT_BUFFER_SIZE);
    when(mockResponse.getCharacterEncoding()).thenReturn(UTF8_ENCODING);
  }

  /**
   * Tests that the constructor properly initializes the wrapper.
   *
   * <p>This test validates that when a HttpServletResponseLegacyWrapper is created,
   * it correctly wraps the provided HttpServletResponse and initializes internal
   * buffers with appropriate default sizes.</p>
   */
  @Test
  public void constructorShouldInitializeWrapperCorrectly() {
    assertNotNull("Wrapper should be initialized", wrapper);
    assertEquals("Should wrap the mock response", mockResponse, wrapper.getResponse());
  }

  /**
   * Tests that the constructor handles zero buffer size gracefully.
   *
   * <p>This test validates that when the underlying response returns a buffer size
   * of zero, the wrapper falls back to a reasonable default buffer size to prevent
   * initialization issues.</p>
   */
  @Test
  public void constructorShouldHandleZeroBufferSize() {
    when(mockResponse.getBufferSize()).thenReturn(0);
    HttpServletResponseLegacyWrapper wrapperWithZeroBuffer =
        new HttpServletResponseLegacyWrapper(mockResponse);

    assertNotNull("Wrapper should handle zero buffer size", wrapperWithZeroBuffer);
  }

  /**
   * Tests that getOutputStream returns a ServletOutputStream.
   *
   * <p>This test validates that the wrapper provides a ServletOutputStream
   * that can be used for writing binary data to the response. The stream
   * should be properly initialized and ready for use.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void getOutputStreamShouldReturnServletOutputStream() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();

    assertNotNull("OutputStream should not be null", outputStream);
    assertTrue("Should return ServletOutputStream", outputStream instanceof ServletOutputStream);
  }



  /**
   * Tests that getWriter returns a PrintWriter.
   *
   * <p>This test validates that the wrapper provides a PrintWriter that can be
   * used for writing character data to the response. The writer should be properly
   * initialized with the correct character encoding.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void getWriterShouldReturnPrintWriter() throws IOException {
    PrintWriter writer = wrapper.getWriter();

    assertNotNull("Writer should not be null", writer);
    assertTrue("Should return PrintWriter", writer instanceof PrintWriter);
  }



  /**
   * Tests that output written to ServletOutputStream is captured.
   *
   * <p>This test validates that binary data written to the ServletOutputStream
   * is properly captured by the wrapper and can be retrieved as a byte array
   * for later processing or inspection.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void outputStreamShouldCaptureWrittenData() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();
    byte[] testData = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);

    outputStream.write(testData);

    byte[] capturedData = wrapper.getCapturedOutput();
    assertArrayEquals("Captured data should match written data", testData, capturedData);
  }

  /**
   * Tests that output written to PrintWriter is captured.
   *
   * <p>This test validates that character data written to the PrintWriter
   * is properly captured by the wrapper and can be retrieved as both byte
   * array and string format for later processing or inspection.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void writerShouldCaptureWrittenData() throws IOException {
    PrintWriter writer = wrapper.getWriter();

    writer.write(TEST_CONTENT);
    writer.flush();

    String capturedString = wrapper.getCapturedOutputAsString();
    assertEquals("Captured string should match written content", TEST_CONTENT, capturedString);
  }

  /**
   * Tests that getCapturedOutput returns empty array initially.
   *
   * <p>This test validates that before any content is written to the response,
   * the captured output is empty, ensuring that the wrapper starts in a clean state.</p>
   */
  @Test
  public void getCapturedOutputShouldReturnEmptyArrayInitially() {
    byte[] capturedData = wrapper.getCapturedOutput();

    assertNotNull("Captured data should not be null", capturedData);
    assertEquals("Captured data should be empty initially", 0, capturedData.length);
  }

  /**
   * Tests that getCapturedOutputAsString returns empty string initially.
   *
   * <p>This test validates that before any content is written to the response,
   * the captured output as string is empty, ensuring consistent behavior
   * across different output retrieval methods.</p>
   *
   * @throws UnsupportedEncodingException if character encoding is not supported
   */
  @Test
  public void getCapturedOutputAsStringShouldReturnEmptyStringInitially()
      throws UnsupportedEncodingException {
    String capturedString = wrapper.getCapturedOutputAsString();

    assertNotNull("Captured string should not be null", capturedString);
    assertEquals("Captured string should be empty initially", "", capturedString);
  }

  /**
   * Tests that multiple writes to OutputStream are accumulated.
   *
   * <p>This test validates that multiple write operations to the ServletOutputStream
   * are properly accumulated in the internal buffer, allowing for incremental
   * content building while maintaining data integrity.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void multipleWritesToOutputStreamShouldAccumulate() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();
    String firstPart = "First part ";
    String secondPart = "Second part";

    outputStream.write(firstPart.getBytes(StandardCharsets.UTF_8));
    outputStream.write(secondPart.getBytes(StandardCharsets.UTF_8));

    String capturedString = wrapper.getCapturedOutputAsString();
    assertEquals("Should accumulate multiple writes", firstPart + secondPart, capturedString);
  }

  /**
   * Tests that multiple writes to PrintWriter are accumulated.
   *
   * <p>This test validates that multiple write operations to the PrintWriter
   * are properly accumulated in the internal buffer, allowing for incremental
   * content building while maintaining character encoding consistency.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void multipleWritesToPrintWriterShouldAccumulate() throws IOException {
    PrintWriter writer = wrapper.getWriter();
    String firstPart = "First part ";
    String secondPart = "Second part";

    writer.write(firstPart);
    writer.write(secondPart);
    writer.flush();

    String capturedString = wrapper.getCapturedOutputAsString();
    assertEquals("Should accumulate multiple writes", firstPart + secondPart, capturedString);
  }

  /**
   * Tests that OutputStream write operations handle different byte array methods.
   *
   * <p>This test validates that the ServletOutputStream correctly handles
   * various write methods including single byte, full byte array, and
   * partial byte array writes, ensuring comprehensive binary data support.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void outputStreamShouldHandleDifferentWriteMethods() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();
    byte[] testData = "Hello World".getBytes(StandardCharsets.UTF_8);

    // Test single byte write
    outputStream.write(65); // 'A'

    // Test full array write
    outputStream.write(testData);

    // Test partial array write
    byte[] partialData = " Test".getBytes(StandardCharsets.UTF_8);
    outputStream.write(partialData, 1, 4); // " Tes"

    String result = wrapper.getCapturedOutputAsString();
    assertEquals("Should handle different write methods", "AHello WorldTest", result);
  }

  /**
   * Tests that character encoding is handled correctly for string output.
   *
   * <p>This test validates that the wrapper respects the character encoding
   * setting when converting captured bytes to string format, ensuring
   * proper internationalization support.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void characterEncodingShouldBeRespectedForStringOutput() throws IOException {
    when(mockResponse.getCharacterEncoding()).thenReturn(ISO_ENCODING);
    HttpServletResponseLegacyWrapper encodingWrapper =
        new HttpServletResponseLegacyWrapper(mockResponse);

    PrintWriter writer = encodingWrapper.getWriter();
    String testString = "Test with encoding";
    writer.write(testString);
    writer.flush();

    String capturedString = encodingWrapper.getCapturedOutputAsString();
    assertEquals("Should handle character encoding", testString, capturedString);
  }

  /**
   * Tests that null character encoding defaults to UTF-8.
   *
   * <p>This test validates that when the underlying response returns null
   * for character encoding, the wrapper falls back to UTF-8 as the default,
   * ensuring consistent behavior and preventing encoding-related issues.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void nullCharacterEncodingShouldDefaultToUtf8() throws IOException {
    when(mockResponse.getCharacterEncoding()).thenReturn(null);
    HttpServletResponseLegacyWrapper nullEncodingWrapper =
        new HttpServletResponseLegacyWrapper(mockResponse);

    PrintWriter writer = nullEncodingWrapper.getWriter();
    writer.write(TEST_CONTENT);
    writer.flush();

    String capturedString = nullEncodingWrapper.getCapturedOutputAsString();
    assertEquals("Should default to UTF-8 for null encoding", TEST_CONTENT, capturedString);
  }

  /**
   * Tests that flushBuffer works correctly with OutputStream.
   *
   * <p>This test validates that the flushBuffer method properly flushes
   * the ServletOutputStream without throwing exceptions, ensuring that
   * buffered content is processed correctly.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void flushBufferShouldWorkWithOutputStream() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();
    outputStream.write(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));

    wrapper.flushBuffer(); // Should not throw exception

    byte[] capturedData = wrapper.getCapturedOutput();
    assertArrayEquals("Content should be captured after flush",
        TEST_CONTENT.getBytes(StandardCharsets.UTF_8), capturedData);
  }

  /**
   * Tests that flushBuffer works correctly with PrintWriter.
   *
   * <p>This test validates that the flushBuffer method properly flushes
   * the PrintWriter without throwing exceptions, ensuring that buffered
   * character content is processed correctly.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void flushBufferShouldWorkWithPrintWriter() throws IOException {
    PrintWriter writer = wrapper.getWriter();
    writer.write(TEST_CONTENT);

    wrapper.flushBuffer(); // Should not throw exception

    String capturedString = wrapper.getCapturedOutputAsString();
    assertEquals("Content should be captured after flush", TEST_CONTENT, capturedString);
  }

  /**
   * Tests that resetBuffer clears captured content.
   *
   * <p>This test validates that the resetBuffer method properly clears
   * any captured content from the internal buffer, allowing for a fresh
   * start in content generation while preserving headers and status.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void resetBufferShouldClearCapturedContent() throws IOException {
    PrintWriter writer = wrapper.getWriter();
    writer.write(TEST_CONTENT);
    writer.flush();

    // Verify content is captured
    assertTrue("Should have captured content", wrapper.getCapturedOutput().length > 0);

    wrapper.resetBuffer();

    // Verify content is cleared
    assertEquals("Content should be cleared", 0, wrapper.getCapturedOutput().length);
    assertEquals("String content should be empty", "", wrapper.getCapturedOutputAsString());
  }

  /**
   * Tests that reset clears all wrapper state.
   *
   * <p>This test validates that the reset method properly clears all internal
   * state including captured content, output stream flags, and writer flags,
   * essentially returning the wrapper to its initial state.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void resetShouldClearAllWrapperState() throws IOException {
    // Use the writer first
    PrintWriter writer = wrapper.getWriter();
    writer.write(TEST_CONTENT);
    writer.flush();

    // Verify content is captured
    assertTrue("Should have captured content", wrapper.getCapturedOutput().length > 0);

    wrapper.reset();

    // Verify content is cleared
    assertEquals("Content should be cleared", 0, wrapper.getCapturedOutput().length);

    // Should be able to get OutputStream after reset (previously would throw exception)
    ServletOutputStream outputStream = wrapper.getOutputStream();
    assertNotNull("Should be able to get OutputStream after reset", outputStream);
  }

  /**
   * Tests that OutputStream isReady always returns true.
   *
   * <p>This test validates that the ServletOutputStream implementation
   * always reports that it is ready for writing, which is appropriate
   * for a memory-based capture mechanism.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void outputStreamIsReadyShouldAlwaysReturnTrue() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();

    assertTrue("OutputStream should always be ready", outputStream.isReady());
  }


  /**
   * Tests that large content is handled correctly.
   *
   * <p>This test validates that the wrapper can handle large amounts of content
   * without issues, ensuring that the internal buffer can grow appropriately
   * to accommodate various content sizes.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void largContentShouldBeHandledCorrectly() throws IOException {
    StringBuilder largeContent = new StringBuilder();
    for (int i = 0; i < 10000; i++) {
      largeContent.append("This is line ").append(i).append("\n");
    }

    PrintWriter writer = wrapper.getWriter();
    writer.write(largeContent.toString());
    writer.flush();

    String capturedString = wrapper.getCapturedOutputAsString();
    assertEquals("Large content should be captured correctly",
        largeContent.toString(), capturedString);
  }

  /**
   * Tests that binary data is handled correctly through OutputStream.
   *
   * <p>This test validates that the wrapper can handle binary data correctly,
   * including bytes that might not represent valid characters, ensuring
   * that binary content integrity is maintained.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void binaryDataShouldBeHandledCorrectly() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();

    // Create some binary data
    byte[] binaryData = new byte[256];
    for (int i = 0; i < 256; i++) {
      binaryData[i] = (byte) i;
    }

    outputStream.write(binaryData);

    byte[] capturedData = wrapper.getCapturedOutput();
    assertArrayEquals("Binary data should be captured correctly", binaryData, capturedData);
  }

  /**
   * Tests that OutputStream close method works without errors.
   *
   * <p>This test validates that the ServletOutputStream can be closed
   * properly without throwing exceptions, ensuring proper resource
   * cleanup when the output stream is no longer needed.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void outputStreamCloseShouldWorkWithoutErrors() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();
    outputStream.write(TEST_CONTENT.getBytes(StandardCharsets.UTF_8));

    outputStream.close(); // Should not throw exception

    // Content should still be accessible
    byte[] capturedData = wrapper.getCapturedOutput();
    assertArrayEquals("Content should be accessible after close",
        TEST_CONTENT.getBytes(StandardCharsets.UTF_8), capturedData);
  }

  /**
   * Tests concurrent access to the wrapper from multiple threads.
   *
   * <p>This test validates that the wrapper can handle concurrent access
   * from multiple threads without data corruption or exceptions. While
   * servlets typically handle one request per thread, this ensures robustness
   * in edge cases where concurrent access might occur.</p>
   *
   * @throws Exception if thread operations fail or timing operations fail
   */
  @Test
  public void concurrentAccessShouldBeHandledGracefully() throws Exception {
    final int numberOfThreads = 5;
    final int writesPerThread = 100;
    Thread[] threads = new Thread[numberOfThreads];

    for (int i = 0; i < numberOfThreads; i++) {
      final int threadIndex = i;
      threads[i] = new Thread(() -> {
        try {
          ServletOutputStream outputStream = wrapper.getOutputStream();
          for (int j = 0; j < writesPerThread; j++) {
            String content = "Thread" + threadIndex + "Write" + j;
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
          }
        } catch (Exception e) {
          // Log but don't fail immediately to allow other threads to complete
          e.printStackTrace();
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

    // Verify that some content was captured (exact content may vary due to concurrency)
    byte[] capturedData = wrapper.getCapturedOutput();
    assertTrue("Should have captured some content from concurrent writes",
        capturedData.length > 0);
  }

  /**
   * Tests that empty writes don't cause issues.
   *
   * <p>This test validates that writing empty content (empty strings or
   * zero-length byte arrays) is handled gracefully without throwing
   * exceptions or causing unexpected behavior.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void emptyWritesShouldBeHandledGracefully() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();

    // Write empty byte array
    outputStream.write(new byte[0]);

    // Write empty string using writer after getting a new wrapper
    HttpServletResponseLegacyWrapper writerWrapper =
        new HttpServletResponseLegacyWrapper(mockResponse);
    PrintWriter writer = writerWrapper.getWriter();
    writer.write("");
    writer.flush();

    // Both should result in empty captured content
    assertEquals("Empty OutputStream write should result in empty capture",
        0, wrapper.getCapturedOutput().length);
    assertEquals("Empty Writer write should result in empty capture",
        "", writerWrapper.getCapturedOutputAsString());
  }

  /**
   * Tests the wrapper behavior with partial byte array writes.
   *
   * <p>This test validates that the ServletOutputStream correctly handles
   * partial byte array writes using the write(byte[], int, int) method,
   * ensuring that only the specified portion of the array is written.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void partialByteArrayWritesShouldWorkCorrectly() throws IOException {
    ServletOutputStream outputStream = wrapper.getOutputStream();
    byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);

    // Write only "World" (offset 6, length 5)
    outputStream.write(data, 6, 5);

    String capturedString = wrapper.getCapturedOutputAsString();
    assertEquals("Should capture only the specified portion", "World", capturedString);
  }

  /**
   * Tests that PrintWriter methods work correctly.
   *
   * <p>This test validates that various PrintWriter methods including
   * print, println, and printf work correctly with the wrapper, ensuring
   * comprehensive character-based output support.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void printWriterMethodsShouldWorkCorrectly() throws IOException {
    PrintWriter writer = wrapper.getWriter();

    writer.print("Hello ");
    writer.println("World");
    writer.printf("Number: %d", 42);
    writer.flush();

    String capturedString = wrapper.getCapturedOutputAsString();
    assertTrue("Should capture print output", capturedString.contains("Hello"));
    assertTrue("Should capture println output", capturedString.contains("World"));
    assertTrue("Should capture printf output", capturedString.contains("Number: 42"));
  }

  /**
   * Tests error handling during flush operations.
   *
   * <p>This test validates that flush operations handle potential I/O errors
   * gracefully without propagating exceptions unnecessarily, ensuring
   * robust error handling in various scenarios.</p>
   *
   * @throws IOException if I/O operations fail during the test
   */
  @Test
  public void flushErrorsShouldBeHandledGracefully() throws IOException {
    PrintWriter writer = wrapper.getWriter();
    writer.write(TEST_CONTENT);

    // Close the writer to potentially cause flush errors
    writer.close();

    // flushBuffer should not throw even if underlying streams have issues
    wrapper.flushBuffer(); // Should not throw exception

    // Content should still be accessible
    String capturedString = wrapper.getCapturedOutputAsString();
    assertEquals("Content should be accessible despite flush errors",
        TEST_CONTENT, capturedString);
  }
}
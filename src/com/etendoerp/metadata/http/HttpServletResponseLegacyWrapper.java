package com.etendoerp.metadata.http;

 import javax.servlet.ServletOutputStream;
 import javax.servlet.WriteListener;
 import javax.servlet.http.HttpServletResponse;
 import java.io.ByteArrayOutputStream;
 import java.io.IOException;
 import java.io.OutputStreamWriter;
 import java.io.PrintWriter;
 import java.io.UnsupportedEncodingException;
 import java.nio.charset.StandardCharsets;

 /**
  * A custom HttpServletResponseLegacyWrapper that captures the output written to the response.
  * It allows retrieval of the captured output as a byte array or string.
  */
 public class HttpServletResponseLegacyWrapper extends javax.servlet.http.HttpServletResponseWrapper {

   // Buffer to capture response content.
   private final ByteArrayOutputStream contentCapture;
   private CapturingServletOutputStream capturingStream;
   private PrintWriter writer;
   private boolean outputStreamUsed = false;
   private boolean writerUsed = false;

   /**
    * Constructs a new HttpServletResponseLegacyWrapper.
    *
    * @param response The original HttpServletResponse.
    */
   public HttpServletResponseLegacyWrapper(HttpServletResponse response) {
     super(response);
     int bufferSize = response.getBufferSize() > 0 ? response.getBufferSize() : 1024;
     this.contentCapture = new ByteArrayOutputStream(bufferSize);
   }

   /**
    * Returns a ServletOutputStream which writes to an internal buffer.
    *
    * @return A ServletOutputStream for writing binary data.
    * @throws IOException If an I/O error occurs.
    * @throws IllegalStateException If getWriter() has already been called.
    */
   @Override
   public ServletOutputStream getOutputStream() throws IOException {
     if (writerUsed) {
       throw new IllegalStateException("getWriter() has already been called for this response");
     }
     if (capturingStream == null) {
       capturingStream = new CapturingServletOutputStream(contentCapture);
     }
     outputStreamUsed = true;
     return capturingStream;
   }

   /**
    * Returns a PrintWriter that writes to an internal buffer.
    *
    * @return A PrintWriter for writing character data.
    * @throws IOException If an I/O error occurs.
    * @throws IllegalStateException If getOutputStream() has already been called.
    */
   @Override
   public PrintWriter getWriter() throws IOException {
     if (outputStreamUsed) {
       throw new IllegalStateException("getOutputStream() has already been called for this response");
     }
     if (writer == null) {
       String encoding = getCharacterEncoding();
       if (encoding == null) {
         encoding = StandardCharsets.UTF_8.name();
       }
       writer = new PrintWriter(new OutputStreamWriter(contentCapture, encoding));
     }
     writerUsed = true;
     return writer;
   }

   /**
    * Retrieves the captured response content as a byte array.
    *
    * @return The captured content in bytes.
    */
   public byte[] getCapturedOutput() {
     try {
       if (writer != null) {
         writer.flush();
       } else if (capturingStream != null) {
         capturingStream.flush();
       }
     } catch (IOException e) {
       // Flush exceptions are ignored.
     }
     return contentCapture.toByteArray();
   }

   /**
    * Retrieves the captured response content as a String.
    *
    * @return The captured content as a String.
    * @throws UnsupportedEncodingException If the character encoding is not supported.
    */
   public String getCapturedOutputAsString() throws UnsupportedEncodingException {
     byte[] bytes = getCapturedOutput();
     String encoding = getCharacterEncoding();
     if (encoding == null) {
       encoding = StandardCharsets.UTF_8.name();
     }
     return new String(bytes, encoding);
   }

   /**
    * Flush the buffers of the writer or output stream.
    *
    * @throws IOException If an I/O error occurs.
    */
   @Override
   public void flushBuffer() throws IOException {
     if (writer != null) {
       writer.flush();
     } else if (capturingStream != null) {
       capturingStream.flush();
     }
   }

   /**
    * Resets the response buffer and the captured content.
    */
   @Override
   public void resetBuffer() {
     super.resetBuffer();
     contentCapture.reset();
   }

   /**
    * Resets the entire response state including headers, status, and captured content.
    */
   @Override
   public void reset() {
     super.reset();
     contentCapture.reset();
     outputStreamUsed = false;
     writerUsed = false;
     writer = null;
     capturingStream = null;
   }

   /**
    * A custom implementation of ServletOutputStream that writes to a ByteArrayOutputStream.
    */
   private static class CapturingServletOutputStream extends ServletOutputStream {

     // Underlying buffer to capture output.
     private final ByteArrayOutputStream buffer;

     /**
      * Constructs a new CapturingServletOutputStream.
      *
      * @param buffer The ByteArrayOutputStream to capture output.
      */
     public CapturingServletOutputStream(ByteArrayOutputStream buffer) {
       this.buffer = buffer;
     }

     /**
      * Writes a single byte to the buffer.
      *
      * @param b The byte to write.
      * @throws IOException If an I/O error occurs.
      */
     @Override
     public void write(int b) throws IOException {
       buffer.write(b);
     }

     /**
      * Writes an array of bytes to the buffer.
      *
      * @param b The byte array to write.
      * @throws IOException If an I/O error occurs.
      */
     @Override
     public void write(byte[] b) throws IOException {
       buffer.write(b);
     }

     /**
      * Writes a subarray of bytes to the buffer.
      *
      * @param b   The byte array.
      * @param off The start offset in the array.
      * @param len The number of bytes to write.
      * @throws IOException If an I/O error occurs.
      */
     @Override
     public void write(byte[] b, int off, int len) throws IOException {
       buffer.write(b, off, len);
     }

     /**
      * Indicates if the stream is ready to be written.
      *
      * @return Always true.
      */
     @Override
     public boolean isReady() {
       return true;
     }

     /**
      * Not supported for this implementation.
      *
      * @param writeListener The WriteListener to set.
      * @throws UnsupportedOperationException Always thrown as WriteListener is not supported.
      */
     @Override
     public void setWriteListener(WriteListener writeListener) {
       throw new UnsupportedOperationException("WriteListener is not supported");
     }

     /**
      * Flushes the underlying buffer.
      *
      * @throws IOException If an I/O error occurs.
      */
     @Override
     public void flush() throws IOException {
       buffer.flush();
     }

     /**
      * Closes the underlying buffer.
      *
      * @throws IOException If an I/O error occurs.
      */
     @Override
     public void close() throws IOException {
       buffer.close();
       super.close();
     }
   }
 }

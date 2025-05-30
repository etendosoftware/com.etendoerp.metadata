package com.etendoerp.metadata.data;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class ContentCaptureWrapper extends HttpServletResponseWrapper {
  private final CharArrayWriter charArray = new CharArrayWriter();
  private final PrintWriter writer = new PrintWriter(charArray);

  public ContentCaptureWrapper(HttpServletResponse response) {
    super(response);
  }

  @Override
  public PrintWriter getWriter() {
    return writer;
  }

  public String getCapturedContent() {
    writer.flush();
    return charArray.toString();
  }
}

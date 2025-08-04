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

    public String replace(String framesetCloseTag, String concat) {
        return null;
    }
}

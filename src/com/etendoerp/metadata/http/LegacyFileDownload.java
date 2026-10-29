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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.http;

import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file answered by a Classic servlet running inside the legacy process popup (e.g. the
 * bank file of Create Remittance File). The captured bytes and headers are kept in the HTTP
 * session until the popup downloads them once through {@code /meta/legacy/download}, the same
 * single-use approach Classic follows with {@code renderJR} and {@code DownloadReport}.
 */
public final class LegacyFileDownload implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String SESSION_KEY_PREFIX = "ETMETA_LEGACY_DOWNLOAD_";
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("filename=\\s*+\"?+([^\";]++)", Pattern.CASE_INSENSITIVE);

    private final byte[] content;
    private final String contentType;
    private final String contentDisposition;

    /**
     * Creates a captured file.
     *
     * @param content            the raw file bytes
     * @param contentType        the Content-Type declared by the Classic servlet, may be {@code null}
     * @param contentDisposition the Content-Disposition declared by the Classic servlet
     */
    public LegacyFileDownload(byte[] content, String contentType, String contentDisposition) {
        this.content = content.clone();
        this.contentType = contentType;
        this.contentDisposition = contentDisposition;
    }

    /**
     * @return a copy of the raw file bytes
     */
    public byte[] getContent() {
        return content.clone();
    }

    /**
     * @return the Content-Type declared by the Classic servlet, may be {@code null}
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * @return the Content-Disposition declared by the Classic servlet
     */
    public String getContentDisposition() {
        return contentDisposition;
    }

    /**
     * Extracts the file name from the {@code filename=} part of the Content-Disposition.
     *
     * @return the file name, or an empty string when the disposition does not carry one
     */
    public String getFileName() {
        if (contentDisposition == null) {
            return "";
        }
        Matcher matcher = FILE_NAME_PATTERN.matcher(contentDisposition);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    /**
     * Stores the file in the session under a new random identifier.
     *
     * @param session the HTTP session of the popup
     * @param file    the file to store
     * @return the identifier to download the file with
     */
    public static String store(HttpSession session, LegacyFileDownload file) {
        String id = UUID.randomUUID().toString();
        session.setAttribute(SESSION_KEY_PREFIX + id, file);
        return id;
    }

    /**
     * Returns the stored file and removes it from the session, so every file is downloaded once.
     *
     * @param session the HTTP session of the popup, may be {@code null}
     * @param id      the identifier returned by {@link #store(HttpSession, LegacyFileDownload)}
     * @return the stored file, or {@code null} when the identifier is invalid, unknown or already used
     */
    public static LegacyFileDownload take(HttpSession session, String id) {
        if (session == null || !isValidId(id)) {
            return null;
        }
        String key = SESSION_KEY_PREFIX + id;
        Object stored = session.getAttribute(key);
        session.removeAttribute(key);
        if (stored instanceof LegacyFileDownload) {
            return (LegacyFileDownload) stored;
        }
        return null;
    }

    private static boolean isValidId(String id) {
        if (id == null) {
            return false;
        }
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

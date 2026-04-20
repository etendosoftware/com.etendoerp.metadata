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

package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.utils.Constants;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Immutable holder for the parameters needed to launch a legacy process in an iframe.
 * <p>
 * All four fields are required by the frontend to build the Classic URL.
 * {@code keyColumnName} and {@code inpkeyColumnId} carry the same value (the primary-key
 * DB column name) because the Classic framework expects both parameter names.
 * </p>
 */
public final class LegacyProcessParams {

    private final String url;
    private final String command;
    private final String keyColumnName;
    private final String inpkeyColumnId;

    public LegacyProcessParams(String url, String command, String keyColumnName, String inpkeyColumnId) {
        this.url = url;
        this.command = command;
        this.keyColumnName = keyColumnName;
        this.inpkeyColumnId = inpkeyColumnId;
    }

    /**
     * Serializes these params into a {@link JSONObject} using the shared key constants
     * from {@link Constants}.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(Constants.LEGACY_URL, url);
        json.put(Constants.LEGACY_COMMAND, command);
        json.put(Constants.LEGACY_KEY_COLUMN_NAME, keyColumnName);
        json.put(Constants.LEGACY_INP_KEY_COLUMN_ID, inpkeyColumnId);
        return json;
    }
}

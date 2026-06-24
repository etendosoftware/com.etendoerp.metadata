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

package com.etendoerp.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Base class for metadata JSON builders that converts entities to JSON representations.
 */
public abstract class Builder {
    /** Shared logger instance for all builder subclasses. */
    protected static final Logger logger = LogManager.getLogger(Builder.class);
    /** The current user's language, used for translatable properties. */
    public final Language language = OBContext.getOBContext().getLanguage();
    protected final DataToJsonConverter converter = new DataToJsonConverter();

    /**
     * Converts this builder's entity into a JSON representation.
     *
     * @return a {@link JSONObject} representing the entity
     * @throws JSONException if there is an error during JSON construction
     */
    public abstract JSONObject toJSON() throws JSONException;

    /**
     * Puts a value under the given key, keeping the key always present: a {@code null}
     * value is written as {@link JSONObject#NULL} rather than removed (Jettison's
     * {@code put(key, null)} deletes the entry). This preserves the stable
     * "key always present, JSON null when empty" contract for explicitly-emitted
     * properties that the {@link DataToJsonConverter} may skip for derived-readable
     * entities.
     *
     * @param json  the JSON object to write into
     * @param key   the property key that must always be present
     * @param value the value to write, or {@code null} to write JSON null
     * @throws JSONException if there is an error writing the value
     */
    protected static void putValueOrNull(JSONObject json, String key, Object value) throws JSONException {
        json.put(key, value == null ? JSONObject.NULL : value);
    }
}

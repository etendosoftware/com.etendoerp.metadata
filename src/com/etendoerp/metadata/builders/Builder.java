package com.etendoerp.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * @author luuchorocha
 */
public abstract class Builder {
    public static final Logger logger = LogManager.getLogger();

    protected final DataToJsonConverter converter;

    public Builder() {
        this.converter = new DataToJsonConverter();
    }

    public abstract JSONObject toJSON() throws JSONException;
}

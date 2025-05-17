package com.etendoerp.metadata.data;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public interface SerializableEntity {
    JSONObject toJSON() throws JSONException;
}

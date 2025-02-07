package com.etendoerp.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

public abstract class Builder {
    public final Logger logger = LogManager.getLogger(this.getClass());

    public abstract JSONObject toJSON();
}

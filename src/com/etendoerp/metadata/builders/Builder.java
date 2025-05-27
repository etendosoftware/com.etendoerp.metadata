package com.etendoerp.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * @author luuchorocha
 */
public abstract class Builder {
  protected static final Logger logger = LogManager.getLogger(Builder.class);
  public final Language language = OBContext.getOBContext().getLanguage();
  protected final DataToJsonConverter converter = new DataToJsonConverter();

  public abstract JSONObject toJSON() throws JSONException;
}

package com.etendoerp.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

public class LanguageBuilder {
    private static final Logger logger = LogManager.getLogger(LanguageBuilder.class);
    private static final String IS_SYSTEM_LANGUAGE = "Y";

    public JSONArray toJSON() {
        JSONArray json = new JSONArray();
        DataToJsonConverter converter = new DataToJsonConverter();

        try {
            OBDal.getInstance()
                 .createCriteria(Language.class)
                 .add(Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true))
                 .list()
                 .forEach((lang) -> json.put(converter.toJsonObject(lang, DataResolvingMode.FULL_TRANSLATABLE)));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return json;
    }
}

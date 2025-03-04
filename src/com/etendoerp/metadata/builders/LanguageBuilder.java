package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataResolvingMode;

/**
 * @author luuchorocha
 */
public class LanguageBuilder extends Builder {
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        try {
            for (Language lang : OBDal.getInstance()
                                      .createCriteria(Language.class)
                                      .add(Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true))
                                      .list()) {
                json.put(lang.getLanguage(), converter.toJsonObject(lang, DataResolvingMode.FULL_TRANSLATABLE));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return json;
    }
}

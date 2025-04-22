package com.etendoerp.metadata.builders;

import static org.openbravo.model.ad.system.Language.PROPERTY_ID;
import static org.openbravo.model.ad.system.Language.PROPERTY_LANGUAGE;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;

/**
 * @author luuchorocha
 */
public class LanguageBuilder extends Builder {
    private static final String PROPERTIES = String.join(",", PROPERTY_ID, PROPERTY_LANGUAGE);

    public LanguageBuilder() {
        converter.setSelectedProperties(PROPERTIES);
    }

    private List<Language> getLanguages() {
        return OBDal.getReadOnlyInstance().createCriteria(Language.class).add(
            Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true)).list();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        try {
            for (Language lang : getLanguages()) {
                json.put(lang.getLanguage(), converter.toJsonObject(lang, null));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return json;
    }
}

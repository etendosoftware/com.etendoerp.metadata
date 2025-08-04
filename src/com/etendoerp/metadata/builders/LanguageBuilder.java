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

import static org.openbravo.model.ad.system.Language.PROPERTY_ID;
import static org.openbravo.model.ad.system.Language.PROPERTY_LANGUAGE;
import static org.openbravo.model.ad.system.Language.PROPERTY_NAME;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataResolvingMode;

/**
 * @author luuchorocha
 */
public class LanguageBuilder extends Builder {
    private static final String PROPERTIES = String.join(",", PROPERTY_ID, PROPERTY_LANGUAGE, PROPERTY_NAME);

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
                json.put(lang.getLanguage(), converter.toJsonObject(lang, DataResolvingMode.FULL_TRANSLATABLE));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return json;
    }
}

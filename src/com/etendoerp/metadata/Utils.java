package com.etendoerp.metadata;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * @author luuchorocha
 */
public class Utils {
    public static Language getLanguage(HttpServletRequest request) {
        String[] providedLanguages = {request.getParameter("language"), request.getHeader("language")};
        String languageCode = Arrays.stream(providedLanguages)
                                    .filter(language -> language != null && !language.isEmpty())
                                    .findFirst()
                                    .orElse(null);

        return (Language) OBDal.getInstance()
                               .createCriteria(Language.class)
                               .add(Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true))
                               .add(Restrictions.eq(Language.PROPERTY_ACTIVE, true))
                               .add(Restrictions.eq(Language.PROPERTY_LANGUAGE, languageCode))
                               .uniqueResult();
    }

}

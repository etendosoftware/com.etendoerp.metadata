package com.etendoerp.metadata.utils;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.expression.OBScriptEngine;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static org.openbravo.client.application.DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic;

/**
 * @author luuchorocha
 */
public class Utils {
    public static Language getLanguage(HttpServletRequest request) {
        String[] providedLanguages = {request.getParameter("language"), request.getHeader("language")};
        String languageCode =
                Arrays.stream(providedLanguages).filter(language -> language != null && !language.isEmpty()).findFirst()
                      .orElse(null);

        return (Language) OBDal.getInstance().createCriteria(Language.class)
                               .add(Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true))
                               .add(Restrictions.eq(Language.PROPERTY_ACTIVE, true))
                               .add(Restrictions.eq(Language.PROPERTY_LANGUAGE, languageCode)).uniqueResult();
    }

    public static boolean evaluateDisplayLogicAtServerLevel(Field field) {
        boolean result;
        String displayLogicEvaluatedInTheServer = field.getDisplayLogicEvaluatedInTheServer();

        if (displayLogicEvaluatedInTheServer == null) {
            return true;
        }

        String translatedDisplayLogic = replaceSystemPreferencesInDisplayLogic(displayLogicEvaluatedInTheServer);
        DynamicExpressionParser parser = new DynamicExpressionParser(translatedDisplayLogic, field.getTab());

        try {
            result = (Boolean) OBScriptEngine.getInstance().eval(parser.getJSExpression());
        } catch (ScriptException e) {
            result = true;
        }

        return result;
    }
}

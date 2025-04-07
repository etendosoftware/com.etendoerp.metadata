package com.etendoerp.metadata.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
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
    private static final Logger logger = LogManager.getLogger(Utils.class);
    private static final MessageFactory messageFactory = new ParameterizedMessageFactory();

    public static String formatMessage(String message, Object... params) {
        try {
            return messageFactory.newMessage(message, params).getFormattedMessage();
        } catch (Exception e) {
            logger.error(e);

            return message;
        }
    }

    public static Language getLanguage(HttpServletRequest request) {
        try {
            String[] providedLanguages = {request.getParameter("language"), request.getHeader("language")};
            String languageCode =
                    Arrays.stream(providedLanguages).filter(language -> language != null && !language.isEmpty())
                          .findFirst().orElse(null);

            return (Language) OBDal.getInstance().createCriteria(Language.class)
                                   .add(Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true))
                                   .add(Restrictions.eq(Language.PROPERTY_ACTIVE, true))
                                   .add(Restrictions.eq(Language.PROPERTY_LANGUAGE, languageCode)).uniqueResult();

        } catch (Exception e) {
            logger.error(e);

            return null;
        }
    }

    public static boolean evaluateDisplayLogicAtServerLevel(Field field) {
        boolean result;
        try {
            String displayLogicEvaluatedInTheServer = field.getDisplayLogicEvaluatedInTheServer();

            if (displayLogicEvaluatedInTheServer == null) {
                return true;
            }

            String translatedDisplayLogic = replaceSystemPreferencesInDisplayLogic(displayLogicEvaluatedInTheServer);
            DynamicExpressionParser parser = new DynamicExpressionParser(translatedDisplayLogic, field.getTab());

            result = (Boolean) OBScriptEngine.getInstance().eval(parser.getJSExpression());
        } catch (ScriptException e) {
            logger.error(e);

            result = true;
        }

        return result;
    }
}

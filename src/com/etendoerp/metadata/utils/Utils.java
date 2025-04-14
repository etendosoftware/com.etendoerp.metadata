package com.etendoerp.metadata.utils;

import static org.openbravo.client.application.DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic;

import javax.script.ScriptException;
import javax.servlet.ServletConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.openbravo.base.expression.OBScriptEngine;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.kernel.KernelServlet;
import org.openbravo.model.ad.ui.Field;

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

    public static void initializeGlobalConfig(ServletConfig config) {
        if (KernelServlet.getGlobalParameters() == null) {
            WeldUtils.getInstanceFromStaticBeanManager(KernelServlet.class).init(config);
        }
    }
}

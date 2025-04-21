package com.etendoerp.metadata.utils;

import static org.openbravo.client.application.DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic;

import javax.script.ScriptException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.expression.OBScriptEngine;
import org.openbravo.base.model.Property;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.application.Process;
import org.openbravo.client.kernel.KernelServlet;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.builders.ProcessDefinitionBuilder;

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

    public static Tab getReferencedTab(Property referenced) {
        OBDal dal = OBDal.getReadOnlyInstance();
        String tableId = referenced.getEntity().getTableId();
        Table table = dal.get(Table.class, tableId);

        return (Tab) dal.createCriteria(Tab.class).add(Restrictions.eq(Tab.PROPERTY_TABLE, table)).add(
            Restrictions.eq(Tab.PROPERTY_ACTIVE, true)).setMaxResults(1).uniqueResult();
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

    public static JSONObject getFieldProcess(Field field) throws JSONException {
        Process process = field.getColumn().getOBUIAPPProcess();

        if (process == null) {
            return new JSONObject();
        }

        JSONObject processJson = new ProcessDefinitionBuilder(process).toJSON();

        processJson.put("fieldId", field.getId());
        processJson.put("columnId", field.getColumn().getId());
        processJson.put("displayLogic", field.getDisplayLogic());
        processJson.put("buttonText", field.getColumn().getName());
        processJson.put("fieldName", field.getName());
        processJson.put("reference", field.getColumn().getReference().getId());

        return processJson;
    }

    public static JSONObject getRequestData(HttpServletRequest request) {
        try {
            return new JSONObject(request.getReader().lines().reduce("", String::concat));
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}

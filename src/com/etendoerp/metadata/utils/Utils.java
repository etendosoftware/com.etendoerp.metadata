package com.etendoerp.metadata.utils;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.openbravo.client.application.DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.authentication.AuthenticationException;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.expression.OBScriptEngine;
import org.openbravo.base.model.Property;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.builders.ProcessDefinitionBuilder;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * @author luuchorocha
 */
public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);
    private static final MessageFactory messageFactory = new ParameterizedMessageFactory();
    private static final Map<String, Integer> exceptionStatusMap = buildExceptionMap();
    private static final DataToJsonConverter converter = new DataToJsonConverter();

    private static Map<String, Integer> buildExceptionMap() {
        final Map<String, Integer> map = new HashMap<>();

        map.put(AuthenticationException.class.getName(), HttpStatus.SC_UNAUTHORIZED);
        map.put(OBSecurityException.class.getName(), SC_UNAUTHORIZED);
        map.put(UnauthorizedException.class.getName(), SC_UNAUTHORIZED);
        map.put(MethodNotAllowedException.class.getName(), SC_METHOD_NOT_ALLOWED);
        map.put(UnprocessableContentException.class.getName(), SC_UNPROCESSABLE_ENTITY);
        map.put(NotFoundException.class.getName(), HttpStatus.SC_NOT_FOUND);

        return map;
    }

    @SuppressWarnings("unused")
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

    public static JSONObject getFieldProcess(Field field) throws JSONException {
        Process process = field.getColumn().getOBUIAPPProcess();

        if (process == null) {
            return new JSONObject();
        }

        JSONObject processJson = new ProcessDefinitionBuilder(process).toJSON();
        Language language = OBContext.getOBContext().getLanguage();
        Column column = field.getColumn();

        processJson.put("fieldId", field.getId());
        processJson.put("columnId", column.getId());
        processJson.put("displayLogic", field.getDisplayLogic());
        processJson.put("buttonText", column.get(Column.PROPERTY_NAME, language, column.getId()));
        processJson.put("fieldName", field.get(Field.PROPERTY_NAME, language, field.getId()));
        processJson.put("reference", column.getReference().getId());

        return processJson;
    }

    public static JSONObject getRequestData(HttpServletRequest request) {
        try {
            return new JSONObject(request.getReader().lines().reduce("", String::concat));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static void setContext(HttpServletRequest request) {
        try {
            OBContext.setAdminMode(true);
            OBContext context = OBContext.getOBContext();
            Language language = getLanguage(request);

            if (language != null) {
                context.setLanguage(language);
            }

            /* Recreating the OBContext, because OBContext.setLanguage
             * does not update langID, only languageCode
             */
            OBContext.setOBContext(context.getUser().getId(), context.getRole().getId(),
                context.getCurrentClient().getId(), context.getCurrentOrganization().getId(),
                context.getLanguage().getLanguage(), context.getWarehouse().getId());

            OBContext.setOBContextInSession(request, OBContext.getOBContext());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private static Language getLanguage(HttpServletRequest request) {
        String[] providedLanguages = { request.getParameter("language"), request.getHeader("language") };
        String languageCode = Arrays.stream(providedLanguages).filter(
            language -> language != null && !language.isEmpty()).findFirst().orElse(null);

        return (Language) OBDal.getInstance().createCriteria(Language.class).add(
            Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true)).add(
            Restrictions.eq(Language.PROPERTY_ACTIVE, true)).add(
            Restrictions.eq(Language.PROPERTY_LANGUAGE, languageCode)).setMaxResults(1).uniqueResult();
    }

    public static int getHttpStatusFor(Throwable t) {
      return Objects.requireNonNullElse(exceptionStatusMap.get(t.getClass().getName()), SC_INTERNAL_SERVER_ERROR);
    }

    public static JSONObject convertToJson(Throwable t) {
        JSONObject json = new JSONObject();

        if (t.getCause() != null) {
            t = t.getCause();
        }

        try {
            json.put("error", t.getMessage());
        } catch (JSONException e) {
            logger.warn(e.getMessage(), e);
        }

        return json;
    }

    public static JSONObject getJsonObject(BaseOBObject object) {
        if (object != null) {
            return converter.toJsonObject(object, DataResolvingMode.FULL_TRANSLATABLE);
        } else {
            return null;
        }
    }
}

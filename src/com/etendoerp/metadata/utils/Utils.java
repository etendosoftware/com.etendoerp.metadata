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

package com.etendoerp.metadata.utils;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.openbravo.client.application.DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import java.io.BufferedReader;

import com.etendoerp.metadata.builders.ProcessDefinitionBuilder;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Utility class containing common methods and helpers for the metadata module.
 *
 * @author luuchorocha
 */
public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);
    private static final MessageFactory messageFactory = new ParameterizedMessageFactory();
    private static final Map<String, Integer> exceptionStatusMap = buildExceptionMap();
    private static final DataToJsonConverter converter = new DataToJsonConverter();

    /**
     * Builds a map that associates exception class names with their corresponding HTTP status codes.
     *
     * @return a map containing exception class names as keys and HTTP status codes as values
     */
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

    /**
     * Formats a message with the given parameters using the parameterized message factory.
     *
     * @param message the message template with placeholders
     * @param params the parameters to replace in the message template
     * @return the formatted message, or the original message if formatting fails
     */
    @SuppressWarnings("unused")
    public static String formatMessage(String message, Object... params) {
        try {
            return messageFactory.newMessage(message, params).getFormattedMessage();
        } catch (Exception e) {
            logger.error(e);

            return message;
        }
    }

    /**
     * Gets the referenced tab for a given property.
     *
     * @param referenced the property that references another entity
     * @return the tab associated with the referenced entity, or null if not found
     */
    public static Tab getReferencedTab(Property referenced) {
        OBDal dal = OBDal.getReadOnlyInstance();
        String tableId = referenced.getEntity().getTableId();
        Table table = dal.get(Table.class, tableId);

        return (Tab) dal.createCriteria(Tab.class).add(Restrictions.eq(Tab.PROPERTY_TABLE, table)).add(
                Restrictions.eq(Tab.PROPERTY_ACTIVE, true)).setMaxResults(1).uniqueResult();
    }

    /**
     * Evaluates display logic at server level for a given field.
     *
     * @param field the field to evaluate display logic for
     * @return true if the field should be displayed, false otherwise
     */
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

    /**
     * Gets the process definition for a field as a JSON object.
     *
     * @param field the field to get process information for
     * @return a JSON object containing process definition, or empty JSON if no process is associated
     * @throws JSONException if there's an error creating the JSON object
     */
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

    /**
     * Extracts request body data as a JSON object from HTTP request.
     *
     * @param request the HTTP servlet request
     * @return a JSON object containing the request data, or empty JSON if parsing fails
     */
    public static JSONObject getRequestData(HttpServletRequest request) {
        try {
            return new JSONObject(request.getReader().lines().reduce("", String::concat));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Sets up the OBContext with language and other context information from the HTTP request.
     *
     * @param request the HTTP servlet request containing context information
     */
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
            String warehouseId = null;
            if (context.getWarehouse() != null) {
                warehouseId = context.getWarehouse().getId();
            }

            OBContext.setOBContext(
                    context.getUser().getId(),
                    context.getRole().getId(),
                    context.getCurrentClient().getId(),
                    context.getCurrentOrganization().getId(),
                    context.getLanguage().getLanguage(),
                    warehouseId
            );

            OBContext.setOBContextInSession(request, OBContext.getOBContext());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Extracts language information from the HTTP request parameters or headers.
     *
     * @param request the HTTP servlet request
     * @return the language object if found and active, null otherwise
     */
    private static Language getLanguage(HttpServletRequest request) {
        String[] providedLanguages = { request.getParameter("language"), request.getHeader("language") };
        String languageCode = Arrays.stream(providedLanguages).filter(
                language -> language != null && !language.isEmpty()).findFirst().orElse(null);

        return (Language) OBDal.getInstance().createCriteria(Language.class).add(
                Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true)).add(
                Restrictions.eq(Language.PROPERTY_ACTIVE, true)).add(
                Restrictions.eq(Language.PROPERTY_LANGUAGE, languageCode)).setMaxResults(1).uniqueResult();
    }

    /**
     * Gets the appropriate HTTP status code for a given throwable.
     *
     * @param t the throwable to get status code for
     * @return the HTTP status code, defaulting to INTERNAL_SERVER_ERROR if not mapped
     */
    public static int getHttpStatusFor(Throwable t) {
        return Objects.requireNonNullElse(exceptionStatusMap.get(t.getClass().getName()), SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Converts a throwable to a JSON object containing error information.
     *
     * @param t the throwable to convert
     * @return a JSON object with error details
     */
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

    /**
     * Converts a BaseOBObject to a JSON object with full translatable data resolution.
     *
     * @param object the BaseOBObject to convert
     * @return a JSON object representation of the object, or null if object is null
     */
    public static JSONObject getJsonObject(BaseOBObject object) {
        if (object != null) {
            return converter.toJsonObject(object, DataResolvingMode.FULL_TRANSLATABLE);
        } else {
            return null;
        }
    }

    /**
     * Reads the HTTP request body and returns it as a string.
     *
     * @param request the HTTP servlet request
     * @return the request body as a string
     * @throws IOException if there's an error reading the request body
     */
    public static String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder buffer = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
        }
        return buffer.toString();
    }

    /**
     * Writes a JSON response to the HTTP response with the specified status code.
     *
     * @param response the HTTP servlet response
     * @param statusCode the HTTP status code to set
     * @param jsonContent the JSON content to write
     * @throws IOException if there's an error writing the response
     */
    public static void writeJsonResponse(HttpServletResponse response, int statusCode, String jsonContent)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonContent);
        response.getWriter().flush();
    }

    /**
     * Writes a JSON error response to the HTTP response with the specified status code and error message.
     *
     * @param response the HTTP servlet response
     * @param statusCode the HTTP status code to set
     * @param errorMessage the error message to include in the response
     * @throws IOException if there's an error writing the response
     */
    public static void writeJsonErrorResponse(HttpServletResponse response, int statusCode, String errorMessage)
            throws IOException {
        JSONObject errorJson = new JSONObject();
        try {
            errorJson.put("success", false);
            errorJson.put("error", errorMessage);
            errorJson.put("status", statusCode);
        } catch (JSONException e) {
            errorMessage = "{\"success\":false,\"error\":\"" + errorMessage.replace("\"", "\\\"") + "\"}";
            writeJsonResponse(response, statusCode, errorMessage);
            return;
        }
        writeJsonResponse(response, statusCode, errorJson.toString());
    }
}

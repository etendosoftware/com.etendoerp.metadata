package com.etendoerp.metadata;

import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.expression.OBScriptEngine;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.etendoerp.metadata.Constants.FALSE;
import static com.etendoerp.metadata.Constants.TRUE;

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
        String displayLogic = field.getDisplayLogicEvaluatedInTheServer();

        if (displayLogic == null) {
            return true;
        }

        try {
            DynamicExpressionParser parser = getDynamicExpressionParser(field, displayLogic);
            return (Boolean) OBScriptEngine.getInstance().eval(parser.getJSExpression());
        } catch (ScriptException e) {
            return true;
        }
    }

    private static DynamicExpressionParser getDynamicExpressionParser(Field field, String displayLogic) {
        return new DynamicExpressionParser(injectPreferences(displayLogic), field.getTab());
    }

    public static String injectPreferences(String displayLogic) {
        return DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic(displayLogic);
    }

    public static void setContext(HttpServletRequest request) {
        OBContext context = OBContext.getOBContext();
        Language language = getLanguage(request);

        if (context != null && language != null) {
            context.setLanguage(language);
        }

        OBContext.setOBContextInSession(request, context);
    }

    public static void setContentHeaders(HttpServletResponse response) {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    public static int getResponseStatus(Exception e) {
        String exceptionName = e.getClass().getSimpleName();

        switch (exceptionName) {
            case "OBSecurityException":
                return HttpStatus.SC_FORBIDDEN;
            case "UnauthorizedException":
                return HttpStatus.SC_UNAUTHORIZED;
            case "MethodNotAllowedException":
                return HttpStatus.SC_METHOD_NOT_ALLOWED;
            case "UnprocessableContentException":
                return HttpStatus.SC_UNPROCESSABLE_ENTITY;
            default:
                return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
    }

    public static String getValue(boolean value) {
        return value ? TRUE : FALSE;
    }
}

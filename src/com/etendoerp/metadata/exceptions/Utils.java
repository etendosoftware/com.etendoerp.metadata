package com.etendoerp.metadata.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * @author luuchorocha
 */
public class Utils {
    private static final Map<String, Integer> EXCEPTION_STATUS_MAP = new HashMap<>();
    private static final DataToJsonConverter CONVERTER = new DataToJsonConverter();

    static {
        EXCEPTION_STATUS_MAP.put(OBSecurityException.class.getName(), HttpStatus.SC_UNAUTHORIZED);
        EXCEPTION_STATUS_MAP.put(UnauthorizedException.class.getName(), HttpStatus.SC_UNAUTHORIZED);
        EXCEPTION_STATUS_MAP.put(MethodNotAllowedException.class.getName(), HttpStatus.SC_METHOD_NOT_ALLOWED);
        EXCEPTION_STATUS_MAP.put(UnprocessableContentException.class.getName(), HttpStatus.SC_UNPROCESSABLE_ENTITY);
        EXCEPTION_STATUS_MAP.put(NotFoundException.class.getName(), HttpStatus.SC_NOT_FOUND);
    }

    public static int getResponseStatus(Exception e) {
        Integer result = EXCEPTION_STATUS_MAP.get(e.getClass().getName());

        return Objects.requireNonNullElse(result, HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    public static JSONObject getJsonObject(BaseOBObject object) {
        if (object != null) {
            return CONVERTER.toJsonObject(object, DataResolvingMode.FULL_TRANSLATABLE);
        } else {
            return null;
        }
    }
}

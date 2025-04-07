package com.etendoerp.metadata.exceptions;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBSecurityException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.openbravo.service.json.JsonUtils.convertExceptionToJson;

/**
 * @author luuchorocha
 */
public class Utils {
    private static final Logger log4j = LogManager.getLogger();
    private static final Map<Class<? extends Exception>, Integer> EXCEPTION_STATUS_MAP = new HashMap<>();

    static {
        EXCEPTION_STATUS_MAP.put(OBSecurityException.class, HttpStatus.SC_UNAUTHORIZED);
        EXCEPTION_STATUS_MAP.put(UnauthorizedException.class, HttpStatus.SC_UNAUTHORIZED);
        EXCEPTION_STATUS_MAP.put(MethodNotAllowedException.class, HttpStatus.SC_METHOD_NOT_ALLOWED);
        EXCEPTION_STATUS_MAP.put(UnprocessableContentException.class, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        EXCEPTION_STATUS_MAP.put(NotFoundException.class, HttpStatus.SC_NOT_FOUND);
    }

    private static int getResponseStatus(Exception e) {
        return Optional.ofNullable(findMappedStatus(e.getClass())).orElse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private static Integer findMappedStatus(Class<?> exceptionClass) {
        for (Map.Entry<Class<? extends Exception>, Integer> entry : EXCEPTION_STATUS_MAP.entrySet()) {
            if (entry.getKey().isAssignableFrom(exceptionClass)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public static void handleException(Exception e, HttpServletResponse response) throws IOException {
        log4j.error(e.getMessage(), e);
        response.setStatus(getResponseStatus(e));
        response.getWriter().write(convertExceptionToJson(e));
    }

}

package com.etendoerp.metadata.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBSecurityException;

/**
 * @author luuchorocha
 */
public class Utils {
    private static final Logger log4j = LogManager.getLogger(Utils.class);
    private static final Map<Class<? extends Exception>, Integer> EXCEPTION_STATUS_MAP = new HashMap<>();

    static {
        EXCEPTION_STATUS_MAP.put(OBSecurityException.class, HttpStatus.SC_UNAUTHORIZED);
        EXCEPTION_STATUS_MAP.put(UnauthorizedException.class, HttpStatus.SC_UNAUTHORIZED);
        EXCEPTION_STATUS_MAP.put(MethodNotAllowedException.class, HttpStatus.SC_METHOD_NOT_ALLOWED);
        EXCEPTION_STATUS_MAP.put(UnprocessableContentException.class, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        EXCEPTION_STATUS_MAP.put(NotFoundException.class, HttpStatus.SC_NOT_FOUND);
    }

    public static int getResponseStatus(Exception e) {
        return Optional.of(EXCEPTION_STATUS_MAP.get(e.getClass())).orElse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}

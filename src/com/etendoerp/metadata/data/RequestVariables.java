package com.etendoerp.metadata.data;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openbravo.base.secureApp.VariablesSecureApp;

import com.etendoerp.metadata.http.HttpServletRequestWrapper;

/**
 * @author luuchorocha
 */
public class RequestVariables extends VariablesSecureApp {
    private final Map<String, Object> casedSessionAttributes = new HashMap<>();

    public RequestVariables(HttpServletRequest request) {
        super(HttpServletRequestWrapper.wrap(request));
    }

    @Override
    public void setSessionValue(String attribute, String value) {
        super.setSessionValue(attribute, value);
        casedSessionAttributes.put(attribute, value);
    }

    public Map<String, Object> getCasedSessionAttributes() {
        return casedSessionAttributes;
    }
}

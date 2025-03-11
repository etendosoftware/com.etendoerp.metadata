package com.etendoerp.metadata.data;

import org.openbravo.base.secureApp.VariablesSecureApp;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public class RequestVariables extends VariablesSecureApp {
    private final Map<String, Object> casedSessionAttributes = new HashMap<>();

    public RequestVariables(HttpServletRequest request) {
        super(request);
    }

    public RequestVariables(HttpServletRequest request, boolean f) {
        super(request, f);
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

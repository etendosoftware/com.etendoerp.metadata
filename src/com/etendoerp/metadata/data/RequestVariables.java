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

package com.etendoerp.metadata.data;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openbravo.base.secureApp.VariablesSecureApp;

/**
 * @author luuchorocha
 */
public class RequestVariables extends VariablesSecureApp {
    private final Map<String, Object> casedSessionAttributes = new HashMap<>();

    public RequestVariables(HttpServletRequest request) {
        super(request);
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

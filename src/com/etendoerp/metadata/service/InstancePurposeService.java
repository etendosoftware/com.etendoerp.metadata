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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.window.ApplicationDictionaryCachedStructures;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_process.HeartbeatProcess;
import org.openbravo.model.ad.system.SystemInformation;

import com.etendoerp.metadata.exceptions.ForbiddenException;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Instance Purpose Configuration, the new UI counterpart of Classic's {@code InstancePurpose} pop-up.
 *
 * <p>The purpose of the instance (Production, Development, Testing or Evaluation) is a global value
 * stored in {@code AD_System_Info.instance_purpose}, so it is configured once per instance and not per user.
 *
 * <ul>
 *   <li>{@code GET} tells whether the configuration modal has to be shown to the current session (a
 *       system-level role and no purpose configured yet, unless the instance is already activated, in
 *       which case Classic's own check adopts the purpose of the activation key) and returns the
 *       current purpose and the accepted values.</li>
 *   <li>{@code POST} validates and stores the chosen purpose, with the same side effect Classic has
 *       when an instance is declared a Production one.</li>
 * </ul>
 *
 * <p>Only roles whose user level is exactly system ({@code S}) can read the "required" flag as true or
 * change the purpose. The check relies on the role's level and not on its name or id, so renamed or
 * custom administrative roles keep working.
 */
public class InstancePurposeService extends MetadataService {

    static final String PURPOSE = "purpose";
    static final String REQUIRED = "required";
    static final String OPTIONS = "options";
    static final String PRODUCTION = "P";
    static final List<String> PURPOSES = Collections.unmodifiableList(Arrays.asList(PRODUCTION, "D", "T", "E"));

    private static final String SYSTEM_INFO_ID = "0";
    private static final String SYSTEM_LEVEL = "S";

    /**
     * Creates the service.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public InstancePurposeService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        OBContext.setAdminMode(true);
        try {
            switch (getRequest().getMethod()) {
                case "GET":
                    handleGet();
                    break;
                case "POST":
                    handlePost();
                    break;
                default:
                    throw new MethodNotAllowedException();
            }
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleGet() throws IOException, JSONException {
        boolean required = isAdministrator(OBContext.getOBContext().getUserLevel())
            && HeartbeatProcess.isShowInstancePurposeRequired();
        write(buildResponse(required));
    }

    private void handlePost() throws IOException, JSONException {
        if (!isAdministrator(OBContext.getOBContext().getUserLevel())) {
            throw new ForbiddenException("Only a system administrator role can set the instance purpose");
        }

        String purpose = readBody().optString(PURPOSE, "");
        if (!PURPOSES.contains(purpose)) {
            throw new UnprocessableContentException("Invalid instance purpose: " + purpose);
        }

        SystemInformation systemInfo = OBDal.getInstance().get(SystemInformation.class, SYSTEM_INFO_ID);
        if (systemInfo == null) {
            throw new NotFoundException();
        }
        systemInfo.setInstancePurpose(purpose);
        OBDal.getInstance().save(systemInfo);
        OBDal.getInstance().flush();

        if (PRODUCTION.equals(purpose)) {
            WeldUtils.getInstanceFromStaticBeanManager(ApplicationDictionaryCachedStructures.class)
                .setNotInDevelopment();
        }

        write(buildResponse(false));
    }

    private JSONObject buildResponse(boolean required) throws JSONException {
        SystemInformation systemInfo = OBDal.getInstance().get(SystemInformation.class, SYSTEM_INFO_ID);
        String current = systemInfo == null ? null : systemInfo.getInstancePurpose();

        JSONArray options = new JSONArray();
        for (String option : PURPOSES) {
            options.put(option);
        }
        return new JSONObject()
            .put(REQUIRED, required)
            .put(PURPOSE, current == null || current.isEmpty() ? JSONObject.NULL : current)
            .put(OPTIONS, options);
    }

    /**
     * Whether a role of the given user level is a system-level (administrator) one.
     *
     * @param userLevel the role's user level ({@code S}, {@code C}, {@code O} or a combination)
     * @return true only for the system-only level
     */
    static boolean isAdministrator(String userLevel) {
        return SYSTEM_LEVEL.equals(userLevel == null ? null : userLevel.trim());
    }

    private JSONObject readBody() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = getRequest().getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        try {
            return new JSONObject(sb.toString());
        } catch (JSONException e) {
            throw new UnprocessableContentException("Invalid JSON body: " + e.getMessage());
        }
    }
}

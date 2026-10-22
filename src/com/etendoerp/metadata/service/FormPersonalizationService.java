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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.ApplicationUtils;
import org.openbravo.client.application.UIPersonalization;
import org.openbravo.client.application.personalization.PersonalizationHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.exceptions.ForbiddenException;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Form personalization of a tab (field order, visibility, groups, columns and initial focus), the new
 * UI counterpart of Classic's "Personalize Form".
 *
 * <p>It reads and writes the same {@code OBUIAPP_UIPersonalization} records Classic does, through
 * Classic's own {@link PersonalizationHandler}, so a personalization created in one UI is applied in the
 * other and the precedence between levels is the one Classic already resolves (client, then
 * organization depth, then user, then role).
 *
 * <ul>
 *   <li>{@code GET ?tab=} returns the personalization that applies to the current session, if any, and
 *       the levels the session is allowed to save at.</li>
 *   <li>{@code POST} stores the form of a tab at one level ({@code USER}, {@code ROLE},
 *       {@code ORGANIZATION}, {@code CLIENT} or {@code SYSTEM}). Classic only checks who may edit shared
 *       levels on the client; here it is enforced on the server too.</li>
 *   <li>{@code DELETE ?id=} discards a personalization the session may edit, which restores the layout
 *       defined in the application dictionary.</li>
 * </ul>
 */
public class FormPersonalizationService extends MetadataService {

    static final String TAB = "tab";
    static final String ID = "id";
    static final String SCOPE = "scope";
    static final String FORM = "form";
    static final String FIELDS = "fields";
    static final String ROLE = "role";
    static final String ORGANIZATION = "organization";

    static final String SCOPE_USER = "USER";
    static final String SCOPE_ROLE = "ROLE";
    static final String SCOPE_ORGANIZATION = "ORGANIZATION";
    static final String SCOPE_CLIENT = "CLIENT";
    static final String SCOPE_SYSTEM = "SYSTEM";

    private static final String SYSTEM_ID = "0";
    private static final String FORM_TYPE = "Form";

    /**
     * Creates the service.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public FormPersonalizationService(HttpServletRequest request, HttpServletResponse response) {
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
                case "DELETE":
                    handleDelete();
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
        Tab tab = requireTab(getRequest().getParameter(TAB));
        write(buildResponse(tab, personalizationHandler().getPersonalizationForTab(tab)));
    }

    private void handlePost() throws IOException, JSONException {
        JSONObject body = readBody();
        Tab tab = requireTab(body.optString(TAB, getRequest().getParameter(TAB)));
        JSONObject form = body.optJSONObject(FORM);
        if (form == null || form.optJSONArray(FIELDS) == null) {
            throw new UnprocessableContentException("The form personalization must have a list of fields");
        }

        Levels levels = resolveLevels(body.optString(SCOPE, SCOPE_USER).toUpperCase(Locale.ROOT), body);
        UIPersonalization saved = personalizationHandler().storePersonalization(null, levels.clientId,
            levels.orgId, levels.roleId, levels.userId, tab.getId(), null, FORM, form.toString(), false);
        OBDal.getInstance().flush();

        write(buildResponse(tab, saved));
    }

    private void handleDelete() throws IOException, JSONException {
        String id = getRequest().getParameter(ID);
        if (id == null || id.isEmpty()) {
            throw new UnprocessableContentException("The id of the personalization is required");
        }

        UIPersonalization personalization = OBDal.getInstance().get(UIPersonalization.class, id);
        if (personalization != null) {
            if (!FORM_TYPE.equals(personalization.getType())) {
                throw new UnprocessableContentException("Not a form personalization: " + id);
            }
            if (!canEdit(personalization)) {
                throw new ForbiddenException("Not authorized to discard this form personalization");
            }
            OBDal.getInstance().remove(personalization);
            OBDal.getInstance().flush();
        }
        // Like Classic, discarding something that is already gone is a success.
        write(new JSONObject().put("result", "success"));
    }

    private Tab requireTab(String tabId) {
        if (tabId == null || tabId.isEmpty()) {
            throw new UnprocessableContentException("The tab is required");
        }
        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        if (tab == null) {
            throw new UnprocessableContentException("Unknown tab: " + tabId);
        }
        return tab;
    }

    private PersonalizationHandler personalizationHandler() {
        return WeldUtils.getInstanceFromStaticBeanManager(PersonalizationHandler.class);
    }

    // --- Levels ---

    /** The visibility levels a personalization is stored at. A null level means "all". */
    private static final class Levels {
        private final String clientId;
        private final String orgId;
        private final String roleId;
        private final String userId;

        private Levels(String clientId, String orgId, String roleId, String userId) {
            this.clientId = clientId;
            this.orgId = orgId;
            this.roleId = roleId;
            this.userId = userId;
        }
    }

    private Levels resolveLevels(String scope, JSONObject body) {
        OBContext ctx = OBContext.getOBContext();
        String clientId = ctx.getCurrentClient().getId();

        switch (scope) {
            case SCOPE_USER:
                // Like Classic's user level: bound to the current client, organization, role and user.
                return new Levels(clientId, ctx.getCurrentOrganization().getId(), ctx.getRole().getId(),
                    ctx.getUser().getId());
            case SCOPE_ROLE: {
                String roleId = body.optString(ROLE, ctx.getRole().getId());
                if (!isAdminOfRole(roleId)) {
                    throw new ForbiddenException("Not authorized to personalize forms for the role " + roleId);
                }
                return new Levels(clientId, null, roleId, null);
            }
            case SCOPE_ORGANIZATION: {
                String orgId = body.optString(ORGANIZATION, ctx.getCurrentOrganization().getId());
                if (!isAdminOfOrganization(orgId)) {
                    throw new ForbiddenException("Not authorized to personalize forms for the organization " + orgId);
                }
                return new Levels(clientId, orgId, null, null);
            }
            case SCOPE_CLIENT:
                if (!ApplicationUtils.isClientAdmin()) {
                    throw new ForbiddenException("Not authorized to personalize forms for the whole client");
                }
                return new Levels(clientId, null, null, null);
            case SCOPE_SYSTEM:
                if (!SYSTEM_ID.equals(ctx.getRole().getId())) {
                    throw new ForbiddenException("Not authorized to personalize forms for the whole system");
                }
                return new Levels(null, null, null, null);
            default:
                throw new UnprocessableContentException("Invalid personalization scope: " + scope);
        }
    }

    private boolean isAdminOfRole(String roleId) {
        for (UserRoles userRole : ApplicationUtils.getAdminRoles()) {
            if (userRole.getRole().getId().equals(roleId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdminOfOrganization(String orgId) {
        for (RoleOrganization roleOrg : ApplicationUtils.getAdminOrgs()) {
            if (roleOrg.getOrganization().getId().equals(orgId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The scope a stored personalization is visible at, derived from its levels.
     *
     * @param personalization the stored personalization
     * @return the most specific scope it is bound to
     */
    static String deriveScope(UIPersonalization personalization) {
        if (personalization.getUser() != null) {
            return SCOPE_USER;
        }
        if (personalization.getVisibleAtRole() != null) {
            return SCOPE_ROLE;
        }
        if (personalization.getVisibleAtOrganization() != null) {
            return SCOPE_ORGANIZATION;
        }
        if (personalization.getVisibleAtClient() != null
            && !SYSTEM_ID.equals(personalization.getVisibleAtClient().getId())) {
            return SCOPE_CLIENT;
        }
        return SCOPE_SYSTEM;
    }

    private boolean canEdit(UIPersonalization personalization) {
        OBContext ctx = OBContext.getOBContext();
        switch (deriveScope(personalization)) {
            case SCOPE_USER:
                return personalization.getUser().getId().equals(ctx.getUser().getId());
            case SCOPE_ROLE:
                return isAdminOfRole(personalization.getVisibleAtRole().getId());
            case SCOPE_ORGANIZATION:
                return isAdminOfOrganization(personalization.getVisibleAtOrganization().getId());
            case SCOPE_CLIENT:
                return ApplicationUtils.isClientAdmin();
            default:
                return SYSTEM_ID.equals(ctx.getRole().getId());
        }
    }

    // --- Response ---

    private JSONObject buildResponse(Tab tab, UIPersonalization personalization) throws JSONException {
        return new JSONObject()
            .put(TAB, tab.getId())
            .put("personalization", toJson(personalization))
            .put("permissions", permissions());
    }

    private Object toJson(UIPersonalization personalization) throws JSONException {
        if (personalization == null || personalization.getValue() == null) {
            return JSONObject.NULL;
        }
        JSONObject form = new JSONObject(personalization.getValue()).optJSONObject(FORM);
        if (form == null) {
            return JSONObject.NULL;
        }
        return new JSONObject()
            .put(ID, personalization.getId())
            .put(SCOPE, deriveScope(personalization))
            .put(FORM, form)
            .put("canEdit", canEdit(personalization));
    }

    /** The levels the current session may save at, with the roles and organizations it administers. */
    private JSONObject permissions() throws JSONException {
        OBContext ctx = OBContext.getOBContext();
        List<String> scopes = new ArrayList<>();
        scopes.add(SCOPE_USER);

        JSONArray roles = new JSONArray();
        for (UserRoles userRole : ApplicationUtils.getAdminRoles()) {
            roles.put(new JSONObject().put(ID, userRole.getRole().getId()).put("name", userRole.getRole().getName()));
        }
        if (roles.length() > 0) {
            scopes.add(SCOPE_ROLE);
        }

        JSONArray organizations = new JSONArray();
        for (RoleOrganization roleOrg : ApplicationUtils.getAdminOrgs()) {
            organizations.put(new JSONObject().put(ID, roleOrg.getOrganization().getId())
                .put("name", roleOrg.getOrganization().getName()));
        }
        if (organizations.length() > 0) {
            scopes.add(SCOPE_ORGANIZATION);
        }
        if (ApplicationUtils.isClientAdmin()) {
            scopes.add(SCOPE_CLIENT);
        }
        if (SYSTEM_ID.equals(ctx.getRole().getId())) {
            scopes.add(SCOPE_SYSTEM);
        }

        return new JSONObject()
            .put("scopes", new JSONArray(scopes))
            .put("roles", roles)
            .put("organizations", organizations);
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

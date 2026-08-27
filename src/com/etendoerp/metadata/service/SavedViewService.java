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

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.data.SavedView;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static com.etendoerp.metadata.utils.Constants.SAVED_VIEW_PATH;

/**
 * Service for managing Saved Views.
 * Runs in admin mode to bypass entity-level security, but enforces its own
 * visibility and write-authorization rules based on the view's scope
 * (USER, ROLE, ORGANIZATION, CLIENT or SYSTEM) and the current OBContext.
 * <p>
 * Scope is derived from which of {@code user}/{@code role}/{@code organization}/
 * {@code client} are populated on the row, following the same System(client 0)
 * &gt; Client &gt; Organization convention used across the rest of the dictionary,
 * with Role added as an extra, more specific level between Organization and User.
 * The effective "default view" for a tab is resolved with USER &gt; ROLE &gt;
 * ORGANIZATION &gt; CLIENT &gt; SYSTEM precedence (most specific wins).
 */
public class SavedViewService extends MetadataService {

    private static final String FIELD_ISDEFAULT = "isdefault";
    private static final String FIELD_FILTERCLAUSE = "filterclause";
    private static final String FIELD_GRIDCONFIGURATION = "gridconfiguration";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_RESPONSE = "response";
    private static final String FIELD_TAB = "tab";
    private static final String FIELD_USER = "user";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_ORGANIZATION = "organization";
    private static final String FIELD_SCOPE = "scope";
    private static final String FIELD_EDITABLE = "editable";

    private static final String SCOPE_USER = "USER";
    private static final String SCOPE_ROLE = "ROLE";
    private static final String SCOPE_ORGANIZATION = "ORGANIZATION";
    private static final String SCOPE_CLIENT = "CLIENT";
    private static final String SCOPE_SYSTEM = "SYSTEM";

    /** Etendo's standard id for the System client / "*" (all) organization. */
    private static final String SYSTEM_ID = "0";

    /**
     * Constructs a new SavedViewService.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public SavedViewService(HttpServletRequest request, HttpServletResponse response) {
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
                case "PUT":
                    handlePut();
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
            throw new IOException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleGet() throws Exception {
        OBContext ctx = OBContext.getOBContext();
        String id = extractId();
        if (id != null) {
            SavedView view = OBDal.getInstance().get(SavedView.class, id);
            if (view == null || !isVisible(view, ctx)) {
                throw new NotFoundException();
            }
            write(wrapSingle(toJSON(view, ctx)));
            return;
        }

        String tabId = getRequest().getParameter(FIELD_TAB);
        String isdefaultStr = getRequest().getParameter(FIELD_ISDEFAULT);
        boolean wantsDefault = isdefaultStr != null && Boolean.parseBoolean(isdefaultStr);

        JSONArray data = new JSONArray();

        if (wantsDefault && tabId != null && !tabId.isEmpty()) {
            SavedView effective = resolveEffectiveDefault(tabId, ctx);
            if (effective != null) {
                data.put(toJSON(effective, ctx));
            }
            write(wrapList(data));
            return;
        }

        OBCriteria<SavedView> crit = OBDal.getInstance().createCriteria(SavedView.class);
        if (tabId != null && !tabId.isEmpty()) {
            crit.add(Restrictions.eq(SavedView.PROPERTY_TAB + ".id", tabId));
        }
        if (isdefaultStr != null && !isdefaultStr.isEmpty()) {
            crit.add(Restrictions.eq(SavedView.PROPERTY_ISDEFAULT, Boolean.parseBoolean(isdefaultStr)));
        }
        crit.add(buildVisibilityRestriction(ctx));

        List<SavedView> list = crit.list();
        for (SavedView view : list) {
            data.put(toJSON(view, ctx));
        }
        write(wrapList(data));
    }

    private void handlePost() throws Exception {
        JSONObject body = readBody();
        OBContext ctx = OBContext.getOBContext();

        String scope = body.optString(FIELD_SCOPE, SCOPE_USER).toUpperCase();
        if (!canManageScope(scope, ctx.getUserLevel())) {
            throw new UnauthorizedException("Not authorized to create a " + scope + "-scoped view");
        }

        SavedView view = OBProvider.getInstance().get(SavedView.class);
        applyScope(view, scope, body, ctx);
        applyBody(view, body);

        OBDal.getInstance().save(view);
        if (Boolean.TRUE.equals(view.isDefault())) {
            clearSiblingDefaults(view);
        }
        OBDal.getInstance().flush();

        write(wrapSingle(toJSON(view, ctx)));
    }

    private void handlePut() throws Exception {
        String id = extractId();
        if (id == null) {
            throw new NotFoundException();
        }

        SavedView view = OBDal.getInstance().get(SavedView.class, id);
        if (view == null) {
            throw new NotFoundException();
        }

        OBContext ctx = OBContext.getOBContext();
        checkWriteAccess(view, ctx);

        applyBody(view, readBody());
        OBDal.getInstance().save(view);
        if (Boolean.TRUE.equals(view.isDefault())) {
            clearSiblingDefaults(view);
        }
        OBDal.getInstance().flush();

        write(wrapSingle(toJSON(view, ctx)));
    }

    private void handleDelete() throws Exception {
        String id = extractId();
        if (id == null) {
            throw new NotFoundException();
        }

        SavedView view = OBDal.getInstance().get(SavedView.class, id);
        if (view == null) {
            throw new NotFoundException();
        }

        checkWriteAccess(view, OBContext.getOBContext());

        OBDal.getInstance().remove(view);
        OBDal.getInstance().flush();

        write(new JSONObject().put(FIELD_RESPONSE, new JSONObject().put(FIELD_STATUS, 0)));
    }

    /**
     * Assigns the owner columns (user/role/organization/client) for a newly created view
     * according to the requested scope. Only USER scope is always allowed; the others are
     * gated by {@link #canManageScope(String, String)} in {@link #handlePost()} before this runs.
     */
    private void applyScope(SavedView view, String scope, JSONObject body, OBContext ctx) throws Exception {
        view.setUser(null);
        view.setRole(null);

        Client currentClient = OBDal.getInstance().get(Client.class, ctx.getCurrentClient().getId());
        Organization currentOrg = OBDal.getInstance().get(Organization.class, ctx.getCurrentOrganization().getId());

        switch (scope) {
            case SCOPE_ROLE: {
                String roleId = body.optString(FIELD_ROLE, ctx.getRole().getId());
                Role role = OBDal.getInstance().get(Role.class, roleId);
                if (role == null) {
                    throw new UnprocessableContentException("Unknown role: " + roleId);
                }
                view.setRole(role);
                view.setClient(currentClient);
                view.setOrganization(currentOrg);
                break;
            }
            case SCOPE_ORGANIZATION: {
                String orgId = body.optString(FIELD_ORGANIZATION, currentOrg.getId());
                Organization org = OBDal.getInstance().get(Organization.class, orgId);
                if (org == null || SYSTEM_ID.equals(org.getId())) {
                    throw new UnprocessableContentException("Unknown organization: " + orgId);
                }
                view.setClient(currentClient);
                view.setOrganization(org);
                break;
            }
            case SCOPE_CLIENT:
                view.setClient(currentClient);
                view.setOrganization(OBDal.getInstance().get(Organization.class, SYSTEM_ID));
                break;
            case SCOPE_SYSTEM:
                view.setClient(OBDal.getInstance().get(Client.class, SYSTEM_ID));
                view.setOrganization(OBDal.getInstance().get(Organization.class, SYSTEM_ID));
                break;
            case SCOPE_USER:
            default:
                view.setUser(OBDal.getInstance().get(User.class, ctx.getUser().getId()));
                view.setClient(currentClient);
                view.setOrganization(currentOrg);
                break;
        }
    }

    /**
     * Derives the effective scope of a persisted view from which owner columns are populated.
     */
    private String deriveScope(SavedView view) {
        if (view.getUser() != null) {
            return SCOPE_USER;
        }
        if (view.getRole() != null) {
            return SCOPE_ROLE;
        }
        if (!SYSTEM_ID.equals(view.getOrganization().getId())) {
            return SCOPE_ORGANIZATION;
        }
        if (!SYSTEM_ID.equals(view.getClient().getId())) {
            return SCOPE_CLIENT;
        }
        return SCOPE_SYSTEM;
    }

    /**
     * Only administrator-tier roles may define a shared (non-USER) view: a plain business
     * role in Etendo carries userLevel "O" only, while Client/System administrator roles
     * additionally carry "C"/"S" — that distinction is reused here to gate who can define
     * shared defaults, instead of introducing a new permission concept.
     */
    private boolean canManageScope(String scope, String userLevel) {
        String level = userLevel == null ? "" : userLevel;
        switch (scope) {
            case SCOPE_SYSTEM:
                return level.contains("S");
            case SCOPE_CLIENT:
            case SCOPE_ORGANIZATION:
            case SCOPE_ROLE:
                return level.contains("C") || level.contains("S");
            default:
                return true;
        }
    }

    private boolean isVisible(SavedView view, OBContext ctx) {
        if (view.getUser() != null) {
            return view.getUser().getId().equals(ctx.getUser().getId());
        }
        if (view.getRole() != null) {
            return view.getRole().getId().equals(ctx.getRole().getId());
        }
        String orgId = view.getOrganization().getId();
        if (!SYSTEM_ID.equals(orgId)) {
            return orgId.equals(ctx.getCurrentOrganization().getId());
        }
        String clientId = view.getClient().getId();
        if (!SYSTEM_ID.equals(clientId)) {
            return clientId.equals(ctx.getCurrentClient().getId());
        }
        return true;
    }

    private void checkWriteAccess(SavedView view, OBContext ctx) {
        boolean isOwner = view.getUser() != null && view.getUser().getId().equals(ctx.getUser().getId());
        if (isOwner) {
            return;
        }
        String scope = deriveScope(view);
        if (!canManageScope(scope, ctx.getUserLevel())) {
            throw new UnauthorizedException("Not authorized to modify this " + scope + "-scoped view");
        }
    }

    /**
     * Builds the "own views OR every shared view visible to the current user" restriction
     * used by the generic list endpoint.
     */
    private Disjunction buildVisibilityRestriction(OBContext ctx) {
        Disjunction visibility = Restrictions.disjunction();
        visibility.add(Restrictions.eq(SavedView.PROPERTY_USER + ".id", ctx.getUser().getId()));
        visibility.add(Restrictions.conjunction()
            .add(Restrictions.isNull(SavedView.PROPERTY_USER))
            .add(Restrictions.eq(SavedView.PROPERTY_ROLE + ".id", ctx.getRole().getId())));
        visibility.add(Restrictions.conjunction()
            .add(Restrictions.isNull(SavedView.PROPERTY_USER))
            .add(Restrictions.isNull(SavedView.PROPERTY_ROLE))
            .add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", ctx.getCurrentOrganization().getId()))
            .add(Restrictions.ne(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID)));
        visibility.add(Restrictions.conjunction()
            .add(Restrictions.isNull(SavedView.PROPERTY_USER))
            .add(Restrictions.isNull(SavedView.PROPERTY_ROLE))
            .add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID))
            .add(Restrictions.eq(SavedView.PROPERTY_CLIENT + ".id", ctx.getCurrentClient().getId()))
            .add(Restrictions.ne(SavedView.PROPERTY_CLIENT + ".id", SYSTEM_ID)));
        visibility.add(Restrictions.conjunction()
            .add(Restrictions.isNull(SavedView.PROPERTY_USER))
            .add(Restrictions.isNull(SavedView.PROPERTY_ROLE))
            .add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID))
            .add(Restrictions.eq(SavedView.PROPERTY_CLIENT + ".id", SYSTEM_ID)));
        return visibility;
    }

    /**
     * Resolves the single "effective" default view for a tab, trying USER, then ROLE, then
     * ORGANIZATION, then CLIENT, then SYSTEM scope in order and returning the first match.
     */
    private SavedView resolveEffectiveDefault(String tabId, OBContext ctx) {
        SavedView userView = findDefault(tabId, crit ->
            crit.add(Restrictions.eq(SavedView.PROPERTY_USER + ".id", ctx.getUser().getId())));
        if (userView != null) {
            return userView;
        }

        SavedView roleView = findDefault(tabId, crit -> {
            crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
            crit.add(Restrictions.eq(SavedView.PROPERTY_ROLE + ".id", ctx.getRole().getId()));
        });
        if (roleView != null) {
            return roleView;
        }

        SavedView orgView = findDefault(tabId, crit -> {
            crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
            crit.add(Restrictions.isNull(SavedView.PROPERTY_ROLE));
            crit.add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", ctx.getCurrentOrganization().getId()));
            crit.add(Restrictions.ne(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID));
        });
        if (orgView != null) {
            return orgView;
        }

        SavedView clientView = findDefault(tabId, crit -> {
            crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
            crit.add(Restrictions.isNull(SavedView.PROPERTY_ROLE));
            crit.add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID));
            crit.add(Restrictions.eq(SavedView.PROPERTY_CLIENT + ".id", ctx.getCurrentClient().getId()));
            crit.add(Restrictions.ne(SavedView.PROPERTY_CLIENT + ".id", SYSTEM_ID));
        });
        if (clientView != null) {
            return clientView;
        }

        return findDefault(tabId, crit -> {
            crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
            crit.add(Restrictions.isNull(SavedView.PROPERTY_ROLE));
            crit.add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID));
            crit.add(Restrictions.eq(SavedView.PROPERTY_CLIENT + ".id", SYSTEM_ID));
        });
    }

    private SavedView findDefault(String tabId, Consumer<OBCriteria<SavedView>> scopeFilter) {
        OBCriteria<SavedView> crit = OBDal.getInstance().createCriteria(SavedView.class);
        crit.add(Restrictions.eq(SavedView.PROPERTY_TAB + ".id", tabId));
        crit.add(Restrictions.eq(SavedView.PROPERTY_ISDEFAULT, true));
        crit.add(Restrictions.eq(SavedView.PROPERTY_ACTIVE, true));
        scopeFilter.accept(crit);
        crit.setMaxResults(1);

        List<SavedView> results = crit.list();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Unsets {@code isdefault} on every other active view sharing the same tab and scope
     * target as {@code view}, so at most one default exists per (tab, scope target) — done
     * server-side, atomically with the save that follows, instead of two separate client PUTs.
     */
    private void clearSiblingDefaults(SavedView view) {
        OBCriteria<SavedView> crit = OBDal.getInstance().createCriteria(SavedView.class);
        crit.add(Restrictions.eq(SavedView.PROPERTY_TAB + ".id", view.getTab().getId()));
        crit.add(Restrictions.eq(SavedView.PROPERTY_ISDEFAULT, true));
        crit.add(Restrictions.ne(SavedView.PROPERTY_ID, view.getId()));

        String scope = deriveScope(view);
        switch (scope) {
            case SCOPE_USER:
                crit.add(Restrictions.eq(SavedView.PROPERTY_USER + ".id", view.getUser().getId()));
                break;
            case SCOPE_ROLE:
                crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
                crit.add(Restrictions.eq(SavedView.PROPERTY_ROLE + ".id", view.getRole().getId()));
                break;
            case SCOPE_ORGANIZATION:
                crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
                crit.add(Restrictions.isNull(SavedView.PROPERTY_ROLE));
                crit.add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", view.getOrganization().getId()));
                break;
            case SCOPE_CLIENT:
                crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
                crit.add(Restrictions.isNull(SavedView.PROPERTY_ROLE));
                crit.add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID));
                crit.add(Restrictions.eq(SavedView.PROPERTY_CLIENT + ".id", view.getClient().getId()));
                break;
            case SCOPE_SYSTEM:
            default:
                crit.add(Restrictions.isNull(SavedView.PROPERTY_USER));
                crit.add(Restrictions.isNull(SavedView.PROPERTY_ROLE));
                crit.add(Restrictions.eq(SavedView.PROPERTY_ORGANIZATION + ".id", SYSTEM_ID));
                crit.add(Restrictions.eq(SavedView.PROPERTY_CLIENT + ".id", SYSTEM_ID));
                break;
        }

        for (SavedView sibling : crit.list()) {
            sibling.setDefault(false);
            OBDal.getInstance().save(sibling);
        }
    }

    private void applyBody(SavedView view, JSONObject body) throws Exception {
        if (body.has("name")) {
            view.setName(body.getString("name"));
        }
        if (body.has(FIELD_TAB)) {
            view.setTab(OBDal.getInstance().get(Tab.class, body.getString(FIELD_TAB)));
        }
        if (body.has(FIELD_ISDEFAULT)) {
            view.setDefault(body.getBoolean(FIELD_ISDEFAULT));
        }
        if (body.has(FIELD_FILTERCLAUSE)) {
            String val = body.optString(FIELD_FILTERCLAUSE, null);
            view.setFilterclause("null".equals(val) ? null : val);
        }
        if (body.has(FIELD_GRIDCONFIGURATION)) {
            String val = body.optString(FIELD_GRIDCONFIGURATION, null);
            view.setGridconfiguration("null".equals(val) ? null : val);
        }
    }

    private JSONObject toJSON(SavedView view, OBContext ctx) throws Exception {
        String scope = deriveScope(view);
        boolean isOwner = view.getUser() != null && view.getUser().getId().equals(ctx.getUser().getId());
        boolean editable = isOwner || canManageScope(scope, ctx.getUserLevel());

        JSONObject json = new JSONObject()
            .put("id", view.getId())
            .put("name", view.getName())
            .put(FIELD_TAB, view.getTab() != null ? view.getTab().getId() : JSONObject.NULL)
            .put(FIELD_USER, view.getUser() != null ? view.getUser().getId() : JSONObject.NULL)
            .put(FIELD_ROLE, view.getRole() != null ? view.getRole().getId() : JSONObject.NULL)
            .put(FIELD_SCOPE, scope)
            .put(FIELD_EDITABLE, editable)
            .put(FIELD_ISDEFAULT, view.isDefault())
            .put("active", view.isActive());

        json.put(FIELD_FILTERCLAUSE,
            view.getFilterclause() != null ? view.getFilterclause() : JSONObject.NULL);
        json.put(FIELD_GRIDCONFIGURATION,
            view.getGridconfiguration() != null ? view.getGridconfiguration() : JSONObject.NULL);

        return json;
    }

    private JSONObject wrapSingle(JSONObject data) throws Exception {
        return new JSONObject().put(FIELD_RESPONSE,
            new JSONObject().put(FIELD_STATUS, 0).put("data", data));
    }

    private JSONObject wrapList(JSONArray data) throws Exception {
        return new JSONObject().put(FIELD_RESPONSE,
            new JSONObject()
                .put(FIELD_STATUS, 0)
                .put("data", data)
                .put("startRow", 0)
                .put("endRow", data.length() - 1)
                .put("totalRows", data.length()));
    }

    private String extractId() {
        String pathInfo = getRequest().getPathInfo();
        if (pathInfo == null) {
            return null;
        }
        String normalized = pathInfo.replace("/com.etendoerp.metadata.meta", "");
        String remainder = normalized.startsWith(SAVED_VIEW_PATH)
            ? normalized.substring(SAVED_VIEW_PATH.length())
            : normalized;
        if (remainder.isEmpty() || remainder.equals("/")) {
            return null;
        }
        return remainder.startsWith("/") ? remainder.substring(1) : remainder;
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
        } catch (Exception e) {
            throw new IOException("Invalid JSON body: " + e.getMessage(), e);
        }
    }
}

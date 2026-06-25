/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.ui.Menu;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** Handles POST /meta/favorites/toggle. */
public class FavoritesService extends MetadataService {

    private static final String MENU_ID = "menuId";
    private static final String USER_ID = "userId";
    private static final String ROLE_ID = "roleId";
    private static final String TOGGLE_PATH = "/favorites/toggle";
    private static final String LIST_PATH = "/favorites";
    private static final String FAVORITE_TABLE = "ETMETA_USER_FAVORITE";

    private static final String EXISTS_HQL =
        "select count(f) from etmeta_User_Favorite f " +
        "where f.userContact.id = :userId and f.menu.id = :menuId " +
        "and f.role.id = :roleId and f.active = true";

    private static final String DELETE_HQL =
        "delete from etmeta_User_Favorite f " +
        "where f.userContact.id = :userId and f.menu.id = :menuId " +
        "and f.role.id = :roleId";

    private static final String MAX_SEQNO_HQL =
        "select coalesce(max(f.sequenceNo), 0) from etmeta_User_Favorite f " +
        "where f.userContact.id = :userId and f.role.id = :roleId and f.active = true";

    private static final String LIST_HQL =
        "select m.name, m.action, m.id, m.window.id " +
        "from etmeta_User_Favorite f join f.menu m " +
        "where f.userContact.id = :userId and f.role.id = :roleId and f.active = true " +
        "order by f.sequenceNo asc";

    public FavoritesService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String path = normalizePath(getRequest().getPathInfo());
        String method = getRequest().getMethod();
        if (LIST_PATH.equals(path) && "GET".equalsIgnoreCase(method)) {
            try {
                OBContext.setAdminMode(true);
                write(listFavorites());
                return;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new InternalServerException(e.getMessage(), e);
            } finally {
                OBContext.restorePreviousMode();
            }
        }
        if (!TOGGLE_PATH.equals(path) || !"POST".equalsIgnoreCase(method)) {
            throw new NotFoundException();
        }

        try {
            OBContext.setAdminMode(true);
            JSONObject body = new JSONObject(readBody());
            String menuId = body.getString(MENU_ID);
            String userId = OBContext.getOBContext().getUser().getId();
            String roleId = OBContext.getOBContext().getRole().getId();
            write(toggle(userId, menuId, roleId));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private JSONObject listFavorites() throws Exception {
        String userId = OBContext.getOBContext().getUser().getId();
        String roleId = OBContext.getOBContext().getRole().getId();
        Query<Object[]> q = OBDal.getInstance().getSession().createQuery(LIST_HQL, Object[].class);
        q.setParameter(USER_ID, userId);
        q.setParameter(ROLE_ID, roleId);
        List<Object[]> rows = q.list();

        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            items.put(new JSONObject()
                .put("label", row[0])
                .put("action", row[1])
                .put(MENU_ID, row[2])
                .put("windowId", row[3] != null ? row[3] : JSONObject.NULL));
        }
        return new JSONObject().put("items", items);
    }

    private JSONObject toggle(String userId, String menuId, String roleId) throws Exception {
        Query<Long> existsQ = OBDal.getInstance().getSession().createQuery(EXISTS_HQL, Long.class);
        existsQ.setParameter(USER_ID, userId);
        existsQ.setParameter(MENU_ID, menuId);
        existsQ.setParameter(ROLE_ID, roleId);
        boolean exists = existsQ.uniqueResult() > 0;

        if (exists) {
            OBDal.getInstance().getSession()
                .createQuery(DELETE_HQL)
                .setParameter(USER_ID, userId)
                .setParameter(MENU_ID, menuId)
                .setParameter(ROLE_ID, roleId)
                .executeUpdate();
            OBDal.getInstance().flush();
            return new JSONObject().put("action", "removed").put(MENU_ID, menuId);
        }

        Menu menu = OBDal.getInstance().get(Menu.class, menuId);
        if (menu == null) {
            throw new NotFoundException();
        }

        Query<Long> seqQ = OBDal.getInstance().getSession().createQuery(MAX_SEQNO_HQL, Long.class);
        seqQ.setParameter(USER_ID, userId);
        seqQ.setParameter(ROLE_ID, roleId);
        Long maxSeq = seqQ.uniqueResult();

        BaseOBObject fav = newEntity(FAVORITE_TABLE);
        fav.set("client", OBContext.getOBContext().getCurrentClient());
        fav.set("organization", OBContext.getOBContext().getCurrentOrganization());
        fav.set("userContact", OBDal.getInstance().get(User.class, userId));
        fav.set("role", OBDal.getInstance().get(Role.class, roleId));
        fav.set("menu", menu);
        fav.set("sequenceNo", maxSeq == null ? 10L : maxSeq + 10L);

        try {
            OBDal.getInstance().save(fav);
            OBDal.getInstance().flush();
        } catch (Exception e) {
            OBDal.getInstance().getSession().evict(fav);
            throw e;
        }

        return new JSONObject().put("action", "added").put(MENU_ID, menuId);
    }

    @SuppressWarnings("unchecked")
    private BaseOBObject newEntity(String tableName) {
        Entity entity = ModelProvider.getInstance().getEntityByTableName(tableName);
        if (entity == null) {
            throw new InternalServerException("Entity not found for table: " + tableName);
        }
        return (BaseOBObject) OBProvider.getInstance().get((Class<BaseOBObject>) entity.getMappingClass());
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path
            .replace("/com.etendoerp.metadata.meta", "")
            .replace("/com.etendoerp.metadata.sws", "");
    }

    private String readBody() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (java.io.BufferedReader reader = getRequest().getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}

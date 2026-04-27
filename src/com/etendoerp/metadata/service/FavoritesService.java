package com.etendoerp.metadata.service;

import com.etendoerp.metadata.data.UserFavorite;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.ui.Menu;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handles POST /meta/favorites/toggle
 *
 * Body: { "menuId": "<AD_Menu_ID>" }
 * Response: { "action": "added"|"removed", "menuId": "..." }
 *
 * If the menu item is not yet in the user's favorites, it is added.
 * If it already exists, it is removed (toggle).
 */
public class FavoritesService extends MetadataService {

    private static final String TOGGLE_PATH = "/favorites/toggle";

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

    public FavoritesService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String path = getRequest().getPathInfo();
        if (path == null || !path.endsWith(TOGGLE_PATH)) {
            throw new NotFoundException();
        }
        if (!"POST".equalsIgnoreCase(getRequest().getMethod())) {
            throw new NotFoundException();
        }

        try {
            OBContext.setAdminMode(true);

            JSONObject body = new JSONObject(readBody());
            String menuId = body.getString("menuId");
            String userId = OBContext.getOBContext().getUser().getId();
            String roleId = OBContext.getOBContext().getRole().getId();

            JSONObject result = toggle(userId, menuId, roleId);
            write(result);

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private JSONObject toggle(String userId, String menuId, String roleId) throws Exception {
        Query<Long> existsQ = OBDal.getInstance().getSession()
                .createQuery(EXISTS_HQL, Long.class);
        existsQ.setParameter("userId", userId);
        existsQ.setParameter("menuId", menuId);
        existsQ.setParameter("roleId", roleId);
        boolean exists = existsQ.uniqueResult() > 0;

        if (exists) {
            // remove via bulk DELETE to avoid Hibernate stale-state issues
            OBDal.getInstance().getSession()
                    .createQuery(DELETE_HQL)
                    .setParameter("userId", userId)
                    .setParameter("menuId", menuId)
                    .setParameter("roleId", roleId)
                    .executeUpdate();
            OBDal.getInstance().flush();
            return new JSONObject().put("action", "removed").put("menuId", menuId);
        }

        // add
        Menu menu = OBDal.getInstance().get(Menu.class, menuId);
        if (menu == null) {
            throw new NotFoundException();
        }

        Query<Long> seqQ = OBDal.getInstance().getSession()
                .createQuery(MAX_SEQNO_HQL, Long.class);
        seqQ.setParameter("userId", userId);
        seqQ.setParameter("roleId", roleId);
        Long maxSeq = seqQ.uniqueResult();

        UserFavorite fav = (UserFavorite) org.openbravo.base.provider.OBProvider.getInstance().get(UserFavorite.class);
        fav.setClient(OBContext.getOBContext().getCurrentClient());
        fav.setOrganization(OBContext.getOBContext().getCurrentOrganization());
        fav.setUserContact(OBDal.getInstance().get(
                org.openbravo.model.ad.access.User.class, userId));
        fav.setRole(OBDal.getInstance().get(Role.class, roleId));
        fav.setMenu(menu);
        fav.setSequenceNo(maxSeq == null ? 10L : maxSeq + 10L);
        try {
            OBDal.getInstance().getSession().save(fav);
            OBDal.getInstance().flush();
        } catch (Exception e) {
            OBDal.getInstance().getSession().evict(fav);
            throw e;
        }

        return new JSONObject().put("action", "added").put("menuId", menuId);
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

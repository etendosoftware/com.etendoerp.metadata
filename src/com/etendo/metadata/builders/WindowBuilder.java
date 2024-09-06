package com.etendo.metadata.builders;

import com.etendo.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

public class WindowBuilder {
    private static final DataToJsonConverter converter = new DataToJsonConverter();
    private final String id;

    public WindowBuilder(String id) {
        this.id = id;
    }

    public JSONObject toJSON() {
        Role role = OBContext.getOBContext().getRole();
        org.openbravo.model.ad.ui.Window adWindow = OBDal.getInstance().get(org.openbravo.model.ad.ui.Window.class, this.id);
        OBCriteria<WindowAccess> windowAccessCriteria = OBDal.getInstance().createCriteria(WindowAccess.class);
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));

        if (adWindow != null) {
            windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow));
            windowAccessCriteria.setMaxResults(1);
            WindowAccess windowAccess = (WindowAccess) windowAccessCriteria.uniqueResult();

            if (windowAccess != null) {
                return converter.toJsonObject(windowAccess.getWindow(), DataResolvingMode.FULL_TRANSLATABLE);
            } else {
                throw new OBSecurityException();
            }
        } else {
            throw new NotFoundException("Not found");
        }
    }
}

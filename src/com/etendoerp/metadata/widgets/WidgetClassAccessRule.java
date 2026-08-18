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

package com.etendoerp.metadata.widgets;

import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

/**
 * Opt-out role access for widget classes: a class with no access rows is allowed for every
 * role; once it has at least one row, only the listed roles are allowed.
 */
public final class WidgetClassAccessRule {

    private WidgetClassAccessRule() {
    }

    /**
     * Checks whether the given role may see/add the given widget class.
     *
     * @param widgetClassId the ETMETA_WIDGET_CLASS id
     * @param roleId        the current session role id
     * @return true if the class has no access rows, or the role is explicitly granted
     */
    public static boolean isAllowed(String widgetClassId, String roleId) {
        Query<Long> restricted = OBDal.getInstance().getSession().createQuery(
                "select count(a) from etmeta_Widget_Class_Access a " +
                "where a.widgetClass.id = :cid and a.active = true", Long.class)
                .setParameter("cid", widgetClassId);
        if (restricted.uniqueResult() == 0L) {
            return true;
        }
        Query<Long> granted = OBDal.getInstance().getSession().createQuery(
                "select count(a) from etmeta_Widget_Class_Access a " +
                "where a.widgetClass.id = :cid and a.role.id = :rid and a.active = true", Long.class)
                .setParameter("cid", widgetClassId).setParameter("rid", roleId);
        return granted.uniqueResult() > 0L;
    }
}

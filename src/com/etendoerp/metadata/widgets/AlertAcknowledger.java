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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.alert.Alert;
import org.openbravo.model.ad.alert.AlertRule;

import com.etendoerp.metadata.widgets.resolvers.NotificationResolver;

/**
 * Marks pending alerts of the current user as read, the "Acknowledged" status of Classic's Alert Management.
 * <p>
 * Classic's {@code AlertManagementActionHandler} changes any alert it is given, in admin mode. This only changes
 * alerts the user can see in the notifications widget: status NEW, in a readable client and organization, and
 * of an alert rule the user receives directly or through their role. Other ids are ignored.
 */
public class AlertAcknowledger {

    static final String NEW_STATUS = "NEW";
    static final String ACKNOWLEDGED_STATUS = "ACKNOWLEDGED";

    // @formatter:off
    static final String OWN_PENDING_ALERTS_HQL =
          "from ADAlert a"
        + " where a.id in :ids"
        + "   and a.alertRule.id in :rules"
        + "   and coalesce(a.alertStatus, '" + NEW_STATUS + "') = '" + NEW_STATUS + "'"
        + "   and a.client.id in :clients"
        + "   and a.organization.id in :orgs";
    // @formatter:on

    private final NotificationResolver rulesSource;

    /** Creates an acknowledger that finds the rules of the user like the notifications widget. */
    public AlertAcknowledger() {
        this(new NotificationResolver());
    }

    AlertAcknowledger(NotificationResolver rulesSource) {
        this.rulesSource = rulesSource;
    }

    /**
     * Moves the given alerts of the current user from New to Acknowledged.
     *
     * @param alertIds ids of the alerts to mark as read
     * @param obContext context of the current user
     * @return how many alerts were marked
     */
    public int acknowledge(Collection<String> alertIds, OBContext obContext) {
        if (alertIds.isEmpty()) {
            return 0;
        }
        List<String> ruleIds = rulesSource.findRules(obContext)
            .stream()
            .map(AlertRule::getId)
            .collect(Collectors.toList());
        if (ruleIds.isEmpty()) {
            return 0;
        }
        List<Alert> alerts = OBDal.getInstance()
            .getSession()
            .createQuery(OWN_PENDING_ALERTS_HQL, Alert.class)
            .setParameterList("ids", alertIds)
            .setParameterList("rules", ruleIds)
            .setParameterList("clients", List.of(obContext.getReadableClients()))
            .setParameterList("orgs", List.of(obContext.getReadableOrganizations()))
            .list();
        for (Alert alert : alerts) {
            alert.setAlertStatus(ACKNOWLEDGED_STATUS);
            OBDal.getInstance().save(alert);
        }
        OBDal.getInstance().flush();
        return alerts.size();
    }
}

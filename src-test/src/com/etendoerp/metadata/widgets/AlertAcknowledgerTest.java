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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.alert.Alert;
import org.openbravo.model.ad.alert.AlertRule;

import com.etendoerp.metadata.widgets.resolvers.NotificationResolver;

/**
 * Tests {@link AlertAcknowledger}: only alerts of the user's rules are marked as read.
 */
class AlertAcknowledgerTest {

    private static final List<String> ALERT_IDS = List.of("ALERT_1");

    private MockedStatic<OBDal> dalStatic;
    private OBDal obDal;
    private Session session;
    private OBContext obContext;
    private NotificationResolver rulesSource;

    /**
     * Mocks the DAL session, the user context and the rule lookup.
     */
    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        obDal = mock(OBDal.class);
        when(obDal.getSession()).thenReturn(session);
        dalStatic = mockStatic(OBDal.class);
        dalStatic.when(OBDal::getInstance).thenReturn(obDal);
        obContext = mock(OBContext.class);
        when(obContext.getReadableClients()).thenReturn(new String[] { "0" });
        when(obContext.getReadableOrganizations()).thenReturn(new String[] { "0" });
        rulesSource = mock(NotificationResolver.class);
    }

    /**
     * Releases the static mock.
     */
    @AfterEach
    void tearDown() {
        dalStatic.close();
    }

    private void givenRule() {
        AlertRule rule = mock(AlertRule.class);
        when(rule.getId()).thenReturn("RULE");
        when(rulesSource.findRules(obContext)).thenReturn(List.of(rule));
    }

    @SuppressWarnings("unchecked")
    @Test
    void marksTheUsersPendingAlertsAsAcknowledged() {
        givenRule();
        Alert alert = mock(Alert.class);
        Query<Alert> query = mock(Query.class, RETURNS_SELF);
        when(query.list()).thenReturn(List.of(alert));
        when(session.createQuery(eq(AlertAcknowledger.OWN_PENDING_ALERTS_HQL), eq(Alert.class))).thenReturn(query);

        int count = new AlertAcknowledger(rulesSource).acknowledge(ALERT_IDS, obContext);

        assertEquals(1, count);
        verify(alert).setAlertStatus(AlertAcknowledger.ACKNOWLEDGED_STATUS);
        verify(obDal).save(alert);
        verify(obDal).flush();
    }

    @Test
    void changesNothingWhenTheUserReceivesNoAlertRule() {
        when(rulesSource.findRules(obContext)).thenReturn(Collections.emptyList());

        assertEquals(0, new AlertAcknowledger(rulesSource).acknowledge(ALERT_IDS, obContext));
        verify(session, never()).createQuery(any(String.class), eq(Alert.class));
    }

    @Test
    void changesNothingWithoutAlertIds() {
        assertEquals(0, new AlertAcknowledger(rulesSource).acknowledge(Collections.emptyList(), obContext));
        verify(rulesSource, never()).findRules(any());
    }
}

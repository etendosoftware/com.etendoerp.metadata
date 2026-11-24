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
package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Tests {@link ImplicitFilterMessage}: the classic text over a grid filtered by its implicit and/or
 * transactional filter.
 */
class ImplicitFilterMessageTest {

    private static final String LANGUAGE = "en_US";
    private static final String FILTERED_GRID = "This grid is filtered using";
    private static final String IMPLICIT = "an implicit filter";
    private static final String TRANSACTIONAL = "a transactional filter (%n day(s))";
    private static final String FILTER_NAME = "Open items";
    private static final String WINDOW_ID = "143";

    private MockedStatic<Utility> utility;
    private MockedStatic<OBContext> obContext;
    /** Real methods except the translated filter name: OBViewUtil reads the database when it is loaded. */
    private MockedStatic<ImplicitFilterMessage> filterMessage;
    private MockedStatic<RequestContext> requestContext;

    /**
     * Stubs the translated messages and the language of the session.
     */
    @BeforeEach
    void setUp() {
        utility = mockStatic(Utility.class);
        obContext = mockStatic(OBContext.class);
        filterMessage = mockStatic(ImplicitFilterMessage.class, CALLS_REAL_METHODS);
        requestContext = mockStatic(RequestContext.class);

        OBContext context = mock(OBContext.class);
        Language language = mock(Language.class);
        when(language.getLanguage()).thenReturn(LANGUAGE);
        when(context.getLanguage()).thenReturn(language);
        obContext.when(OBContext::getOBContext).thenReturn(context);

        stubMessage(ImplicitFilterMessage.FILTERED_GRID, FILTERED_GRID);
        stubMessage(ImplicitFilterMessage.IMPLICIT_FILTER, IMPLICIT);
        stubMessage(ImplicitFilterMessage.TRANSACTIONAL_FILTER, TRANSACTIONAL);
        stubMessage(ImplicitFilterMessage.AND, "and");
    }

    /**
     * Closes the static mocks.
     */
    @AfterEach
    void tearDown() {
        utility.close();
        obContext.close();
        filterMessage.close();
        requestContext.close();
    }

    private void stubMessage(String key, String text) {
        utility.when(() -> Utility.messageBD(any(DalConnectionProvider.class), eq(key), eq(LANGUAGE)))
                .thenReturn(text);
    }

    /**
     * Builds a tab mock.
     *
     * @param hqlFilter the HQL filter clause, or {@code null}
     * @param filterName the name of the filter, or {@code null}
     * @param level the tab level
     * @param windowType the type of its window
     * @return the tab
     */
    private Tab tab(String hqlFilter, String filterName, long level, String windowType) {
        Tab tab = mock(Tab.class);
        Window window = mock(Window.class);
        when(window.getWindowType()).thenReturn(windowType);
        when(window.getId()).thenReturn(WINDOW_ID);
        when(tab.getWindow()).thenReturn(window);
        when(tab.getTabLevel()).thenReturn(level);
        when(tab.getHqlfilterclause()).thenReturn(hqlFilter);
        when(tab.getFilterName()).thenReturn(filterName);
        filterMessage.when(() -> ImplicitFilterMessage.translatedFilterName(tab)).thenReturn(filterName);
        return tab;
    }

    /**
     * Stubs the transactional range of the session.
     *
     * @param days the range, in days
     */
    private void stubTransactionalRange(String days) {
        RequestContext context = mock(RequestContext.class);
        when(context.getVariablesSecureApp()).thenReturn(mock(VariablesSecureApp.class));
        requestContext.when(RequestContext::get).thenReturn(context);
        utility.when(() -> Utility.getTransactionalDate(any(), any(), eq(WINDOW_ID))).thenReturn(days);
    }

    /**
     * A tab without filters gets no message.
     */
    @Test
    void buildIsEmptyWithoutFilters() {
        assertEquals("", ImplicitFilterMessage.build(tab(null, null, 1L, ImplicitFilterMessage.TRANSACTIONAL_WINDOW)));
    }

    /**
     * An implicit filter names the filter of the tab.
     */
    @Test
    void buildDescribesTheImplicitFilterAndItsName() {
        String message = ImplicitFilterMessage.build(tab("e.processed = 'N'", FILTER_NAME, 1L, "M"));

        assertEquals(FILTERED_GRID + " " + IMPLICIT + "<i>(" + FILTER_NAME + ")</i>.", message);
    }

    /**
     * The top tab of a transactional window describes the transactional filter with its range in days.
     */
    @Test
    void buildDescribesTheTransactionalFilterWithItsRange() {
        stubTransactionalRange("7");

        String message = ImplicitFilterMessage.build(tab(null, null, 0L, ImplicitFilterMessage.TRANSACTIONAL_WINDOW));

        assertEquals(FILTERED_GRID + " a transactional filter (7 day(s)).", message);
    }

    /**
     * Both filters are joined with the translated "and".
     */
    @Test
    void buildJoinsBothFilters() {
        stubTransactionalRange("1");

        String message = ImplicitFilterMessage.build(
                tab("e.processed = 'N'", null, 0L, ImplicitFilterMessage.TRANSACTIONAL_WINDOW));

        assertEquals(FILTERED_GRID + " " + IMPLICIT + " and a transactional filter (1 day(s)).", message);
    }

    /**
     * Without a request context the range falls back to the classic default of one day.
     */
    @Test
    void buildUsesTheDefaultRangeWithoutRequestContext() {
        requestContext.when(RequestContext::get).thenThrow(new IllegalStateException("no request"));

        String message = ImplicitFilterMessage.build(tab(null, null, 0L, ImplicitFilterMessage.TRANSACTIONAL_WINDOW));

        assertEquals(FILTERED_GRID + " a transactional filter ("
                + ImplicitFilterMessage.DEFAULT_TRANSACTIONAL_RANGE + " day(s)).", message);
    }

    /**
     * Only the top tab of a transactional window applies the transactional filter.
     */
    @Test
    void isTransactionalOnlyForTheTopTabOfATransactionalWindow() {
        assertFalse(ImplicitFilterMessage.isTransactional(tab(null, null, 1L, ImplicitFilterMessage.TRANSACTIONAL_WINDOW)));
        assertFalse(ImplicitFilterMessage.isTransactional(tab(null, null, 0L, "M")));
    }
}

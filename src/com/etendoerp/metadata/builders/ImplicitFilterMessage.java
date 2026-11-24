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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.client.application.window.OBViewUtil;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Builds the message classic shows over a grid filtered by its implicit filter, with the same rules and
 * translated texts as {@code OBViewGridComponent.getFilterName}: "This grid is filtered using an implicit
 * filter (name) and a transactional filter (...)."
 */
public final class ImplicitFilterMessage {

    static final String FILTERED_GRID = "OBUIAPP_FilteredGrid";
    static final String IMPLICIT_FILTER = "OBUIAPP_ImplicitFilter";
    static final String TRANSACTIONAL_FILTER = "OBUIAPP_TransactionalFilter";
    static final String AND = "And";
    /** Window type whose top tab applies the transactional filter. */
    static final String TRANSACTIONAL_WINDOW = "T";
    /** Level of the top tab of a window. */
    static final Long TOP_LEVEL = 0L;
    /** Classic default range, in days, when {@code Transactional$Range} is not set. */
    static final String DEFAULT_TRANSACTIONAL_RANGE = "1";
    private static final String DAYS_PLACEHOLDER = "%n";
    private static final Logger log = LogManager.getLogger(ImplicitFilterMessage.class);

    private ImplicitFilterMessage() {
    }

    /**
     * Builds the message of {@code tab}.
     *
     * @param tab the tab whose grid is filtered
     * @return the message, or an empty string when the tab has neither an implicit nor a transactional filter
     */
    public static String build(Tab tab) {
        String filters = describeImplicitFilter(tab);
        if (isTransactional(tab)) {
            String transactional = message(TRANSACTIONAL_FILTER).replace(DAYS_PLACEHOLDER, transactionalRange(tab));
            filters = joinFilters(filters, transactional);
        }
        if (filters.isEmpty()) {
            return "";
        }
        return message(FILTERED_GRID) + " " + filters + ".";
    }

    /** "an implicit filter (name)" when the tab has an HQL filter clause, empty otherwise. */
    private static String describeImplicitFilter(Tab tab) {
        if (tab.getHqlfilterclause() == null) {
            return "";
        }
        String description = message(IMPLICIT_FILTER);
        if (tab.getFilterName() != null) {
            description += "<i>(" + translatedFilterName(tab) + ")</i>";
        }
        return description;
    }

    /**
     * The filter name of the tab in the language of the session, as classic shows it. {@code OBViewUtil} also
     * reads the translations of a tab cached outside the current DAL session.
     *
     * @param tab the tab with a filter name
     * @return the translated filter name, or the filter name of the tab without a translation
     */
    static String translatedFilterName(Tab tab) {
        return OBViewUtil.getLabel(tab, tab.getADTabTrlList(), Tab.PROPERTY_FILTERNAME);
    }

    private static String joinFilters(String first, String second) {
        if (first.isEmpty()) {
            return second;
        }
        return first + " " + message(AND) + " " + second;
    }

    /**
     * The transactional filter applies to the top tab of a transactional window, as the classic grid does.
     *
     * @param tab the tab to check
     * @return true when the grid of the tab applies the transactional filter
     */
    static boolean isTransactional(Tab tab) {
        Window window = tab.getWindow();
        return TOP_LEVEL.equals(tab.getTabLevel()) && window != null && TRANSACTIONAL_WINDOW.equals(window.getWindowType());
    }

    /** Days of the transactional filter ({@code Transactional$Range}), or the classic default of 1. */
    private static String transactionalRange(Tab tab) {
        try {
            String range = Utility.getTransactionalDate(new DalConnectionProvider(false),
                    RequestContext.get().getVariablesSecureApp(), tab.getWindow().getId());
            return StringUtils.defaultIfBlank(range, DEFAULT_TRANSACTIONAL_RANGE);
        } catch (RuntimeException e) {
            log.debug("Transactional range not available, using the default: {}", e.getMessage());
            return DEFAULT_TRANSACTIONAL_RANGE;
        }
    }

    private static String message(String key) {
        return Utility.messageBD(new DalConnectionProvider(false), key,
                OBContext.getOBContext().getLanguage().getLanguage());
    }
}

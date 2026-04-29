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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.data;

import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.userinterface.selector.Selector;

/**
 * Holds the selector and tree selector associated with a reference.
 */
public class ReferenceSelectors {
    /** The standard selector for this reference, or {@code null} if not applicable. */
    public final Selector selector;
    /** The tree selector for this reference, or {@code null} if not applicable. */
    public final ReferencedTree treeSelector;

    /**
     * Creates a new ReferenceSelectors with the given selector and tree selector.
     *
     * @param selector     the standard selector, or {@code null}
     * @param treeSelector the tree selector, or {@code null}
     */
    public ReferenceSelectors(Selector selector, ReferencedTree treeSelector) {
        this.selector = selector;
        this.treeSelector = treeSelector;
    }
}

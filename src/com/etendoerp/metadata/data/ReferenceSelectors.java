package com.etendoerp.metadata.data;

import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.userinterface.selector.Selector;

/**
 * @author luuchorocha
 */
public class ReferenceSelectors {
    public final Selector selector;
    public final ReferencedTree treeSelector;

    public ReferenceSelectors(Selector selector, ReferencedTree treeSelector) {
        this.selector = selector;
        this.treeSelector = treeSelector;
    }
}

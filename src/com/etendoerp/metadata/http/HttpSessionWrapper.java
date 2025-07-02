package com.etendoerp.metadata.http;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

import org.openbravo.client.kernel.RequestContext;

/**
 * @author luuchorocha
 */
public class HttpSessionWrapper extends RequestContext.HttpSessionWrapper {
    private Enumeration<String> attributeNames;

    public HttpSessionWrapper() {
        super();
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        if (attributeNames == null) {
            attributeNames = new Vector<>(Collections.list(super.getAttributeNames())).elements();
        }

        return attributeNames;
    }
}

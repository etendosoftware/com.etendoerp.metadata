package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

public class HttpSessionWrapper extends RequestContext.HttpSessionWrapper {
    private volatile Enumeration<String> attributeNames = null;

    @Override
    public Enumeration<String> getAttributeNames() {
        if (attributeNames == null) {
            attributeNames = new Vector<>(Collections.list(super.getAttributeNames())).elements();
        }

        return attributeNames;
    }
}

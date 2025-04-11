package com.etendoerp.metadata.http;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

import org.openbravo.client.kernel.RequestContext;

/**
 * @author luuchorocha
 */
public class HttpSessionWrapper extends RequestContext.HttpSessionWrapper {
    private final Enumeration<String> attributeNames;

    {
        attributeNames = new Vector<>(Collections.list(super.getAttributeNames())).elements();
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return attributeNames;
    }
}

package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

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

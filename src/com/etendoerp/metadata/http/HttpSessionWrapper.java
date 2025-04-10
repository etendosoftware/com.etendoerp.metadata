package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

/**
 * @author luuchorocha
 */
public class HttpSessionWrapper extends RequestContext.HttpSessionWrapper {
    private static final ThreadLocal<Enumeration<String>> attributeNames = new ThreadLocal<>();

    public static void clear() {
        attributeNames.remove();
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        Enumeration<String> localAttributes = attributeNames.get();

        if (localAttributes == null) {
            localAttributes = new Vector<>(Collections.list(super.getAttributeNames())).elements();
            attributeNames.set(localAttributes);
        }

        return localAttributes;
    }
}

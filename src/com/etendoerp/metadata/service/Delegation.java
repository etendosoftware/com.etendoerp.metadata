package com.etendoerp.metadata.service;

import org.apache.commons.lang3.function.TriFunction;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.function.Predicate;

/**
 * @author luuchorocha
 */
public class Delegation {
    private final Predicate<String> matcher;
    private final TriFunction<HttpSecureAppServlet, HttpServletRequest, HttpServletResponse, MetadataService> creator;

    public Delegation(final Predicate<String> matcher,
                      final TriFunction<HttpSecureAppServlet, HttpServletRequest, HttpServletResponse, MetadataService> creator) {
        this.matcher = matcher;
        this.creator = creator;
    }

    public boolean matches(final String path) {
        return matcher.test(path);
    }

    public MetadataService create(final HttpSecureAppServlet caller, HttpServletRequest req,
                                  final HttpServletResponse res) {
        return creator.apply(caller, req, res);
    }
}

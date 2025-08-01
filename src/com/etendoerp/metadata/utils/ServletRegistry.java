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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.utils;

import static com.etendoerp.metadata.utils.Constants.SERVLET_PATH;
import static com.etendoerp.metadata.utils.Constants.SERVLET_PATH_LENGTH;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletRegistration;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.exceptions.NotFoundException;

public class ServletRegistry {
    private static final Map<String, ServletRegistration> SERVLET_REGISTRY = buildServletRegistry();

    private static Map<String, ServletRegistration> buildServletRegistry() {
        final Collection<? extends ServletRegistration> servletRegistrations = getServletRegistrations();
        final Map<String, ServletRegistration> result = new ConcurrentHashMap<>();

        for (ServletRegistration sr : servletRegistrations) {
            for (String mapping : sr.getMappings()) {
                result.put(mapping.replace("/*", ""), sr);
            }
        }

        return result;
    }

    private static Collection<? extends ServletRegistration> getServletRegistrations() {
        return RequestContext.getServletContext().getServletRegistrations().values();
    }

    private static String getFirstSegment(String path) {
        int secondSlash = path.indexOf("/", 1);

        return secondSlash == -1 ? path : path.substring(0, secondSlash);
    }

    private static HttpSecureAppServlet getOrCreateServlet(String uri) {
        try {
            Class<? extends HttpSecureAppServlet> klazz = getServletClass(uri);
            HttpSecureAppServlet servlet = WeldUtils.getInstanceFromStaticBeanManager(klazz);

            if (servlet == null) {
                servlet = klazz.getDeclaredConstructor().newInstance();
            }

            return servlet;
        } catch (Exception e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private static Class<? extends HttpSecureAppServlet> getServletClass(String uri) throws ClassNotFoundException {
        return Class.forName(findMatchingServlet(uri).getClassName()).asSubclass(HttpSecureAppServlet.class);
    }

    private static ServletRegistration findMatchingServlet(String uri) {
        ServletRegistration servlet = SERVLET_REGISTRY.get(uri);

        if (servlet == null) {
            servlet = Optional.of(SERVLET_REGISTRY.get(getFirstSegment(uri))).orElseThrow(NotFoundException::new);
        }

        return servlet;
    }

    private static String getMappingPath(String uri) {
        if (uri == null) {
            throw new NotFoundException("Missing path info in request");
        }

        return uri.startsWith(SERVLET_PATH) ? uri.substring(SERVLET_PATH_LENGTH) : uri;
    }

    public static HttpSecureAppServlet getDelegatedServlet(HttpSecureAppServlet caller, String uri) {
        HttpSecureAppServlet servlet = getOrCreateServlet(getMappingPath(uri));

        if (servlet.getServletConfig() == null) {
            servlet.init(caller.getServletConfig());
        }

        return servlet;
    }
}

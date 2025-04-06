package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.NotFoundException;
import org.apache.commons.lang3.function.TriFunction;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.function.Predicate;

import static com.etendoerp.metadata.utils.Constants.*;


public class ServiceFactory {
    private final List<Service> services;

    public ServiceFactory() {
        services = List.of(new Service(path -> path.startsWith(WINDOW_PATH), WindowService::new),
                           new Service(path -> path.startsWith(TAB_PATH), TabService::new),
                           new Service(path -> path.startsWith(TOOLBAR_PATH), ToolbarService::new),
                           new Service(path -> path.startsWith(LANGUAGE_PATH), LanguageService::new),
                           new Service(path -> path.equals(MENU_PATH), MenuService::new),
                           new Service(path -> path.equals(SESSION_PATH), SessionService::new),
                           new Service(path -> path.equals(MESSAGE_PATH), MessageService::new),
                           new Service(path -> path.startsWith(DELEGATED_SERVLET_PATH), ServletService::new));
    }

    public MetadataService getService(final HttpSecureAppServlet servlet, final HttpServletRequest req,
                                      final HttpServletResponse res) {
        final String path = req.getPathInfo();

        return services.stream().filter(entry -> entry.matches(path)).findFirst()
                       .map(entry -> entry.create(servlet, req, res))
                       .orElseThrow(() -> new NotFoundException("Invalid URL: " + path));
    }

    private static class Service {
        private final Predicate<String> matcher;
        private final TriFunction<HttpSecureAppServlet, HttpServletRequest, HttpServletResponse, MetadataService>
                creator;

        public Service(final Predicate<String> matcher,
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
}

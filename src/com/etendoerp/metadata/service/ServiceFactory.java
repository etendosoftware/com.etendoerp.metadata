package com.etendoerp.metadata.service;

import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class ServiceFactory {
    private final List<Service> serviceEntries;

    public ServiceFactory(final HttpSecureAppServlet servlet) {
        serviceEntries = List.of(new Service(path -> path.startsWith(Constants.WINDOW_PATH), WindowService::new),
                                 new Service(path -> path.startsWith(Constants.TAB_PATH), TabService::new),
                                 new Service(path -> path.startsWith(Constants.TOOLBAR_PATH), ToolbarService::new),
                                 new Service(path -> path.startsWith(Constants.LANGUAGE_PATH), LanguageService::new),
                                 new Service(path -> path.equals(Constants.MENU_PATH), MenuService::new),
                                 new Service(path -> path.equals(Constants.SESSION_PATH), SessionService::new),
                                 new Service(path -> path.equals(Constants.MESSAGE_PATH), MessageService::new),
                                 new Service(path -> path.startsWith(Constants.DELEGATED_SERVLET_PATH),
                                             (req, res) -> new ServletService(servlet, req, res)));
    }

    public MetadataService getService(final String path, final HttpServletRequest req, final HttpServletResponse res) {
        return serviceEntries.stream().filter(entry -> entry.matches(path)).findFirst()
                             .map(entry -> entry.create(req, res))
                             .orElseThrow(() -> new NotFoundException("Invalid URL: " + path));
    }

    private static class Service {
        private final Predicate<String> matcher;
        private final BiFunction<HttpServletRequest, HttpServletResponse, MetadataService> creator;

        public Service(final Predicate<String> matcher,
                       final BiFunction<HttpServletRequest, HttpServletResponse, MetadataService> creator) {
            this.matcher = matcher;
            this.creator = creator;
        }

        public boolean matches(final String path) {
            return matcher.test(path);
        }

        public MetadataService create(final HttpServletRequest req, final HttpServletResponse res) {
            return creator.apply(req, res);
        }
    }
}

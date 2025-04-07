package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.NotFoundException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static com.etendoerp.metadata.utils.Constants.*;

/**
 * @author luuchorocha
 */
public class ServiceFactory {
    private final List<Delegation> delegations;

    public ServiceFactory() {
        delegations = List.of(new Delegation(path -> path.startsWith(WINDOW_PATH), WindowService::new),
                              new Delegation(path -> path.startsWith(TAB_PATH), TabService::new),
                              new Delegation(path -> path.startsWith(TOOLBAR_PATH), ToolbarService::new),
                              new Delegation(path -> path.startsWith(LANGUAGE_PATH), LanguageService::new),
                              new Delegation(path -> path.equals(MENU_PATH), MenuService::new),
                              new Delegation(path -> path.equals(SESSION_PATH), SessionService::new),
                              new Delegation(path -> path.equals(MESSAGE_PATH), MessageService::new),
                              new Delegation(path -> true, ServletService::new));
    }

    public MetadataService getService(final HttpSecureAppServlet servlet, final HttpServletRequest req,
                                      final HttpServletResponse res) {
        final String path = req.getPathInfo();

        return delegations.stream().filter(entry -> entry.matches(path)).findFirst()
                          .map(entry -> entry.create(servlet, req, res))
                          .orElseThrow(() -> new NotFoundException("Invalid URL: " + path));
    }
}

package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.LANGUAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.MENU_PATH;
import static com.etendoerp.metadata.utils.Constants.MESSAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.SESSION_PATH;
import static com.etendoerp.metadata.utils.Constants.TAB_PATH;
import static com.etendoerp.metadata.utils.Constants.TOOLBAR_PATH;
import static com.etendoerp.metadata.utils.Constants.WINDOW_PATH;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * @author luuchorocha
 */
public class ServiceFactory {
    private static final Map<String, Delegation> delegationCache = new ConcurrentHashMap<>();

    private static final List<Delegation> DELEGATIONS = List.of(
            new Delegation(path -> path.startsWith(WINDOW_PATH), WindowService::new),
            new Delegation(path -> path.startsWith(TAB_PATH), TabService::new),
            new Delegation(path -> path.startsWith(TOOLBAR_PATH), ToolbarService::new),
            new Delegation(path -> path.startsWith(LANGUAGE_PATH), LanguageService::new),
            new Delegation(path -> path.equals(MENU_PATH), MenuService::new),
            new Delegation(path -> path.equals(SESSION_PATH), SessionService::new),
            new Delegation(path -> path.equals(MESSAGE_PATH), MessageService::new),
            new Delegation(path -> true, ServletService::new)
    );

    private ServiceFactory() {
    }

    public static MetadataService getService(final HttpSecureAppServlet servlet,
                                             final HttpServletRequest req,
                                             final HttpServletResponse res) {
        final String path = req.getPathInfo();
        final Delegation cachedDelegation = delegationCache.get(path);

        if (cachedDelegation != null) {
            return cachedDelegation.create(servlet, req, res);
        }

        for (Delegation delegation : DELEGATIONS) {
            if (delegation.matches(path)) {
                delegationCache.put(path, delegation);

                return delegation.create(servlet, req, res);
            }
        }

        throw new NotFoundException("Invalid path: " + path);
    }
}

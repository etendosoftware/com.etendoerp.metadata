package com.etendoerp.metadata.http;

import org.apache.log4j.Logger;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.ThreadHandler;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import java.io.IOException;

import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;

@WebFilter(urlPatterns = {"/meta", "/meta/*"})
public class MetadataFilter implements Filter {
    private static final Logger logger = Logger.getLogger(MetadataFilter.class);

    @Override
    public void init(FilterConfig fConfig) {
        RequestContext.setServletContext(fConfig.getServletContext());
        logger.info("MetadataFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws
                                                                                              IOException,
                                                                                              ServletException {

        final ThreadHandler handler = new ThreadHandler() {
            @Override
            public void doBefore() {
                request.setAttribute(STATELESS_REQUEST_PARAMETER, "true");
            }

            @Override
            protected void doAction() throws Exception {
                chain.doFilter(request, response);
            }

            @Override
            public void doFinal(boolean error) {
                if (error) {
                    logger.error("An error occurred in MetadataFilter");
                }

                RequestContext.clear();
            }
        };

        handler.run();
    }

    @Override
    public void destroy() {
        logger.info("MetadataFilter destroyed");
    }
}

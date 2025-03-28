package com.etendoerp.metadata.http;

import org.openbravo.dal.core.ThreadHandler;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import java.io.IOException;

import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;

@WebFilter(urlPatterns = {"/meta", "/meta/*"})
public class MetadataFilter implements Filter {
    @Override
    public void init(FilterConfig fConfig) throws ServletException {
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

            }
        };

        handler.run();
    }

    @Override
    public void destroy() {
    }
}

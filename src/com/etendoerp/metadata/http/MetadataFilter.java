package com.etendoerp.metadata.http;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;

import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.service.MetadataService;

/**
 * @author luuchorocha
 */
@WebFilter(urlPatterns = { "/meta", "/meta/*" })
public class MetadataFilter implements Filter {
    @Override
    public void init(FilterConfig fConfig) {
        RequestContext.setServletContext(fConfig.getServletContext());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            MetadataService.clear();
        }
    }

    @Override
    public void destroy() {
    }
}

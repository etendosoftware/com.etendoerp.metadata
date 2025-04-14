package com.etendoerp.metadata.http;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;

import com.etendoerp.metadata.service.MetadataService;

/**
 * @author luuchorocha
 */
@WebFilter(urlPatterns = { "/meta", "/meta/*" })
public class MetadataFilter implements Filter {
    @Override
    public void init(FilterConfig fConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            MetadataService.clear();
            HttpServletRequestWrapper.clear();
        }
    }

    @Override
    public void destroy() {
    }
}

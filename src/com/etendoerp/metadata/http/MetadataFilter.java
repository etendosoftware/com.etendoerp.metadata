package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.exceptions.Utils.handleException;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.etendoerp.metadata.service.MetadataService;

/**
 * @author luuchorocha
 */
@WebFilter(urlPatterns = {"/meta", "/meta/*"})
public class MetadataFilter implements Filter {
    private static final Logger logger = Logger.getLogger(MetadataFilter.class);

    @Override
    public void init(FilterConfig fConfig) {
        logger.info("MetadataFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException e) {
            logger.error("MetadataFilter error: ".concat(e.getMessage()), e);

            handleException(e, (HttpServletResponse) response);
        } finally {
            MetadataService.clear();
        }
    }

    @Override
    public void destroy() {
        logger.info("MetadataFilter destroyed");
    }
}

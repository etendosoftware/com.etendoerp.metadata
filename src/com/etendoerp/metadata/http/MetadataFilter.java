    package com.etendoerp.metadata.http;

    import com.etendoerp.metadata.service.MetadataService;
    import org.apache.log4j.Logger;
    import org.jboss.weld.module.web.servlet.SessionHolder;
    import org.openbravo.client.kernel.RequestContext;

    import javax.servlet.*;
    import javax.servlet.annotation.WebFilter;
    import java.io.IOException;

    /**
     * @author luuchorocha
     */
    @WebFilter(urlPatterns = {"/meta", "/meta/*"})
    public class MetadataFilter implements Filter {
        private static final Logger logger = Logger.getLogger(MetadataFilter.class);

        @Override
        public void init(FilterConfig fConfig) {
            RequestContext.setServletContext(fConfig.getServletContext());
            logger.info("MetadataFilter initialized");
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            try {
                chain.doFilter(request, response);
            } finally {
                RequestContext.clear();
                SessionHolder.clear();
                MetadataService.clear();
                HttpServletRequestWrapper.clear();
                HttpSessionWrapper.clear();
            }
        }

        @Override
        public void destroy() {
            logger.info("MetadataFilter destroyed");
        }
    }

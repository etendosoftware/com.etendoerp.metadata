package com.etendoerp.metadata.data;

import javax.servlet.ServletRegistration;

/**
 * @author luuchorocha
 */
public class ServletMapping {
    protected final ServletRegistration registration;
    protected final String mapping;

    public ServletMapping(ServletRegistration registration, String mapping) {
        this.registration = registration;
        this.mapping = mapping;
    }

    public ServletRegistration getRegistration() {
        return registration;
    }

    public String getMapping() {
        return mapping;
    }
}

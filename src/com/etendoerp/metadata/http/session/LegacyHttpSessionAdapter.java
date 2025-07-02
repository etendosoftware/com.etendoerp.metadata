package com.etendoerp.metadata.http.session;

    import javax.servlet.ServletContext;
    import javax.servlet.http.HttpSession;
    import javax.servlet.http.HttpSessionContext;
    import java.util.Collections;
    import java.util.Enumeration;

    public class LegacyHttpSessionAdapter implements HttpSession {
      private final String id;
      private final ServletContext servletContext;
      private final SessionAttributeStore attributeStore;
      private long creationTime;
      private long lastAccessedTime;
      private boolean invalidated = false;

      public LegacyHttpSessionAdapter(String sessionId, ServletContext servletContext) {
        this.id = sessionId;
        this.servletContext = servletContext;
        this.attributeStore = new SessionAttributeStore();
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = this.creationTime;
      }

      private void checkValid() {
        if (invalidated) {
          throw new IllegalStateException("Session already invalidated: " + getId());
        }
      }

      @Override
      public long getCreationTime() {
        checkValid();
        return creationTime;
      }

      @Override
      public String getId() {
        return id;
      }

      @Override
      public long getLastAccessedTime() {
        checkValid();
        return lastAccessedTime;
      }

      @Override
      public ServletContext getServletContext() {
        return servletContext;
      }

      @Override
      public void setMaxInactiveInterval(int interval) {
        checkValid();
      }

      @Override
      public int getMaxInactiveInterval() {
        checkValid();
        return -1;
      }

      @Override
      @Deprecated
      public HttpSessionContext getSessionContext() {
        checkValid();
        return null;
      }

      @Override
      public Object getAttribute(String name) {
        checkValid();
        lastAccessedTime = System.currentTimeMillis();
        return attributeStore.getAttribute(id, name);
      }

      @Override
      public Object getValue(String name) {
        return getAttribute(name);
      }

      @Override
      public Enumeration<String> getAttributeNames() {
        checkValid();
        lastAccessedTime = System.currentTimeMillis();
        return Collections.enumeration(attributeStore.getAttributes(id).keySet());
      }

      @Override
      public String[] getValueNames() {
        checkValid();
        return attributeStore.getAttributes(id).keySet().toArray(new String[0]);
      }

      @Override
      public void setAttribute(String name, Object value) {
        checkValid();
        if (value == null) {
          removeAttribute(name);
          return;
        }
        attributeStore.setAttribute(id, name, value);
        lastAccessedTime = System.currentTimeMillis();
      }

      @Override
      public void putValue(String name, Object value) {
        setAttribute(name, value);
      }

      @Override
      public void removeAttribute(String name) {
        checkValid();
        attributeStore.removeAttribute(id, name);
        lastAccessedTime = System.currentTimeMillis();
      }

      @Override
      public void removeValue(String name) {
        removeAttribute(name);
      }

      @Override
      public void invalidate() {
        checkValid();
        invalidated = true;
        attributeStore.removeAllAttributes(id);
      }

      @Override
      public boolean isNew() {
        checkValid();
        return false;
      }
    }

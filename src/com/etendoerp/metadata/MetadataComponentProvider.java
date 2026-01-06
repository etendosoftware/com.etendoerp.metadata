package com.etendoerp.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.client.kernel.BaseComponentProvider;
import org.openbravo.client.kernel.Component;
import org.openbravo.client.kernel.ComponentProvider;

@ApplicationScoped
@ComponentProvider.Qualifier(MetadataComponentProvider.METADATA_COMPONENT_TYPE)
public class MetadataComponentProvider extends BaseComponentProvider {

    public static final String METADATA_COMPONENT_TYPE = "SMF_OKR_Metadata";

    @Override
    public Component getComponent(String componentId, Map<String, Object> parameters) {
        throw new IllegalArgumentException(
                "Component id " + componentId + " not supported.");
    }

    @Override
    public List<ComponentResource> getGlobalComponentResources() {
        final List<ComponentResource> resources = new ArrayList<>();

        resources.add(createStaticResource(
                "web/com.etendoerp.metadata/js/okr-ui-flags.js",
                false
        ));

        return resources;
    }
}
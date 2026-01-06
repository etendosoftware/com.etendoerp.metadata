package com.etendoerp.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.client.kernel.BaseComponentProvider;
import org.openbravo.client.kernel.Component;
import org.openbravo.client.kernel.ComponentProvider;

/**
 * Component Provider for the Metadata module.
 * <p>
 * This provider is responsible for registering global static resources required
 * by the
 * metadata module, such as JavaScript files for UI flags.
 * </p>
 */
@ApplicationScoped
@ComponentProvider.Qualifier(MetadataComponentProvider.METADATA_COMPONENT_TYPE)
public class MetadataComponentProvider extends BaseComponentProvider {

    public static final String METADATA_COMPONENT_TYPE = "SMF_OKR_Metadata";

    /**
     * Retrieves a specific component.
     * <p>
     * This implementation does not support retrieving individual components and
     * will
     * always throw an {@link IllegalArgumentException}.
     * </p>
     *
     * @param componentId
     *                    The ID of the component to retrieve.
     * @param parameters
     *                    A map of parameters for the component.
     * @return Nothing, as this method always throws an exception.
     * @throws IllegalArgumentException
     *                                  Always thrown as this provider does not
     *                                  support component retrieval.
     */
    @Override
    public Component getComponent(String componentId, Map<String, Object> parameters) {
        throw new IllegalArgumentException(
                "Component id " + componentId + " not supported.");
    }

    /**
     * Retrieves the list of global component resources.
     * <p>
     * Registers the {@code okr-ui-flags.js} file as a static resource.
     * This resource is included in the new UI mode (OB3) but not in Classic mode.
     * </p>
     *
     * @return A list of {@link ComponentResource} containing the registered
     *         resources.
     */
    @Override
    public List<ComponentResource> getGlobalComponentResources() {
        final List<ComponentResource> resources = new ArrayList<>();

        resources.add(createStaticResource(
                "web/com.etendoerp.metadata/js/okr-ui-flags.js",
                false));

        return resources;
    }
}
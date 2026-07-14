package com.scripty.api;

import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.core.EmbeddedWrappers;

/**
 * Spring HATEOAS omits {@code _embedded} entirely for an empty
 * {@link CollectionModel}, which breaks native clients whose Decodable expects
 * the key to always be present. Wrapping the empty case in an
 * {@link EmbeddedWrappers#emptyCollectionOf(Class) typed empty wrapper} keeps
 * {@code _embedded} in the payload as an empty array under the resource's
 * {@code @Relation} collection rel.
 */
final class ApiCollections {

    private static final EmbeddedWrappers WRAPPERS = new EmbeddedWrappers(false);

    private ApiCollections() {
    }

    @SuppressWarnings("unchecked")
    static <T> CollectionModel<EntityModel<T>> of(List<EntityModel<T>> resources, Class<T> resourceType) {
        if (resources.isEmpty()) {
            return (CollectionModel<EntityModel<T>>) (CollectionModel<?>)
                    CollectionModel.of(List.of(WRAPPERS.emptyCollectionOf(resourceType)));
        }
        return CollectionModel.of(resources);
    }
}

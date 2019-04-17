package org.apache.skywalking.oap.server.receiver.envoy.als;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
class DependencyResource {
    @Getter(AccessLevel.PACKAGE)
    private final V1ObjectMeta metadata;

    private boolean stop;

    DependencyResource getOwnerResource(final String kind, final Fetcher transform) {
        if (stop) {
            return this;
        }
        if (metadata.getOwnerReferences() == null) {
            stop = true;
            return this;
        }
        V1OwnerReference ownerReference = null;
        for (V1OwnerReference each : metadata.getOwnerReferences()) {
            if (each.getKind().equals(kind)) {
                ownerReference = each;
                break;
            }
        }
        if (ownerReference == null) {
            stop = true;
            return this;
        }
        Optional<V1ObjectMeta> metaOptional = transform.apply(ownerReference);
        if (!metaOptional.isPresent()) {
            stop = true;
            return this;
        }
        return new DependencyResource(metaOptional.get());
    }
}

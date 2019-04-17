package org.apache.skywalking.oap.server.receiver.envoy.als;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

interface Fetcher extends Function<V1OwnerReference, Optional<V1ObjectMeta>> {

    Logger logger = LoggerFactory.getLogger(Fetcher.class);

    V1ObjectMeta go(V1OwnerReference ownerReference) throws ApiException;

    default Optional<V1ObjectMeta> apply(V1OwnerReference ownerReference) {
        try {
            return Optional.ofNullable(go(ownerReference));
        } catch (final ApiException e) {
            logger.error("code:{} header:{} body:{}", e.getCode(), e.getResponseHeaders(), e.getResponseBody());
            return Optional.empty();
        } catch (final Throwable th) {
            logger.error("other errors", th);
            return Optional.empty();
        }
    }
}

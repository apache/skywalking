/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

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

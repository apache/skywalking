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

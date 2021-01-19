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
 */

package org.apache.skywalking.oap.server.library.client.healthcheck;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

public class DelegatedHealthChecker implements HealthChecker {
    private final AtomicReference<HealthChecker> delegated = new AtomicReference<>();

    @Override public void health() {
        Optional.ofNullable(delegated.get()).ifPresent(HealthChecker::health);
    }

    @Override public void unHealth(Throwable t) {
        Optional.ofNullable(delegated.get()).ifPresent(d -> d.unHealth(t));
    }

    @Override
    public void unHealth(String reason) {
        Optional.ofNullable(delegated.get()).ifPresent(d -> d.unHealth(reason));
    }

    public void register(HealthChecker healthChecker) {
        delegated.set(healthChecker);
    }
}

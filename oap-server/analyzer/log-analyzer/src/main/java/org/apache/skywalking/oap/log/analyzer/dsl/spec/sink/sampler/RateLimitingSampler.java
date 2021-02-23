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

package org.apache.skywalking.oap.log.analyzer.dsl.spec.sink.sampler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Accessors(fluent = true)
@EqualsAndHashCode(of = {"qps"})
public class RateLimitingSampler implements Sampler {
    @Getter
    @Setter
    private volatile int qps;

    private final AtomicInteger factor = new AtomicInteger();

    private final ResetHandler resetHandler;

    public RateLimitingSampler(final ResetHandler resetHandler) {
        this.resetHandler = resetHandler;
    }

    @Override
    public RateLimitingSampler start() {
        resetHandler.start(this);
        return this;
    }

    @Override
    public void close() {
        resetHandler.close(this);
    }

    @Override
    public boolean sample() {
        return factor.getAndIncrement() < qps;
    }

    @Override
    public RateLimitingSampler reset() {
        factor.set(0);
        return this;
    }

    @Slf4j
    public static class ResetHandler {
        private final Set<Sampler> samplers = new HashSet<>();

        private volatile ScheduledFuture<?> future;

        private volatile boolean started = false;

        private synchronized void start(final Sampler sampler) {
            samplers.add(sampler);

            if (!started) {
                future = Executors.newSingleThreadScheduledExecutor()
                                  .scheduleAtFixedRate(this::reset, 1, 1, TimeUnit.SECONDS);
                started = true;
            }
        }

        private synchronized void close(final Sampler sampler) {
            samplers.remove(sampler);

            if (samplers.isEmpty() && future != null) {
                future.cancel(true);
                started = false;
            }
        }

        private synchronized void reset() {
            samplers.forEach(sampler -> {
                try {
                    sampler.reset();
                } catch (final Exception e) {
                    log.error("Failed to reset sampler {}.", sampler, e);
                }
            });
        }
    }
}

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
 */

package org.apache.skywalking.apm.agent.core.pool.connections;

import com.google.common.util.concurrent.AtomicDouble;
import java.util.function.Supplier;

public class FailureRateSupplier implements Supplier<Double> {
    private final AtomicDouble totalTimes = new AtomicDouble();
    private AtomicDouble failedTimes = new AtomicDouble();

    @Override
    public Double get() {
        double total = totalTimes.getAndSet(0);
        double failed = failedTimes.getAndSet(0);
        return total == 0 ? 0 : failed / total;
    }

    public void recordGetConnectionStatue(final boolean failed) {
        totalTimes.addAndGet(1);
        failedTimes.addAndGet(failed ? 1 : 0);
    }
}

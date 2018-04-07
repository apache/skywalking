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

package org.apache.skywalking.apm.collector.instrument;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author wusheng, peng-yongsheng
 */
public class ServiceMetricRecord {
    private AtomicLong totalNanoseconds;
    private AtomicLong counter;
    private AtomicLong errorCounter;

    ServiceMetricRecord() {
        totalNanoseconds = new AtomicLong(0);
        counter = new AtomicLong(0);
        errorCounter = new AtomicLong(0);
    }

    void add(long nanoseconds, boolean occurException) {
        totalNanoseconds.addAndGet(nanoseconds);
        counter.incrementAndGet();
        if (occurException)
            errorCounter.incrementAndGet();
    }

    void clear() {
        totalNanoseconds.set(0);
        counter.set(0);
        errorCounter.set(0);
    }

    long getMetricRecordCount() {
        return counter.get();
    }

    @Override public String toString() {
        if (counter.longValue() == 0) {
            return "Avg=N/A";
        }
        return "Avg=" + (totalNanoseconds.longValue() / counter.longValue()) + " (nanosecond)" +
            ", Success Rate=" + (counter.longValue() - errorCounter.longValue()) * 100 / counter.longValue() +
            "%, Calls=" + counter.longValue() + ", Total=" + totalNanoseconds.longValue() + " (nanosecond)";
    }
}

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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author wusheng, peng-yongsheng
 */
public class ServiceMetric {
    private ServiceMetricRecord winA;
    private ServiceMetricRecord winB;
    private AtomicBoolean isUsingWinA;
    private volatile int detectedBatchIndex;

    ServiceMetric(int detectedBatchIndex) {
        winA = detectedBatchIndex > -1 ? new ServiceMetricBatchRecord() : new ServiceMetricRecord();
        winB = detectedBatchIndex > -1 ? new ServiceMetricBatchRecord() : new ServiceMetricRecord();
        isUsingWinA = new AtomicBoolean(true);
        this.detectedBatchIndex = detectedBatchIndex;
    }

    void trace(long nanoseconds, boolean occurException, Object[] allArguments) {
        ServiceMetricRecord usingRecord = isUsingWinA.get() ? winA : winB;
        if (detectedBatchIndex > -1) {
            List listArgs = (List)allArguments[detectedBatchIndex];
            ((ServiceMetricBatchRecord)usingRecord).add(nanoseconds, occurException, listArgs == null ? 0 : listArgs.size());
        } else {
            usingRecord.add(nanoseconds, occurException);
        }
    }

    void exchangeWindows() {
        isUsingWinA.set(!isUsingWinA.get());
    }

    void clear() {
        if (isUsingWinA.get()) {
            winB.clear();
        } else {
            winA.clear();
        }
    }

    void toOutput(ReportWriter writer) {

        /*
         * If using A, then B is available and free to output.
         */
        writer.writeMetric(isUsingWinA.get() ? winB.toString() : winA.toString());
    }
}

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

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;

/**
 * The most label sets one row of a labeled meter metric holds, per metric. A labeled value merged into a row that is
 * full keeps the label sets the row already has and adds no new one, as OAL's {@code labelCount} does with its
 * {@code maxLabelCount}.
 *
 * <p>Keyed by the metric's generated class rather than carried on the instance: an instance rebuilt from a remote
 * message or from storage is created by its plain constructor and knows nothing but its class. A metric with no
 * limit, every MAL metric among them, keeps all its label sets.
 */
public final class LabeledValueLimits {
    private static final Map<String, Integer> LIMITS = new ConcurrentHashMap<>();

    private LabeledValueLimits() {
    }

    /**
     * @param meterClassName the generated class of the metric
     * @param maxLabels      the most label sets one row holds; 0 or less removes the limit
     */
    public static void register(final String meterClassName, final int maxLabels) {
        if (maxLabels > 0) {
            LIMITS.put(meterClassName, maxLabels);
        } else {
            LIMITS.remove(meterClassName);
        }
    }

    /**
     * @param rows the rows taking part in a merge
     * @return the limit the merge keeps, the largest any of them has; 0 for no limit
     */
    public static int of(final Metrics... rows) {
        int limit = 0;
        for (final Metrics row : rows) {
            final Integer declared = LIMITS.get(row.getClass().getName());
            if (declared != null && declared > limit) {
                limit = declared;
            }
        }
        return limit;
    }
}

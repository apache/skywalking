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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.*;
import org.apache.skywalking.oap.server.core.storage.model.Model;

/**
 * @author peng-yongsheng
 */
public class TimeSeriesUtils {

    public static String timeSeries(Model model) {
        long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), model.getDownsampling());
        return timeSeries(model, timeBucket);
    }

    public static String timeSeries(String modelName, long timeBucket, Downsampling downsampling) {
        switch (downsampling) {
            case None:
                return modelName;
            case Hour:
                return modelName + Const.LINE + timeBucket / 100;
            case Minute:
                return modelName + Const.LINE + timeBucket / 10000;
            case Second:
                return modelName + Const.LINE + timeBucket / 1000000;
            default:
                return modelName + Const.LINE + timeBucket;
        }
    }

    static String timeSeries(Model model, long timeBucket) {
        if (!model.isCapableOfTimeSeries()) {
            return model.getName();
        }

        return timeSeries(model.getName(), timeBucket, model.getDownsampling());
    }

    static long indexTimeSeries(String indexName) {
        return Long.valueOf(indexName.substring(indexName.lastIndexOf(Const.LINE) + 1));
    }
}

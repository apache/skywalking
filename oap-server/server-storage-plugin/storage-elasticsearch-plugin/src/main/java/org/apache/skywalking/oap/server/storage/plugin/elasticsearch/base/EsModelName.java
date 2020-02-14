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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.query.DownsamplingToStep;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.NetworkAddressInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.model.ModelName;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class EsModelName extends ModelName {

    private static final DateTimeFormatter YYYYMM = DateTimeFormat.forPattern("yyyyMM");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormat.forPattern("yyyyMMdd");

    public static final ImmutableSet<String> WHITE_INDEX_LIST = ImmutableSet.of(
            EndpointInventory.INDEX_NAME,
            NetworkAddressInventory.INDEX_NAME,
            ServiceInventory.INDEX_NAME,
            ServiceInstanceInventory.INDEX_NAME
        );

    public static List<String> build(Downsampling downsampling, String modelName, long startTB, long endTB) {
        if (WHITE_INDEX_LIST.contains(modelName)) {
            return Arrays.asList(modelName);
        }

        if (downsampling.equals(Downsampling.None)) {
            return Arrays.asList(modelName);
        }

        List<String> indexNameList = new LinkedList<>();
        Step step = DownsamplingToStep.transform(downsampling);
        if (startTB <= endTB) {
            switch (downsampling) {
                case Second:
                case Minute:
                case Hour:
                case Day:
                    long startTS = DurationUtils.INSTANCE.timeBucketToTimestamp(step, startTB);
                    long endTS = DurationUtils.INSTANCE.timeBucketToTimestamp(step, endTB);
                    while (startTS <= endTS) {
                        String indexName = build(downsampling, modelName) + "-" + YYYYMMDD.print(startTS);
                        indexNameList.add(indexName);
                        startTS = startTS + 24 * 60 * 60 * 1000;
                    }
                    break;
                case Month:
                    DateTime startDT = DurationUtils.INSTANCE.parseToDateTime(downsampling, startTB);
                    DateTime endDT = DurationUtils.INSTANCE.parseToDateTime(downsampling, endTB);
                    while (startDT.isAfter(endDT)) {
                        String indexName = build(downsampling, modelName) + "-" + YYYYMM.print(startDT);
                        indexNameList.add(indexName);
                        startDT = startDT.plusMonths(1);
                    }
                    break;
                default:
                    indexNameList.add(modelName);
            }
        }
        return indexNameList;
    }
}

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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.NetworkAddressInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class ModelName {

    private static final DateTimeFormatter YYYYMM = DateTimeFormat.forPattern("yyyyMM");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormat.forPattern("yyyyMMdd");

    public static String build(Downsampling downsampling, String modelName) {
        switch (downsampling) {
            case Month:
                return modelName + Const.ID_SPLIT + Downsampling.Month.getName();
            case Day:
                return modelName + Const.ID_SPLIT + Downsampling.Day.getName();
            case Hour:
                return modelName + Const.ID_SPLIT + Downsampling.Hour.getName();
            default:
                return modelName;
        }
    }

    public static String[] build(Downsampling downsampling, String modelName, long startTB, long endTB) {
        List<String> indexNameList = new ArrayList<>();

        List<String> whiteIndexList = new ArrayList<String>() {
            {
                add(EndpointInventory.INDEX_NAME);
                add(NetworkAddressInventory.INDEX_NAME);
                add(ServiceInventory.INDEX_NAME);
                add(ServiceInstanceInventory.INDEX_NAME);
            }
        };

        if (whiteIndexList.contains(modelName)) {
            return new String[] { modelName };
        }

        DateTime startDT = DurationUtils.INSTANCE.startTimeBucket2DateTime(downsampling, startTB);
        DateTime endDT = DurationUtils.INSTANCE.endTimeBucket2DateTime(downsampling, endTB);
        if (endDT.isAfter(startDT)) {
            switch (downsampling) {
                case Month:
                    while (endDT.isAfter(startDT)) {
                        String indexName = build(downsampling, modelName) + "-" + YYYYMM.print(startDT);
                        indexNameList.add(indexName);
                        startDT = startDT.plusMonths(1);
                    }
                    break;
                case Day:
                    while (endDT.isAfter(startDT)) {
                        String indexName = build(downsampling, modelName) + "-" + YYYYMMDD.print(startDT);
                        indexNameList.add(indexName);
                        startDT = startDT.plusDays(1);
                    }
                    break;
                case Hour:
                    //current hour index is also suffix with YYYYMMDD
                    while (endDT.isAfter(startDT)) {
                        String indexName = build(downsampling, modelName) + "-" + YYYYMMDD.print(startDT);
                        indexNameList.add(indexName);
                        startDT = startDT.plusDays(1);
                    }
                    break;
                case Minute:
                    //current minute index is also suffix with YYYYMMDD
                    while (endDT.isAfter(startDT)) {
                        String indexName = build(downsampling, modelName) + "-" + YYYYMMDD.print(startDT);
                        indexNameList.add(indexName);
                        startDT = startDT.plusDays(1);
                    }
                    break;
                case Second:
                    //current second index is also suffix with YYYYMMDD
                    while (endDT.isAfter(startDT)) {
                        String indexName = build(downsampling, modelName) + "-" + YYYYMMDD.print(startDT);
                        indexNameList.add(indexName);
                        startDT = startDT.plusDays(1);
                    }
                    break;
                default:
                    indexNameList.add(modelName);
            }
        }
        return indexNameList.toArray(new String[0]);
    }
}

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

package org.apache.skywalking.apm.collector.remote.grpc.service;

import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.remote.grpc.proto.*;
import org.apache.skywalking.apm.collector.remote.service.RemoteSerializeService;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteSerializeService implements RemoteSerializeService<RemoteData.Builder> {

    @Override public RemoteData.Builder serialize(org.apache.skywalking.apm.collector.core.data.RemoteData data) {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        for (int i = 0; i < data.getDataStringsCount(); i++) {
            if (StringUtils.isNotEmpty(data.getDataString(i))) {
                remoteBuilder.addDataStrings(data.getDataString(i));
            } else {
                remoteBuilder.addDataStrings(Const.EMPTY_STRING);
            }
        }

        for (int i = 0; i < data.getDataIntegersCount(); i++) {
            remoteBuilder.addDataIntegers(data.getDataInteger(i));
        }

        for (int i = 0; i < data.getDataLongsCount(); i++) {
            remoteBuilder.addDataLongs(data.getDataLong(i));
        }

        for (int i = 0; i < data.getDataDoublesCount(); i++) {
            remoteBuilder.addDataDoubles(data.getDataDouble(i));
        }

        for (int i = 0; i < data.getDataStringListsCount(); i++) {
            StringList.Builder stringList = StringList.newBuilder();
            data.getDataStringList(i).forEach(stringList::addValue);
            remoteBuilder.addDataStringLists(stringList);
        }

        for (int i = 0; i < data.getDataLongListsCount(); i++) {
            LongList.Builder longList = LongList.newBuilder();
            data.getDataLongList(i).forEach(longList::addValue);
            remoteBuilder.addDataLongLists(longList);
        }

        for (int i = 0; i < data.getDataIntegerListsCount(); i++) {
            IntegerList.Builder integerList = IntegerList.newBuilder();
            data.getDataIntegerList(i).forEach(integerList::addValue);
            remoteBuilder.addDataIntegerLists(integerList);
        }

        for (int i = 0; i < data.getDataDoubleListsCount(); i++) {
            DoubleList.Builder doubleList = DoubleList.newBuilder();
            data.getDataDoubleList(i).forEach(doubleList::addValue);
            remoteBuilder.addDataDoubleLists(doubleList);
        }

        return remoteBuilder;
    }
}

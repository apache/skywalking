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

import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.apache.skywalking.apm.collector.remote.service.RemoteSerializeService;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteSerializeService implements RemoteSerializeService<RemoteData.Builder> {

    @Override public RemoteData.Builder serialize(org.apache.skywalking.apm.collector.core.data.RemoteData data) {
        RemoteData.Builder builder = RemoteData.newBuilder();
        for (int i = 0; i < data.getDataStringsCount(); i++) {
            if (StringUtils.isNotEmpty(data.getDataString(i))) {
                builder.addDataStrings(data.getDataString(i));
            } else {
                builder.addDataStrings(Const.EMPTY_STRING);
            }
        }
        for (int i = 0; i < data.getDataIntegersCount(); i++) {
            builder.addDataIntegers(data.getDataInteger(i));
        }
        for (int i = 0; i < data.getDataLongsCount(); i++) {
            builder.addDataLongs(data.getDataLong(i));
        }
        for (int i = 0; i < data.getDataDoublesCount(); i++) {
            builder.addDataDoubles(data.getDataDouble(i));
        }
        return builder;
    }
}

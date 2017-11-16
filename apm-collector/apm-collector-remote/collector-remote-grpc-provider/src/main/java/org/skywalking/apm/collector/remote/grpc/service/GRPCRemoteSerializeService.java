/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.remote.grpc.service;

import com.google.protobuf.ByteString;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.remote.service.RemoteSerializeService;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteSerializeService implements RemoteSerializeService<RemoteData.Builder> {

    @Override public RemoteData.Builder serialize(Data data) {
        RemoteData.Builder builder = RemoteData.newBuilder();
        for (int i = 0; i < data.getDataStringsCount(); i++) {
            builder.setDataStrings(i, data.getDataString(i));
        }
        for (int i = 0; i < data.getDataIntegersCount(); i++) {
            builder.setDataIntegers(i, data.getDataInteger(i));
        }
        for (int i = 0; i < data.getDataLongsCount(); i++) {
            builder.setDataLongs(i, data.getDataLong(i));
        }
        for (int i = 0; i < data.getDataBooleansCount(); i++) {
            builder.setDataBooleans(i, data.getDataBoolean(i));
        }
        for (int i = 0; i < data.getDataDoublesCount(); i++) {
            builder.setDataDoubles(i, data.getDataDouble(i));
        }
        for (int i = 0; i < data.getDataBytesCount(); i++) {
            builder.setDataBytes(i, ByteString.copyFrom(data.getDataBytes(i)));
        }
        return builder;
    }
}

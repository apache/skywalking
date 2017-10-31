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

package org.skywalking.apm.collector.remote.grpc.data;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.service.SerializableAndDeserialize;

/**
 * @author peng-yongsheng
 */
public abstract class GRPCData implements SerializableAndDeserialize<RemoteData, RemoteData.Builder> {

    protected Data build(RemoteData remoteData) {
        return new Data(remoteData.getDataStrings(0), remoteData.getStringCapacity(), remoteData.getLongCapacity(), remoteData.getDoubleCapacity(), remoteData.getIntegerCapacity(), remoteData.getBooleanCapacity(), remoteData.getByteCapacity());
    }
}

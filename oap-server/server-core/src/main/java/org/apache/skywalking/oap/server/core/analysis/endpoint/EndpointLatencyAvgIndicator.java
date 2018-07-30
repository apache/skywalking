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

package org.apache.skywalking.oap.server.core.analysis.endpoint;

import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.AvgIndicator;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;

/**
 * @author peng-yongsheng
 */
public class EndpointLatencyAvgIndicator extends AvgIndicator {

    @Setter @Getter private int id;

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + id;
        result = 31 * result + (int)getTimeBucket();
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        EndpointLatencyAvgIndicator indicator = (EndpointLatencyAvgIndicator)obj;
        if (id != indicator.id)
            return false;
        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.setDataIntegers(0, getId());
        remoteBuilder.setDataIntegers(1, getCount());

        remoteBuilder.setDataLongs(0, getTimeBucket());
        remoteBuilder.setDataLongs(1, getSummation());
        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {
        setId(remoteData.getDataIntegers(0));
        setCount(remoteData.getDataIntegers(1));

        setTimeBucket(remoteData.getDataLongs(0));
        setSummation(remoteData.getDataLongs(1));
    }
}

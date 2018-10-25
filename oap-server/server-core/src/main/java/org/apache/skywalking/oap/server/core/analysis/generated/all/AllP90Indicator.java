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

package org.apache.skywalking.oap.server.core.analysis.generated.all;

import java.util.*;
import org.apache.skywalking.oap.server.core.alarm.AlarmMeta;
import org.apache.skywalking.oap.server.core.alarm.AlarmSupported;
import org.apache.skywalking.oap.server.core.analysis.indicator.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorType;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.annotation.*;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.source.Scope;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
@IndicatorType
@StreamData
@StorageEntity(name = "all_p90", builder = AllP90Indicator.Builder.class)
public class AllP90Indicator extends P90Indicator implements AlarmSupported {


    @Override public String id() {
        String splitJointId = String.valueOf(getTimeBucket());
        return splitJointId;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + (int)getTimeBucket();
        return result;
    }

    @Override public int remoteHashCode() {
        int result = 17;
        return result;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        AllP90Indicator indicator = (AllP90Indicator)obj;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getTimeBucket());


        remoteBuilder.setDataIntegers(0, getValue());
        remoteBuilder.setDataIntegers(1, getPrecision());
        getDetailGroup().forEach(element -> remoteBuilder.addDataIntLongPairList(element.serialize()));

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {

        setTimeBucket(remoteData.getDataLongs(0));


        setValue(remoteData.getDataIntegers(0));
        setPrecision(remoteData.getDataIntegers(1));

        setDetailGroup(new IntKeyLongValueArray(30));
        remoteData.getDataIntLongPairListList().forEach(element -> {
            getDetailGroup().add(new IntKeyLongValue(element.getKey(), element.getValue()));
        });

    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("all_p90", Scope.All);
    }

    @Override
    public Indicator toHour() {
        AllP90Indicator indicator = new AllP90Indicator();
        indicator.setValue(this.getValue());
        indicator.setPrecision(this.getPrecision());
        org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray newValue = new org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray();
        newValue.copyFrom(this.getDetailGroup());
        indicator.setDetailGroup(newValue);
        indicator.setTimeBucket(toTimeBucketInHour());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        AllP90Indicator indicator = new AllP90Indicator();
        indicator.setValue(this.getValue());
        indicator.setPrecision(this.getPrecision());
        org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray newValue = new org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray();
        newValue.copyFrom(this.getDetailGroup());
        indicator.setDetailGroup(newValue);
        indicator.setTimeBucket(toTimeBucketInDay());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        AllP90Indicator indicator = new AllP90Indicator();
        indicator.setValue(this.getValue());
        indicator.setPrecision(this.getPrecision());
        org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray newValue = new org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray();
        newValue.copyFrom(this.getDetailGroup());
        indicator.setDetailGroup(newValue);
        indicator.setTimeBucket(toTimeBucketInMonth());
        return indicator;
    }

    public static class Builder implements StorageBuilder<AllP90Indicator> {

        @Override public Map<String, Object> data2Map(AllP90Indicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("value", storageData.getValue());
            map.put("precision", storageData.getPrecision());
            map.put("detail_group", storageData.getDetailGroup());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public AllP90Indicator map2Data(Map<String, Object> dbMap) {
            AllP90Indicator indicator = new AllP90Indicator();
            indicator.setValue(((Number)dbMap.get("value")).intValue());
            indicator.setPrecision(((Number)dbMap.get("precision")).intValue());
            indicator.setDetailGroup(new org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray((String)dbMap.get("detail_group")));
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}

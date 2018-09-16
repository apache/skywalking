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
@StorageEntity(name = "all_heatmap", builder = AllHeatmapIndicator.Builder.class)
public class AllHeatmapIndicator extends ThermodynamicIndicator implements AlarmSupported {


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

        AllHeatmapIndicator indicator = (AllHeatmapIndicator)obj;

        if (getTimeBucket() != indicator.getTimeBucket())
            return false;

        return true;
    }

    @Override public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.setDataLongs(0, getTimeBucket());


        remoteBuilder.setDataIntegers(0, getStep());
        remoteBuilder.setDataIntegers(1, getNumOfSteps());
        getDetailGroup().forEach(element -> remoteBuilder.addDataIntLongPairList(element.serialize()));

        return remoteBuilder;
    }

    @Override public void deserialize(RemoteData remoteData) {

        setTimeBucket(remoteData.getDataLongs(0));


        setStep(remoteData.getDataIntegers(0));
        setNumOfSteps(remoteData.getDataIntegers(1));

        setDetailGroup(new ArrayList<>(30));
        remoteData.getDataIntLongPairListList().forEach(element -> {
            getDetailGroup().add(new IntKeyLongValue(element.getKey(), element.getValue()));
        });

    }

    @Override public AlarmMeta getAlarmMeta() {
        return new AlarmMeta("all_heatmap", Scope.All);
    }

    @Override
    public Indicator toHour() {
        AllHeatmapIndicator indicator = new AllHeatmapIndicator();
        indicator.setTimeBucket(toTimeBucketInHour());
        indicator.setStep(this.getStep());
        indicator.setNumOfSteps(this.getNumOfSteps());
        indicator.setDetailGroup(this.getDetailGroup());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toDay() {
        AllHeatmapIndicator indicator = new AllHeatmapIndicator();
        indicator.setTimeBucket(toTimeBucketInDay());
        indicator.setStep(this.getStep());
        indicator.setNumOfSteps(this.getNumOfSteps());
        indicator.setDetailGroup(this.getDetailGroup());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    @Override
    public Indicator toMonth() {
        AllHeatmapIndicator indicator = new AllHeatmapIndicator();
        indicator.setTimeBucket(toTimeBucketInMonth());
        indicator.setStep(this.getStep());
        indicator.setNumOfSteps(this.getNumOfSteps());
        indicator.setDetailGroup(this.getDetailGroup());
        indicator.setTimeBucket(this.getTimeBucket());
        return indicator;
    }

    public static class Builder implements StorageBuilder<AllHeatmapIndicator> {

        @Override public Map<String, Object> data2Map(AllHeatmapIndicator storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put("step", storageData.getStep());
            map.put("num_of_steps", storageData.getNumOfSteps());
            map.put("detail_group", storageData.getDetailGroup());
            map.put("time_bucket", storageData.getTimeBucket());
            return map;
        }

        @Override public AllHeatmapIndicator map2Data(Map<String, Object> dbMap) {
            AllHeatmapIndicator indicator = new AllHeatmapIndicator();
            indicator.setStep(((Number)dbMap.get("step")).intValue());
            indicator.setNumOfSteps(((Number)dbMap.get("num_of_steps")).intValue());
            indicator.setDetailGroup((org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray)dbMap.get("detail_group"));
            indicator.setTimeBucket(((Number)dbMap.get("time_bucket")).longValue());
            return indicator;
        }
    }
}

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

package org.apache.skywalking.library.banyandb.v1.client;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;

/**
 * MeasureQueryResponse represents the measure query result.
 */
public class MeasureQueryResponse {
    @Getter
    private final List<DataPoint> dataPoints;

    @Getter
    private final Trace trace;

    MeasureQueryResponse(BanyandbMeasure.QueryResponse response) {
        final List<BanyandbMeasure.DataPoint> dataPointList = response.getDataPointsList();
        this.dataPoints = new ArrayList<>(dataPointList.size());
        for (final BanyandbMeasure.DataPoint dp : dataPointList) {
            dataPoints.add(DataPoint.create(dp));
        }
        this.trace = Trace.convertFromProto(response.getTrace());
    }

    /**
     * @return size of the response set.
     */
    public int size() {
        return dataPoints.size();
    }
}

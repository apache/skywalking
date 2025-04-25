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

package org.apache.skywalking.oap.server.core.query.input;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;

import java.util.List;

@Getter
@Setter
@ToString
public class Duration {
    private String start;
    private String end;
    private Step step;
    private boolean coldStage = false;

    /**
     * See {@link DurationUtils#convertToTimeBucket(Step, String)}
     */
    public long getStartTimeBucket() {
        return DurationUtils.INSTANCE.convertToTimeBucket(step, start);
    }

    /**
     * See {@link DurationUtils#convertToTimeBucket(Step, String)}
     */
    public long getEndTimeBucket() {
        return DurationUtils.INSTANCE.convertToTimeBucket(step, end);
    }

    public long getStartTimestamp() {
        return DurationUtils.INSTANCE.startTimeToTimestamp(step, start);
    }

    public long getEndTimestamp() {
        return DurationUtils.INSTANCE.endTimeToTimestamp(step, end);
    }

    public long getStartTimeBucketInSec() {
        return DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(step, start);
    }

    public long getEndTimeBucketInSec() {
        return DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(step, end);
    }

    public long getStartTimeBucketInMin() {
        return DurationUtils.INSTANCE.startTimeDurationToMinuteTimeBucket(step, start);
    }

    public long getEndTimeBucketInMin() {
        return DurationUtils.INSTANCE.endTimeDurationToMinuteTimeBucket(step, end);
    }

    /**
     * Assemble time point based on {@link #step} and {@link #start} / {@link #end}
     */
    public List<PointOfTime> assembleDurationPoints() {
        return DurationUtils.INSTANCE.getDurationPoints(step, getStartTimeBucket(), getEndTimeBucket());
    }
}

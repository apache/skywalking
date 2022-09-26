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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;

@Getter
@Setter
/**
 * To optimize the query, the range of start and end times will be trimmed to [TTL_deadLine < time <= CurrentTime].
 */
public class Duration {
    private String start;
    private String end;
    private Step step;

    /**
     * See {@link DurationUtils#trimToStartTimeBucket(Step, String, boolean)}
     */
    public long getStartTimeBucket(boolean isRecord) {
        return DurationUtils.INSTANCE.trimToStartTimeBucket(step, start, isRecord);
    }

    /**
     * See {@link DurationUtils#trimToEndTimeBucket(Step, String)}
     */
    public long getEndTimeBucket() {
        return DurationUtils.INSTANCE.trimToEndTimeBucket(step, end);
    }

    public long getStartTimestamp(boolean isRecord) {
        return DurationUtils.INSTANCE.startTimeToTimestamp(step, DurationUtils.INSTANCE.trimToStartTimeBucket(step, start, isRecord));
    }

    public long getEndTimestamp() {
        return DurationUtils.INSTANCE.endTimeToTimestamp(step, DurationUtils.INSTANCE.trimToEndTimeBucket(step, end));
    }

    public long getStartTimeBucketInSec(boolean isRecord) {
        return DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(
            step, DurationUtils.INSTANCE.trimToStartTimeBucket(step, start, isRecord));
    }

    public long getEndTimeBucketInSec() {
        return DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(
            step, DurationUtils.INSTANCE.trimToEndTimeBucket(step, end));
    }

    /**
     * Assemble time point based on {@link #step} and {@link #start} / {@link #end}
     */
    public List<PointOfTime> assembleDurationPoints(boolean isRecord) {
        return DurationUtils.INSTANCE.getDurationPoints(step, getStartTimeBucket(isRecord), getEndTimeBucket());
    }
}

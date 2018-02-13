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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler.mock;

import java.util.LinkedList;
import java.util.List;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public enum TimeBuilder {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(TimeBuilder.class);

    private Duration[] durations = {
        new Duration("2017-01-01T00:02:01.001", "2017-01-01T00:05:01.001", 2),
        new Duration("2017-02-01T00:02:01.001", "2017-02-01T00:05:01.001", 2),
        new Duration("2017-03-01T00:02:01.001", "2017-03-01T00:05:01.001", 2),

        new Duration("2017-04-01T00:02:01.001", "2017-04-01T00:05:01.001", 2),
        new Duration("2017-04-02T00:02:01.001", "2017-04-02T00:05:01.001", 2),
        new Duration("2017-04-03T00:02:01.001", "2017-04-03T00:05:01.001", 2),

        new Duration("2017-05-01T08:02:01.001", "2017-05-01T08:05:01.001", 2),
        new Duration("2017-05-01T09:02:01.001", "2017-05-01T09:05:01.001", 2),
        new Duration("2017-05-01T10:02:01.001", "2017-05-01T10:05:01.001", 2),

        new Duration("2017-06-01T10:02:01.001", "2017-06-01T10:05:01.001", 20),
    };

    public Long[] generateTimes() {
        List<Long> times = new LinkedList<>();

        for (Duration duration : durations) {
            DateTime start = new DateTime(duration.getStart());
            DateTime end = new DateTime(duration.getEnd());

            while (!start.isAfter(end)) {
                for (int i = 0; i < duration.getTps(); i++) {
                    times.add(start.getMillis());
                }
                start = start.plusSeconds(1);
            }
        }

        return times.toArray(new Long[0]);
    }

    class Duration {
        private String start;
        private String end;
        private int tps;

        Duration(String start, String end, int tps) {
            this.start = start;
            this.end = end;
            this.tps = tps;
        }

        String getStart() {
            return start;
        }

        String getEnd() {
            return end;
        }

        int getTps() {
            return tps;
        }
    }

    public static void main(String[] args) {
        Long[] times = TimeBuilder.INSTANCE.generateTimes();

        for (Long time : times) {
            DateTime dateTime = new DateTime(time);
            logger.debug("{}-{}-{} {}:{}:{} {}", dateTime.year().getAsText(), dateTime.monthOfYear().getAsString(),
                dateTime.dayOfMonth().getAsText(), dateTime.hourOfDay().getAsText(),
                dateTime.minuteOfHour().getAsText(), dateTime.secondOfMinute().getAsText(),
                dateTime.millisOfSecond().getAsText());
        }
    }
}

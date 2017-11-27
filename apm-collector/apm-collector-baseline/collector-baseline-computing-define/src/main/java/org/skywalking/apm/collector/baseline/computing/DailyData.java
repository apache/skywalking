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

package org.skywalking.apm.collector.baseline.computing;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * @author zhang-chen
 */
class DailyData {

    private static final int TOTAL_SECONDS_IN_DAY = 86400;

    /**
     * minute duration: 60
     * five minute duration: 300
     * hour duration: 3600
     */
    final int duration;

    final int[] data;

    protected DailyData(int duration, TimeUnit timeUnit) {
        this.duration = (int)timeUnit.toSeconds(duration);
        data = new int[TOTAL_SECONDS_IN_DAY / this.duration];
    }

    protected DailyData(int[] data) {
        this.data = data;
        this.duration = TOTAL_SECONDS_IN_DAY / data.length;
    }

    /**
     * add one time point data into daily metric
     *
     * @param data metric data, like response time, total calls or error calls
     * @param timestamp measured in seconds, from midnight, January 1, 1970 UTC.
     */
    public void addData(int data, int timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(1000L * timestamp);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int start = (int)(c.getTimeInMillis() / 1000);
        this.data[(timestamp - start) / duration] = data;
    }

    public int[] getData() {
        return data;
    }

    public int getDuration() {
        return duration;
    }

    public int length() {
        return data.length;
    }
}

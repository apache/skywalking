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

package org.apache.skywalking.apm.collector.instrument.tools;

/**
 * @author peng-yongsheng
 */
public class Metric {

    private String metricName;
    private long avg;
    private long rate;
    private long calls;
    private long total;

    String getMetricName() {
        return metricName;
    }

    void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    long getAvg() {
        return avg;
    }

    void setAvg(long avg) {
        this.avg = avg;
    }

    long getRate() {
        return rate;
    }

    void setRate(long rate) {
        this.rate = rate;
    }

    long getCalls() {
        return calls;
    }

    void setCalls(long calls) {
        this.calls = calls;
    }

    long getTotal() {
        return total;
    }

    String getTotalWithUnit() {
        return transport(total);
    }

    void setTotal(long total) {
        this.total = total;
    }

    void merge(Metric metric) {
        this.total = this.total + metric.getTotal();
        this.calls = this.calls + metric.getCalls();
        this.avg = this.total / this.calls;
    }

    private String transport(long nanoseconds) {
        long ns2ms = 1000000;
        long ns2s = ns2ms * 1000;
        long ns2m = ns2s * 60;

        if (ns2ms <= nanoseconds && nanoseconds < ns2s) {
            return nanoseconds / ns2ms + "(ms)";
        } else if (ns2s <= nanoseconds && nanoseconds < ns2m) {
            return nanoseconds / ns2s + "(s)";
        } else if (nanoseconds >= ns2m) {
            return nanoseconds / ns2m + "(m)";
        }
        return nanoseconds + "(ns)";
    }
}

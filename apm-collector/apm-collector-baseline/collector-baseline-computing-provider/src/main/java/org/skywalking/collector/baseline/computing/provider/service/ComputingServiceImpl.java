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

package org.skywalking.collector.baseline.computing.provider.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.skywalking.apm.collector.baseline.computing.Metric;
import org.skywalking.apm.collector.baseline.computing.service.ComputingService;

/**
 * @author zhang-chen
 */
public class ComputingServiceImpl implements ComputingService {

    public static final int DEFAULT_DISCARD = 0;
    public static final int DEFAULT_EXTENT = 3;
    public static final int DEFAULT_SLOPE = 2;

    /**
     * how many highest and lowest value will be discarded, when computing average. example: we have eight days original
     * performance data, if we set discard as two, two highest value and two lowest value will be discarded in order to
     * ensure the baseline won't disturb by some extreme data. "discard" must be even number
     */
    private int discard;

    /**
     * how many nearby values will be used to computing gaussian distribution in one day performance data if we set
     * extent as three, three values on the left and three values on the right will impact currently computing
     * value "extent" must be odd number
     */
    private int extent;

    /**
     * how much impact by nearby values when computing gaussian distribution
     * the greater the slope, the greater the impact
     * slope must greater than 0 and less than 50
     */
    private int slope;

    public ComputingServiceImpl() {
        this(DEFAULT_DISCARD, DEFAULT_EXTENT, DEFAULT_SLOPE);
    }

    public ComputingServiceImpl(int discard, int extent, int slope) {
        if (discard < 0) {
            throw new IllegalArgumentException("argument discard value must be positive integer");
        }
        if (extent < 0) {
            throw new IllegalArgumentException("argument extend value must be positive integer");
        }
        if (slope < 0 || slope > 50) {
            throw new IllegalArgumentException("argument slope value must be positive integer and less than fifty");
        }
        this.discard = discard;
        this.extent = extent;
        this.slope = slope;
    }

    @Override
    public List<Metric> compute(List<Metric>[] metrics) {
        checkArgs(metrics);
        List<Metric>[] gaussian = new List[metrics.length];
        for (int i = 0; i < metrics.length; i++) {
            gaussian[i] = gaussian(metrics[i]);
        }
        return avg(gaussian);
    }

    private List<Metric> gaussian(List<Metric> metrics) {
        List<Metric> gaussianMetrics = new ArrayList<>(metrics.size());
        int mid = extent / 2 + 1;
        int size = metrics.size();
        double[] weight = assignWeight(extent, slope);
        for (int i = 0; i < size; i++) {
            double call = 0, avg = 0;
            //computing center point value with nearby point by gaussian
            for (int j = 1; j <= extent; j++) {
                //relative index on every extent loop
                int extentIndex = j - 1;
                //absolute index on original metrics list
                int index = Math.abs(i + (j - mid));
                if (index >= size) {
                    index = size - (index - size) - 1;
                }
                Metric originalMetric = metrics.get(index);
                call += originalMetric.getCall() * weight[extentIndex];
                avg += originalMetric.getAvg() * weight[extentIndex];
            }
            gaussianMetrics.add(new Metric((int)Math.round(call), (int)Math.round(avg)));
        }
        return gaussianMetrics;
    }

    /**
     * assign weight number for every extent value
     *
     * @param extent
     * @param slope
     * @return
     */
    private double[] assignWeight(final int extent, final int slope) {
        double avg = 1.0 / extent;//average weight
        double[] distributionSequence = new double[extent];
        Arrays.fill(distributionSequence, avg);
        int mid = extent / 2; //index of center point
        double mid2 = mid / 2.0;
        for (int i = 0; i <= mid; i++) {
            double delta = avg * (i - mid2) * slope * 0.01;
            distributionSequence[i] += delta;
            if ((extent - i - 1) == mid) {
                for (int j = 0; j < extent; j++) {
                    distributionSequence[j] += delta / extent;
                }
            } else {
                //update mirror value point
                distributionSequence[extent - i - 1] += delta;
            }
        }
        return distributionSequence;
    }

    private List<Metric> avg(List<Metric>[] base) {
        List<Metric> line = new ArrayList<Metric>(base[0].size());
        int size = base[0].size();
        int length = base.length;
        for (int i = 0; i < size; i++) {
            //create a new Averager for each column
            Averager gen = new Averager(length);
            for (int j = 0; j < length; j++) {
                Metric metric = base[j].get(i);
                gen.add(j, metric.getCall(), metric.getAvg());
            }
            gen.sort();
            Metric baseline = new Metric();
            baseline.setCall(gen.getCall());
            baseline.setAvg(gen.getAvg());
            line.add(baseline);
        }
        return line;
    }

    private void checkArgs(List<Metric>[] metrics) {
        if (metrics.length <= discard * 2) {
            throw new IllegalArgumentException("There is not enough data to computing baseline, input metrics array length must be greater than how many highest and lowest are discarded. " + metrics.length + " <= " + discard * 2);
        }
        int length = metrics[0].size();
        for (int i = 0; i < metrics.length; i++) {
            if (length != metrics[i].size()) {
                throw new IllegalArgumentException("The length of the List in Array is different");
            }
        }
    }

    /**
     * computing average
     */
    class Averager {

        int[] call;

        int[] avg;

        int length;

        int discard;

        public Averager(int length) {
            this(length, Math.min((length + 1) / 2 - 1, 3));
        }

        public Averager(int length, int discard) {
            this.length = length;
            this.discard = discard;
            call = new int[length];
            avg = new int[length];
        }

        public void add(int index, int total, int avg) {
            if (index >= 0 && index < length) {
                this.call[index] = total;
                this.avg[index] = avg;
            }
        }

        public void sort() {
            Arrays.sort(call);
            Arrays.sort(avg);
        }

        public int getCall() {
            return compute(call);
        }

        public int getAvg() {
            return compute(avg);
        }

        private int compute(int[] temp) {
            int startIndex = discard, endIndex = length - discard;
            int value = 0;
            int adjust = 0;
            for (int i = startIndex; i < endIndex; i++) {
                int v = temp[i];
                if (v == 0) {
                    adjust++;
                } else {
                    value += temp[i];
                }
            }
            int divisor = endIndex - startIndex - adjust;
            if (divisor == 0) {
                return 0;
            } else {
                return value / divisor;
            }
        }

    }
}

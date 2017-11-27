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

import java.util.Arrays;
import org.skywalking.apm.collector.baseline.computing.Baseline;
import org.skywalking.apm.collector.baseline.computing.DataOfSingleDay;
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

    @Override public <T extends DataOfSingleDay> Baseline compute(T[] metrics) {
        int[] baseline = computeBaseline(metrics);
        return new Baseline(baseline);
    }

    private int[] computeBaseline(DataOfSingleDay[] metrics) {
        checkArgs(metrics);
        int[][] gaussian = new int[metrics.length][];
        for (int i = 0; i < metrics.length; i++) {
            gaussian[i] = gaussian(metrics[i].getData(), extent, slope);
        }
        return computeBaseline(gaussian);
    }

    private int[] computeBaseline(int[][] gaussian) {
        int row = gaussian.length;
        int column = gaussian[0].length;
        int[] baseline = new int[column];
        for (int j = 0; j < column; j++) {
            int[] avgData = new int[row];
            for (int i = 0; i < row; i++) {
                avgData[i] = gaussian[i][j];
            }
            baseline[j] = avg(avgData, discard);
        }
        return baseline;
    }

    private int[] gaussian(int[] dailyMetrics, int extent, int slope) {
        int size = dailyMetrics.length;
        int[] gaussian = new int[size];
        int mid = extent / 2 + 1;
        double[] weight = assignWeight(extent, slope);
        for (int i = 0; i < size; i++) {
            double data = 0;
            //computing center point value with nearby point by gaussian
            for (int j = 1; j <= extent; j++) {
                //relative index on every extent loop
                int extentIndex = j - 1;
                //absolute index on original metrics list
                int index = Math.abs(i + (j - mid));
                if (index >= size) {
                    index = size - (index - size) - 1;
                }
                data += dailyMetrics[index] * weight[extentIndex];
            }
            gaussian[i] = (int)Math.round(data);
        }
        return gaussian;
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

    private int avg(int[] temp, int discard) {
        Arrays.sort(temp);
        int length = temp.length;
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

    private <T extends DataOfSingleDay> void checkArgs(T[] metrics) {
        if (metrics.length <= discard * 2) {
            throw new IllegalArgumentException("There is not enough data to computing baseline, input metrics array length must be greater than how many highest and lowest are discarded. " + metrics.length + " <= " + discard * 2);
        }
        int length = 0;
        for (int i = 0; i < metrics.length; i++) {
            if (i == 0) {
                length = metrics[i].length();
            } else {
                if (length != metrics[i].length()) {
                    throw new IllegalArgumentException("The length of the List in Array is different");
                }
            }
        }
    }

}

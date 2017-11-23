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
import org.skywalking.apm.collector.baseline.computing.Configuration;
import org.skywalking.apm.collector.baseline.computing.Metric;
import org.skywalking.apm.collector.baseline.computing.service.ComputingService;

/**
 * @author zhang-chen
 */
public class ComputingServiceImpl implements ComputingService {

    @Override
    public List<Metric> compute(List<Metric>[] metrics, Configuration conf) {
        checkArgs(metrics, conf);
//        Map<Integer, Metric>[] base = new HashMap[metrics.length];
//        for (List<Metric> metric : metrics) {
//            base[i] = computeGaussian(c, duration, appName, className, methodName, conf);
//        }
        return new ArrayList<Metric>();
    }

    private List<Metric> gaussian(List<Metric> metrics, final int count, final int slope) {
        int mid = count / 2 + 1;
        int size = metrics.size();
        double[] dst = incrementalDistribution(count, slope);
        for (int i = 0; i < size; i++) {
            Metric metric = metrics.get(i);
            double fail = 0, total = 0, avg = 0;
            for (int j = 1; j <= count; j++) {
                int dstIndex = j - 1;
                int index = Math.abs(i + (j - mid));
                if (index >= size) {
                    index = size - (index - size) - 1;
                }
                Metric indexMetric = metrics.get(index);
                total += indexMetric.getCall() * dst[dstIndex];
                avg += indexMetric.getAvg() * dst[dstIndex];
            }
            metric.setCall((int)Math.round(total));
            metric.setAvg((int)Math.round(avg));
        }
        return metrics;
    }

    /**
     * assign weight number for every extent value
     *
     * @param extent
     * @param slope
     * @return
     */
    private double[] incrementalDistribution(final int extent, final int slope) {
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

    private void checkArgs(List<Metric>[] metrics, Configuration conf) {
        if (conf.getExtent() < 0 || conf.getSlope() < 0 || conf.getDiscard() < 0) {
            throw new IllegalArgumentException("all baseline computing configuration arguments should be positive integer");
        }
        if (metrics.length < conf.getDiscard()) {
            throw new IllegalArgumentException("metrics array length must be greater than how many highest and lowest are discarded");
        }
        if (conf.getDiscard() % 2 == 1) {
            throw new IllegalArgumentException("filed discard value must be even number");
        }
        if (conf.getExtent() % 2 == 0) {
            throw new IllegalArgumentException("filed extend value must be odd number");
        }
        if (conf.getExtent() <= 3) {
            throw new IllegalArgumentException("filed extend value must be greater than three");
        }
        if (conf.getSlope() > 50) {
            throw new IllegalArgumentException("filed slope value must be less than or equal to fifty");
        }
    }

}

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

/**
 * @author zhang-chen
 */
public class Configuration {

    /**
     * how many highest and lowest value will be discarded, when computing average. example: we have eight days original
     * performance data, if we set discard as four, two highest value and two lowest value will be discarded in order to
     * ensure the baseline won't disturb by some extreme data. "discard" must be even number
     */
    int discard;

    /**
     * how many nearby values will be used to computing gaussian distribution in one day performance data if we set
     * extent as five, two(extent / 2) values on the left and two values on the right will impact currently computing
     * value "extent" must be odd number
     */
    int extent;

    /**
     * how much impact by nearby values when computing gaussian distribution
     * the greater the slope, the greater the impact
     * slope must greater than 0 and less than 50
     */
    int slope;

    public int getDiscard() {
        return discard;
    }

    public void setDiscard(int discard) {
        this.discard = discard;
    }

    public int getExtent() {
        return extent;
    }

    public void setExtent(int extent) {
        this.extent = extent;
    }

    public int getSlope() {
        return slope;
    }

    public void setSlope(int slope) {
        this.slope = slope;
    }
}

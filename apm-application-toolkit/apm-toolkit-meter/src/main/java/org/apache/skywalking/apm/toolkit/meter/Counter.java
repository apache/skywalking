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

package org.apache.skywalking.apm.toolkit.meter;

/**
 * A counter is a cumulative metric that represents a single monotonically increasing counter whose value can only increase or be reset to zero on restart.
 */
public interface Counter extends BaseMeter {

    void increment(double count);

    double get();

    /**
     * Counter mode
     */
    enum Mode {
        /**
         * Increase single value, report the real value
         */
        INCREMENT,

        /**
         * Rate with previous value when report
         */
        RATE
    }

    interface Builder extends BaseBuilder<Builder, Counter> {
        /**
         * Setting counter mode
         */
        Builder mode(Mode mode);
    }
}

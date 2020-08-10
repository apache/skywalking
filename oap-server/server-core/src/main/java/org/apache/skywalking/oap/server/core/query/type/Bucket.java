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

package org.apache.skywalking.oap.server.core.query.type;

/**
 * @since 8.0.0
 */
public class Bucket {
    public static final String INFINITE_NEGATIVE = "infinite-";
    public static final String INFINITE_POSITIVE = "infinite+";

    /**
     * The min value of this bucket representing.
     */
    private String min = "0";
    /**
     * The max value of this bucket representing.
     */
    private String max = "0";

    public Bucket() {
    }

    public Bucket(int min, int max) {
        setMin(min);
        setMax(max);
    }

    public Bucket setMin(int min) {
        this.min = String.valueOf(min);
        return this;
    }

    public Bucket setMax(int max) {
        this.max = String.valueOf(max);
        return this;
    }

    public Bucket infiniteMin() {
        this.min = INFINITE_NEGATIVE;
        return this;
    }

    public Bucket infiniteMax() {
        this.max = INFINITE_POSITIVE;
        return this;
    }

    public boolean isInfiniteMin() {
        return INFINITE_NEGATIVE.equals(this.min);
    }

    public boolean isInfiniteMax() {
        return INFINITE_POSITIVE.equals(this.max);
    }

    public int duration() {
        if (isInfiniteMin()) {
            return Integer.MIN_VALUE;
        }
        if (isInfiniteMax()) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(this.max) - Integer.parseInt(this.min);
    }
}

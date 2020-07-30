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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

/**
 * Meter support calculate operation
 */
public interface MeterEvalOperation<FROM extends EvalData> {

    /**
     * Add value to meter
     */
    default EvalData add(double value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Add from eval data
     */
    default EvalData add(FROM data) {
        throw new UnsupportedOperationException();
    }

    /**
     * Minus from value
     */
    default EvalData minus(double value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Subtract from eval data
     */
    default EvalData minus(FROM data) {
        throw new UnsupportedOperationException();
    }

    /**
     * Multiply by value
     */
    default EvalData multiply(double value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Multiply from eval data
     */
    default EvalData multiply(FROM data) {
        throw new UnsupportedOperationException();
    }

    /**
     * Divide from value
     */
    default EvalData divide(double value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Mean from eval data
     */
    default EvalData divide(FROM data) {
        throw new UnsupportedOperationException();
    }

    /**
     * Scale the meter value.
     */
    default EvalData scale(Integer value) {
        throw new UnsupportedOperationException();
    }

    /**
     * IRate value from time range
     */
    default EvalData irate(String range) {
        throw new UnsupportedOperationException();
    }

    /**
     * Rate value from time range
     */
    default EvalData rate(String range) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get increase value from time range
     */
    default EvalData increase(String range) {
        throw new UnsupportedOperationException();
    }

}

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

package org.apache.skywalking.oap.server.receiver.meter.provider.process;

/**
 * Meter support calculate operation
 */
public interface MeterEvalOperation<FROM extends EvalData> {

    /**
     * Add value to meter
     */
    EvalData add(double value);

    /**
     * Add from eval data
     */
    EvalData add(FROM data);

    /**
     * Minus from value
     */
    EvalData minus(double value);

    /**
     * Subtract from eval data
     */
    EvalData minus(FROM data);

    /**
     * Multiply by value
     */
    EvalData multiply(double value);

    /**
     * Multiply from eval data
     */
    EvalData multiply(FROM data);

    /**
     * Divide from value
     */
    EvalData divide(double value);

    /**
     * Mean from eval data
     */
    EvalData divide(FROM data);

    /**
     * Scale the meter value.
     */
    EvalData scale(Integer value);

    /**
     * IRate value from time range
     */
    EvalData irate(String range);

    /**
     * Rate value from time range
     */
    EvalData rate(String range);

    /**
     * Get increase value from time range
     */
    EvalData increase(String range);

}

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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.model;

/**
 * A numeric literal on the right of a comparison.
 */
public interface TALNumberConditionValue extends TALConditionValue {
    double getValue();

    /**
     * @return the literal exactly as the rule wrote it, which is what preserves the declared numeric type: a bare
     * {@code 10000} stays an int where an {@code L}-suffixed one is a long. Null for a synthesized value.
     */
    String getLiteral();
}

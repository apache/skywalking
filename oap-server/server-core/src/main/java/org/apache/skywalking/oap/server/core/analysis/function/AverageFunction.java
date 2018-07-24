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

package org.apache.skywalking.oap.server.core.analysis.function;

import org.apache.skywalking.oap.server.core.analysis.operator.AverageOperator;

/**
 * @author peng-yongsheng
 */
public class AverageFunction implements Function<AverageOperator> {

    private static AverageFunction INSTANCE;

    private AverageFunction() {
    }

    public static AverageFunction getInstance() {
        if (INSTANCE == null)
            INSTANCE = new AverageFunction();

        return INSTANCE;
    }

    @Override public FunctionType type() {
        return FunctionType.Average;
    }

    @Override public void combine(AverageOperator opX, AverageOperator opY) {
        opX.setValue(opX.getValue() + opY.getValue());
        opX.setCount(opX.getCount() + opY.getCount());
    }
}

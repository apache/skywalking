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

package org.apache.skywalking.oap.server.core.analysis.operator;

import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.function.*;

/**
 * @author peng-yongsheng
 */
public class Aggregator {

    private final Function function;
    private final OperatorType operatorType;
    private final Map<Indicator, Indicator> cache;

    public Aggregator(Function function, OperatorType operatorType) {
        this.function = function;
        this.operatorType = operatorType;
        this.cache = new HashMap<>();
    }

    public void in(Indicator indicator) {
        if (cache.containsKey(indicator)) {
            combine(cache.get(indicator), indicator);
        }
    }

    public void out() {

    }

    public void combine(Indicator indX, Indicator indY) {
        switch (operatorType) {
            case Average:
                AverageFunction.getInstance().combine((AverageOperator)indX, (AverageOperator)indY);
            case Sum:
                SumFunction.getInstance().combine((SumOperator)indX, (SumOperator)indY);
        }
    }
}

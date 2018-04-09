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

package org.apache.skywalking.apm.collector.instrument;

import java.util.ArrayList;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class MetricTreePrintTestCase {

    @Test
    public void testPrint() {
        ServiceMetric table1PersistenceDay = MetricTree.INSTANCE.lookup("/persistence/onWork/table_1/day").getMetric(null);
        table1PersistenceDay.trace(1000, false, newArguments());

        ServiceMetric table1PersistenceHour = MetricTree.INSTANCE.lookup("/persistence/onWork/table_1/hour").getMetric(null);
        table1PersistenceHour.trace(2000, false, newArguments());

        ServiceMetric table1Aggregate = MetricTree.INSTANCE.lookup("/aggregate/onWork/table_1").getMetric(null);
        table1Aggregate.trace(3000, false, newArguments());

        ServiceMetric table2Aggregate = MetricTree.INSTANCE.lookup("/aggregate/onWork/table_2").getMetric(null);
        table2Aggregate.trace(4000, false, newArguments());

        MetricTree.INSTANCE.run();
    }

    private Object[] newArguments() {
        Object[] arguments = new Object[1];
        arguments[0] = new ArrayList<>(100);
        return arguments;
    }
}

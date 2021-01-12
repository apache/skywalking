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

package org.apache.skywalking.apm.commons.datacarrier.consumer;

import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.SampleData;

public class SampleConsumer implements IConsumer<SampleData> {
    public int i = 1;

    @Override
    public void init() {

    }

    @Override
    public void consume(List<SampleData> data) {
        for (SampleData one : data) {
            one.setIntValue(this.hashCode());
            ConsumerTest.BUFFER.offer(one);
        }
    }

    @Override
    public void onError(List<SampleData> data, Throwable t) {

    }

    @Override
    public void onExit() {

    }
}

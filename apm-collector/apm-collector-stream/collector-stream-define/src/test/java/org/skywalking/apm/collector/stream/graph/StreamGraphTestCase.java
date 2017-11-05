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

package org.skywalking.apm.collector.stream.graph;

import org.junit.Test;
import org.skywalking.apm.collector.storage.table.instance.InstPerformance;
import org.skywalking.apm.collector.storage.table.register.Application;

/**
 * @author peng-yongsheng
 */
public class StreamGraphTestCase {

    @Test
    public void test() {
        StreamGraph graph = new StreamGraph();
        graph.addNode(new Aggregator<InstPerformance, Application>() {
            @Override public void process(InstPerformance performance, Next<Application> next) {
                Application application = new Application("111");
                next.execute(application);
            }
        }).addNext(new Aggregator<Application, InstPerformance>() {
            @Override public void process(Application application, Next<InstPerformance> next) {

            }
        });

        InstPerformance instPerformance = new InstPerformance("111");
        graph.start(instPerformance);
    }
}

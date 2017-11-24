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

package org.skywalking.collector.baseline.computing.provider.service;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.baseline.computing.Metric;
import org.skywalking.apm.collector.baseline.computing.service.ComputingService;

/**
 * @author Zhang, Chen
 */
public class ComputingServiceTest {

    @Test
    public void computing() {
        int size = 100;
        ComputingService service = new ComputingServiceImpl();
        List<Metric>[] metrics = new List[8];
        for (int j = 0; j < metrics.length; j++) {
            metrics[j] = new ArrayList();
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < metrics.length; j++) {
                metrics[j].add(new Metric(i % 2 == 0 ? i : size - i, i + j));
            }
        }
        List<Metric> list = service.compute(metrics);
        Assert.assertNotNull(list);
        Assert.assertEquals(size, list.size());
    }

}

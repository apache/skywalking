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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixBaseTest;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixMetrics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ZabbixProtocolHandlerTest extends ZabbixBaseTest {

    @Override
    protected ZabbixMetrics buildZabbixMetrics() throws ModuleStartException, Exception {
        ZabbixMetrics metrics = mock(ZabbixMetrics.class);
        // mock zabbix metrics
        when(metrics.getAllMonitorMetricNames(any())).thenReturn(ImmutableSet.<String>builder().add("system.cpu.load[all,avg15]").build());
        when(metrics.convertMetrics(any())).thenReturn(ZabbixMetrics.ConvertStatics.EMPTY);
        return metrics;
    }

    /**
     * Test active tasks and agent data request and response
     */
    @Test
    public void testReceive() throws Throwable {
        // Verify Active Checks
        writeZabbixMessage("{\"request\":\"active checks\",\"host\":\"zabbix-test-agent\"}");
        assertZabbixActiveChecksRequest(0, "zabbix-test-agent");
        assertZabbixActiveChecksResponse(0, "system.cpu.load[all,avg15]");

        // Verify Agent data
        writeZabbixMessage("{\"request\":\"agent data\",\"session\":\"f32425dc61971760bf791f731931a92e\",\"data\":[{\"host\":\"zabbix-test-agent\",\"key\":\"system.cpu.load[all,avg15]\",\"value\":\"1.123\",\"id\":2,\"clock\":1609588563,\"ns\":87682907}],\"clock\":1609588568,\"ns\":102244476}");
        assertZabbixAgentDataRequest(1, "zabbix-test-agent", "system.cpu.load[all,avg15]");
        assertZabbixAgentDataResponse(2);
    }

    /**
     * Test error protocol
     */
    @Test
    public void testErrorProtocol() throws Throwable {
        // Simple header
        for (int i = 1; i < 4; i++) {
            assertNeedMoreInput(new byte[i]);
        }

        // Only header string
        assertNeedMoreInput(new byte[] {'Z', 'B', 'X', 'D'});

        // Header error
        assertWriteErrorProtocol(new byte[] {'Z', 'B', 'X', 'D', 2, 0, 0, 0, 0});
        assertWriteErrorProtocol(new byte[] {'Z', 'B', 'X', 'D', 2, 1, 0, 0, 0});

        // Need more content
        assertNeedMoreInput(new byte[] {'Z', 'B', 'X', 'D', 1, 5, 0, 0, 0, 1, 1, 1});

        // Empty data
        assertWriteErrorProtocol(buildZabbixRequestData(""));
        assertWriteErrorProtocol(buildZabbixRequestData("{}"));
        assertWriteErrorProtocol(buildZabbixRequestData("{\"test\": 1}"));
    }

}

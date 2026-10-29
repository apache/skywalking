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

package org.apache.skywalking.oap.server.core.analysis.manual.networkalias;

import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

public class NetworkAddressAliasSetupDispatcherTest {
    private MockedStatic<MetricsStreamProcessor> mockedProcessor;
    private MetricsStreamProcessor processor;

    @BeforeEach
    public void setUp() {
        processor = Mockito.mock(MetricsStreamProcessor.class);
        mockedProcessor = Mockito.mockStatic(MetricsStreamProcessor.class);
        mockedProcessor.when(MetricsStreamProcessor::getInstance).thenReturn(processor);
    }

    @AfterEach
    public void tearDown() {
        mockedProcessor.close();
    }

    @Test
    public void theUpdateBucketIsWhenOapReceivedTheSetupAndTheTimeBucketTheSpans() {
        // A segment that arrives an hour late, or from an agent whose clock is an hour behind.
        final long spanBucket = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis() - 3_600_000L);
        final NetworkAddressAliasSetup setup = new NetworkAddressAliasSetup();
        setup.setAddress("10.0.0.9:8080");
        setup.setRepresentService("cart");
        setup.setRepresentServiceNormal(true);
        setup.setRepresentServiceInstance("cart-1");
        setup.setTimeBucket(spanBucket);
        setup.prepare();

        final long before = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis());
        new NetworkAddressAliasSetupDispatcher().dispatch(setup);
        final long after = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis());

        final ArgumentCaptor<NetworkAddressAlias> captor = ArgumentCaptor.forClass(NetworkAddressAlias.class);
        verify(processor).in(captor.capture());
        final NetworkAddressAlias alias = captor.getValue();
        assertEquals(spanBucket, alias.getTimeBucket(), "storage retention keeps the span's minute");
        assertTrue(alias.getLastUpdateTimeBucket() >= before && alias.getLastUpdateTimeBucket() <= after,
            "the cache reload reads OAP's minute: " + alias.getLastUpdateTimeBucket());
    }
}

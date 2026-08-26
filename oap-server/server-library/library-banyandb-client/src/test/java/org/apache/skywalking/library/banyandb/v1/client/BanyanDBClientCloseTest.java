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

package org.apache.skywalking.library.banyandb.v1.client;

import io.grpc.Channel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

public class BanyanDBClientCloseTest {

    /**
     * The channel the stubs run on is an interceptor wrapper rather than a ManagedChannel. close() used to
     * return early on that, from inside the connection lock and before the matching unlock, so the lock was
     * left held and every later connect() or close() blocked forever.
     *
     * <p>The second call has to come from another thread: the lock is reentrant, so the thread that leaked it
     * re-acquires it without noticing and would make this test pass against the broken code.
     */
    @Test
    public void closeShouldReleaseTheConnectionLock() throws Exception {
        final BanyanDBClient client = new BanyanDBClient(new String[] {"127.0.0.1:17912"}, new Options());
        client.connect(mock(Channel.class));

        client.close();

        final ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            final Future<?> closedAgain = other.submit(() -> {
                client.close();
                return null;
            });
            // TimeoutException if the first close() walked out still holding the lock.
            assertDoesNotThrow(() -> closedAgain.get(10, TimeUnit.SECONDS));
        } finally {
            other.shutdownNow();
        }
    }
}

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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.library.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.MeasureBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureInsertRequest;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BanyanDBBatchDAOTest {
    /**
     * The end of a persistence round sends the measures it queued at once, instead of leaving them to the bulk's
     * own interval, and a measure enters the session cache only once its write is answered.
     */
    @Test
    public void theEndOfARoundSendsTheQueuedMeasuresAtOnce() {
        final BanyanDBStorageClient client = mock(BanyanDBStorageClient.class);
        final MeasureBulkWriteProcessor bulk = mock(MeasureBulkWriteProcessor.class);
        when(client.createMeasureBulkProcessor(anyInt(), anyInt(), anyInt())).thenReturn(bulk);
        final CompletableFuture<Void> unanswered = new CompletableFuture<>();
        when(bulk.add(any())).thenReturn(unanswered);
        final SessionCacheCallback callback = mock(SessionCacheCallback.class);
        final BanyanDBBatchDAO dao = new BanyanDBBatchDAO(client, 10000, 15, 1);

        dao.flush(Collections.singletonList(new BanyanDBMeasureInsertRequest(mock(MeasureWrite.class), callback)));
        dao.endOfFlush();

        verify(bulk).flush();
        verify(callback, never()).onInsertCompleted();
        unanswered.complete(null);
        verify(callback).onInsertCompleted();
    }

    /**
     * A round that queued no measure has no bulk to send.
     */
    @Test
    public void theEndOfARoundWithoutMeasuresSendsNothing() {
        final BanyanDBStorageClient client = mock(BanyanDBStorageClient.class);

        new BanyanDBBatchDAO(client, 10000, 15, 1).endOfFlush();

        verify(client, never()).createMeasureBulkProcessor(anyInt(), anyInt(), anyInt());
    }
}

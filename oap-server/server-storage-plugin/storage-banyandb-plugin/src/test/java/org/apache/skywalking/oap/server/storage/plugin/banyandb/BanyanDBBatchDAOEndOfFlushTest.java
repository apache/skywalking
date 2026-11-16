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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.skywalking.library.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.library.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.MeasureBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.StreamBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStreamInsertRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The end of a persistence round sends both bulks it queued into — the measures and the TopN records, which are
 * streams — even when one of the batches fails, and still reports the failure to the persistence timer.
 */
public class BanyanDBBatchDAOEndOfFlushTest {
    private final CompletionException measureRejected = new CompletionException(new IllegalStateException("measure"));
    private final CompletionException streamRejected = new CompletionException(new IllegalStateException("stream"));

    @Test
    public void sendsTheStreamBulkWhenTheMeasureBatchFails() {
        final Round round = new Round();
        doThrow(measureRejected).when(round.measures).flush();

        assertSame(measureRejected, assertThrows(CompletionException.class, round.dao::endOfFlush));
        verify(round.streams).flush();
    }

    @Test
    public void sendsTheMeasureBulkWhenTheStreamBatchFails() {
        final Round round = new Round();
        doThrow(streamRejected).when(round.streams).flush();

        assertSame(streamRejected, assertThrows(CompletionException.class, round.dao::endOfFlush));
        verify(round.measures).flush();
    }

    @Test
    public void reportsBothFailures() {
        final Round round = new Round();
        doThrow(measureRejected).when(round.measures).flush();
        doThrow(streamRejected).when(round.streams).flush();

        final CompletionException thrown = assertThrows(CompletionException.class, round.dao::endOfFlush);
        assertSame(measureRejected, thrown);
        assertArrayEquals(new Throwable[] {streamRejected}, thrown.getSuppressed());
        verify(round.measures).flush();
        verify(round.streams).flush();
    }

    /** A round that queued a measure and a TopN record, each into its own bulk. */
    private static final class Round {
        private final MeasureBulkWriteProcessor measures = mock(MeasureBulkWriteProcessor.class);
        private final StreamBulkWriteProcessor streams = mock(StreamBulkWriteProcessor.class);
        private final BanyanDBBatchDAO dao;

        private Round() {
            final BanyanDBStorageClient client = mock(BanyanDBStorageClient.class);
            when(client.createMeasureBulkProcessor(anyInt(), anyInt(), anyInt())).thenReturn(measures);
            when(client.createStreamBulkProcessor(anyInt(), anyInt(), anyInt())).thenReturn(streams);
            when(measures.add(any())).thenReturn(new CompletableFuture<>());
            when(streams.add(any())).thenReturn(new CompletableFuture<>());
            dao = new BanyanDBBatchDAO(client, 10000, 15, 1);
            dao.flush(Arrays.asList(
                new BanyanDBMeasureInsertRequest(mock(MeasureWrite.class), mock(SessionCacheCallback.class)),
                new BanyanDBStreamInsertRequest(mock(StreamWrite.class))
            ));
        }
    }
}

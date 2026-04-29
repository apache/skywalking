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

import com.google.protobuf.Duration;
import io.grpc.Channel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.schema.v1.BanyandbSchema;
import org.apache.skywalking.banyandb.schema.v1.SchemaBarrierServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.HandleExceptionsWith;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

/**
 * Client wrapper around BanyanDB's {@code SchemaWatcherService}. Replaces the legacy
 * "poll {@code findX} until you can read your own write" idiom with an authoritative
 * fence: the server-side watcher blocks until every data node has observed the target
 * schema state.
 *
 * <p>Three RPCs:
 * <ul>
 *   <li>{@link #awaitRevisionApplied} — block until every data node's local schema
 *       cache has caught up to a target {@code mod_revision}. Use this after a
 *       Create / Update where the response gave a non-zero revision; the etcd
 *       revision is global, so a single fence covers all schema mutations done
 *       under it.
 *   <li>{@link #awaitSchemaApplied} — block until specific keys are present at or
 *       above per-key revisions. Useful when the caller wants a precise key list
 *       confirmed (e.g., index rules + binding for a single measure).
 *   <li>{@link #awaitSchemaDeleted} — block until specific keys have disappeared
 *       from every data node's cache. Use after a Delete that returned
 *       {@code mod_revision == 0} (server did not record a tombstone) — the
 *       revision-based fence won't observe the deletion, so the caller falls back
 *       to a key-based wait.
 * </ul>
 *
 * <p>The legacy {@code findX}-poll path used to time-out after about 5 s before
 * declaring "schema not applied"; the server-side watcher RPCs themselves take a
 * timeout so the caller no longer needs an external retry loop.
 */
public final class SchemaWatcher {

    private final SchemaBarrierServiceGrpc.SchemaBarrierServiceBlockingStub stub;

    public SchemaWatcher(final Channel channel) {
        this.stub = SchemaBarrierServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Block until every data node has observed at least {@code minRevision}, or until
     * the timeout elapses. {@code minRevision == 0} returns immediately (no fence
     * needed). Returns the server's response so callers can inspect laggards on a
     * non-applied result.
     */
    public Result awaitRevisionApplied(final long minRevision,
                                       final java.time.Duration timeout) throws BanyanDBException {
        if (minRevision <= 0L) {
            return Result.applied();
        }
        final BanyandbSchema.AwaitRevisionAppliedResponse resp = HandleExceptionsWith.callAndTranslateApiException(() ->
            stub.awaitRevisionApplied(BanyandbSchema.AwaitRevisionAppliedRequest.newBuilder()
                .setMinRevision(minRevision)
                .setTimeout(toProto(timeout))
                .build()));
        return new Result(resp.getApplied(), resp.getLaggardsList());
    }

    /**
     * Block until every data node reports the listed keys present at or above their
     * per-key revisions. {@code minRevisions} entries pair positionally with
     * {@code keys}; pass {@code 0} for "any revision will do".
     */
    public Result awaitSchemaApplied(final List<BanyandbSchema.SchemaKey> keys,
                                     final List<Long> minRevisions,
                                     final java.time.Duration timeout) throws BanyanDBException {
        final BanyandbSchema.AwaitSchemaAppliedRequest.Builder req = BanyandbSchema.AwaitSchemaAppliedRequest.newBuilder()
            .addAllKeys(keys)
            .addAllMinRevisions(minRevisions)
            .setTimeout(toProto(timeout));
        final BanyandbSchema.AwaitSchemaAppliedResponse resp = HandleExceptionsWith.callAndTranslateApiException(() ->
            stub.awaitSchemaApplied(req.build()));
        return new Result(resp.getApplied(), resp.getLaggardsList());
    }

    /**
     * Block until every data node has removed the listed keys from its cache.
     * Use after a Delete that returned {@code mod_revision == 0}; the
     * revision-based fence cannot observe a deletion that didn't get a tombstone.
     */
    public Result awaitSchemaDeleted(final List<BanyandbSchema.SchemaKey> keys,
                                     final java.time.Duration timeout) throws BanyanDBException {
        final BanyandbSchema.AwaitSchemaDeletedResponse resp = HandleExceptionsWith.callAndTranslateApiException(() ->
            stub.awaitSchemaDeleted(BanyandbSchema.AwaitSchemaDeletedRequest.newBuilder()
                .addAllKeys(keys)
                .setTimeout(toProto(timeout))
                .build()));
        return new Result(resp.getApplied(), resp.getLaggardsList());
    }

    /** Convenience for the common single-key delete-wait. */
    public Result awaitSchemaDeleted(final BanyandbSchema.SchemaKey key,
                                     final java.time.Duration timeout) throws BanyanDBException {
        return awaitSchemaDeleted(Arrays.asList(key), timeout);
    }

    private static Duration toProto(final java.time.Duration d) {
        return Duration.newBuilder()
            .setSeconds(d.getSeconds())
            .setNanos(d.getNano())
            .build();
    }

    /**
     * Result of a watcher call. {@link #applied} is true iff every data node has
     * caught up; {@link #laggards} carries per-node detail when not.
     */
    @Getter
    @RequiredArgsConstructor
    public static final class Result {
        private final boolean applied;
        private final List<BanyandbSchema.NodeLaggard> laggards;

        public static Result applied() {
            return new Result(true, Collections.emptyList());
        }
    }
}

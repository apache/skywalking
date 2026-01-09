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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.protobuf.Timestamp;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Metadata;
import org.apache.skywalking.banyandb.common.v1.ServiceGrpc;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRuleBinding;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Subject;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Trace;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.apache.skywalking.banyandb.measure.v1.MeasureServiceGrpc;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty;
import org.apache.skywalking.banyandb.stream.v1.BanyandbStream;
import org.apache.skywalking.banyandb.stream.v1.StreamServiceGrpc;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.apache.skywalking.banyandb.trace.v1.TraceServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.auth.AuthInterceptor;
import org.apache.skywalking.library.banyandb.v1.client.grpc.HandleExceptionsWith;
import org.apache.skywalking.library.banyandb.v1.client.grpc.channel.ChannelManager;
import org.apache.skywalking.library.banyandb.v1.client.grpc.channel.DefaultChannelFactory;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.GroupMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.metadata.IndexRuleBindingMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.metadata.IndexRuleMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.metadata.MeasureMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.metadata.PropertyMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.metadata.ResourceExist;
import org.apache.skywalking.library.banyandb.v1.client.metadata.StreamMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.metadata.TopNAggregationMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.metadata.TraceMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.util.TimeUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * BanyanDBClient represents a client instance interacting with BanyanDB server.
 * This is built on the top of BanyanDB v1 gRPC APIs.
 *
 * <pre>{@code
 * // use `default` group
 * client = new BanyanDBClient("127.0.0.1", 17912);
 * // to send any request, a connection to the server must be estabilished
 * client.connect();
 * }</pre>
 */
@Slf4j
public class BanyanDBClient implements Closeable {
    public static final ZonedDateTime DEFAULT_EXPIRE_AT = ZonedDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private final String[] targets;
    /**
     * Options for server connection.
     */
    @Getter
    private final Options options;
    /**
     * gRPC connection.
     */
    @Getter
    private volatile Channel channel;
    /**
     * gRPC client stub
     */
    @Getter
    private StreamServiceGrpc.StreamServiceStub streamServiceStub;
    /**
     * gRPC client stub
     */
    @Getter
    private MeasureServiceGrpc.MeasureServiceStub measureServiceStub;
    /**
     * gRPC client stub
     */
    @Getter
    private TraceServiceGrpc.TraceServiceStub traceServiceStub;
    /**
     * gRPC future stub.
     */
    @Getter
    private StreamServiceGrpc.StreamServiceBlockingStub streamServiceBlockingStub;
    /**
     * gRPC future stub.
     */
    @Getter
    private MeasureServiceGrpc.MeasureServiceBlockingStub measureServiceBlockingStub;
    /**
     * gRPC future stub.
     */
    @Getter
    private TraceServiceGrpc.TraceServiceBlockingStub traceServiceBlockingStub;
    /**
     * The connection status.
     */
    private volatile boolean isConnected = false;
    /**
     * A lock to control the race condition in establishing and disconnecting network connection.
     */
    private final ReentrantLock connectionEstablishLock;

    /**
     * Create a BanyanDB client instance with a default options.
     *
     * @param targets server targets
     */
    public BanyanDBClient(String... targets) {
        this(targets, new Options());
    }

    /**
     * Create a BanyanDB client instance with a customized options.
     *
     * @param targets server targets
     * @param options customized options
     */
    public BanyanDBClient(String[] targets, Options options) {
        String[] tt = Preconditions.checkNotNull(targets, "targets");
        checkState(tt.length > 0, "targets' size must be more than 1");
        tt = Arrays.stream(tt).filter(t -> !Strings.isNullOrEmpty(t)).toArray(size -> new String[size]);
        checkState(tt.length > 0, "valid targets' size must be more than 1");
        this.targets = tt;
        this.options = options;
        this.connectionEstablishLock = new ReentrantLock();
    }

    /**
     * Construct a connection to the server.
     *
     * @throws IOException thrown if fail to create a connection
     */
    public void connect() throws IOException {
        connectionEstablishLock.lock();
        try {
            if (!isConnected) {
                URI[] addresses = new URI[this.targets.length];
                for (int i = 0; i < this.targets.length; i++) {
                        addresses[i] = URI.create("//" + this.targets[i]);
                }
                Channel rawChannel = ChannelManager.create(this.options.buildChannelManagerSettings(),
                                                           new DefaultChannelFactory(addresses, this.options));
                Channel interceptedChannel = rawChannel;
                // register auth interceptor
                String username = options.getUsername();
                String password = options.getPassword();
                if (StringUtil.isNotBlank(username) && StringUtil.isNotBlank(password)) {
                    interceptedChannel = ClientInterceptors.intercept(rawChannel,
                            new AuthInterceptor(username, password));
                }
                // Ensure this.channel is assigned only once.
                this.channel = interceptedChannel;
                streamServiceBlockingStub = StreamServiceGrpc.newBlockingStub(this.channel);
                measureServiceBlockingStub = MeasureServiceGrpc.newBlockingStub(this.channel);
                traceServiceBlockingStub = TraceServiceGrpc.newBlockingStub(this.channel);
                streamServiceStub = StreamServiceGrpc.newStub(this.channel);
                measureServiceStub = MeasureServiceGrpc.newStub(this.channel);
                traceServiceStub = TraceServiceGrpc.newStub(this.channel);
                isConnected = true;
            }
        } finally {
            connectionEstablishLock.unlock();
        }
    }

    @VisibleForTesting
    void connect(Channel channel) {
        connectionEstablishLock.lock();
        try {
            if (!isConnected) {
                this.channel = channel;
                streamServiceBlockingStub = StreamServiceGrpc.newBlockingStub(this.channel);
                measureServiceBlockingStub = MeasureServiceGrpc.newBlockingStub(this.channel);
                traceServiceBlockingStub = TraceServiceGrpc.newBlockingStub(this.channel);
                streamServiceStub = StreamServiceGrpc.newStub(this.channel);
                measureServiceStub = MeasureServiceGrpc.newStub(this.channel);
                traceServiceStub = TraceServiceGrpc.newStub(this.channel);
                isConnected = true;
            }
        } finally {
            connectionEstablishLock.unlock();
        }
    }

    /**
     * Build a MeasureWrite request.
     *
     * @param group     the group of the measure
     * @param name      the name of the measure
     * @param timestamp the timestamp of the measure
     * @return the request to be built
     */
    public MeasureWrite createMeasureWrite(String group, String name, long timestamp) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        return new MeasureWrite(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build(), timestamp);
    }

    /**
     * Build a StreamWrite request.
     *
     * @param group     the group of the stream
     * @param name      the name of the stream
     * @param elementId the primary key of the stream
     * @return the request to be built
     */
    public StreamWrite createStreamWrite(String group, String name, final String elementId) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        return new StreamWrite(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build(), elementId);
    }

    /**
     * Build a TraceWrite request without initial timestamp.
     *
     * @param group the group of the trace
     * @param name  the name of the trace
     * @return the request to be built
     */
    public TraceWrite createTraceWrite(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        return new TraceWrite(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build());
    }

    /**
     * Query streams according to given conditions
     *
     * @param streamQuery condition for query
     * @return hint streams.
     */
    public StreamQueryResponse query(StreamQuery streamQuery) throws BanyanDBException {
        checkState(this.streamServiceStub != null, "stream service is null");

        for (String group : streamQuery.groups) {
            final BanyandbStream.QueryResponse response =
                HandleExceptionsWith.callAndTranslateApiException(() ->
                                                                      this.streamServiceBlockingStub
                                                                          .withDeadlineAfter(
                                                                              this.getOptions().getDeadline(),
                                                                              TimeUnit.SECONDS
                                                                          )
                                                                          .query(streamQuery.build()));
            return new StreamQueryResponse(response);
        }
        throw new RuntimeException("No metadata found for the query");
    }

    /**
     * Query TopN according to given conditions
     *
     * @param topNQuery condition for query
     * @return hint topN.
     */
    public TopNQueryResponse query(TopNQuery topNQuery) throws BanyanDBException {
        checkState(this.measureServiceStub != null, "measure service is null");

        final BanyandbMeasure.TopNResponse response = HandleExceptionsWith.callAndTranslateApiException(() ->
                this.measureServiceBlockingStub
                        .withDeadlineAfter(this.getOptions().getDeadline(), TimeUnit.SECONDS)
                        .topN(topNQuery.build()));
        return new TopNQueryResponse(response);
    }

    /**
     * Query measures according to given conditions
     *
     * @param measureQuery condition for query
     * @return hint measures.
     */
    public MeasureQueryResponse query(MeasureQuery measureQuery) throws BanyanDBException {
        checkState(this.streamServiceStub != null, "measure service is null");
        for (String group : measureQuery.groups) {
            final BanyandbMeasure.QueryResponse response =
                HandleExceptionsWith.callAndTranslateApiException(() ->
                                                                      this.measureServiceBlockingStub
                                                                          .withDeadlineAfter(
                                                                              this.getOptions()
                                                                                  .getDeadline(),
                                                                              TimeUnit.SECONDS
                                                                          )
                                                                          .query(
                                                                              measureQuery.build()));
            return new MeasureQueryResponse(response);
        }
            throw new RuntimeException("No metadata found for the query");
   }

    /**
     * Query traces according to given conditions
     *
     * @param traceQuery condition for query
     * @return trace query response.
     */
    public TraceQueryResponse query(TraceQuery traceQuery) throws BanyanDBException {
        checkState(this.traceServiceStub != null, "trace service is null");

        for (String group : traceQuery.groups) {
            final BanyandbTrace.QueryResponse response =
                HandleExceptionsWith.callAndTranslateApiException(() ->
                                                                      this.traceServiceBlockingStub
                                                                          .withDeadlineAfter(
                                                                              this.getOptions().getDeadline(),
                                                                              TimeUnit.SECONDS
                                                                          )
                                                                          .query(traceQuery.build()));
            return new TraceQueryResponse(response);

        }
        throw new RuntimeException("No metadata found for the query");
    }

    /**
     * Define a new group and attach to the current client.
     *
     * @param group the group to be created
     * @return a grouped client
     */
    public Group define(Group group) throws BanyanDBException {
        GroupMetadataRegistry registry = new GroupMetadataRegistry(checkNotNull(this.channel));
        registry.create(group);
        return registry.get(null, group.getMetadata().getName());
    }

    /**
     * Define a new stream
     *
     * @param stream the stream to be created
     */
    public void define(Stream stream) throws BanyanDBException {
        StreamMetadataRegistry streamRegistry = new StreamMetadataRegistry(checkNotNull(this.channel));
        long modRevision = streamRegistry.create(stream);
        stream = stream.toBuilder().setMetadata(stream.getMetadata().toBuilder().setModRevision(modRevision)).build();
    }

    /**
     * Define a new stream with index rules,
     * @param stream the stream to be created
     * @param indexRules the index rules to be created
     */
    public void define(Stream stream, List<IndexRule> indexRules) throws BanyanDBException {
        define(stream);
        defineIndexRules(stream, indexRules);
    }

    /**
     * Define a new measure
     *
     * @param measure the measure to be created
     */
    public void define(Measure measure) throws BanyanDBException {
        MeasureMetadataRegistry measureRegistry = new MeasureMetadataRegistry(checkNotNull(this.channel));
        long modRevision = measureRegistry.create(measure);
        measure = measure.toBuilder().setMetadata(measure.getMetadata().toBuilder().setModRevision(modRevision)).build();
    }

    /**
     * Define a new measure with index rules
     * @param measure the measure to be created
     * @param indexRules the index rules to be created
     */
    public void define(Measure measure, List<IndexRule> indexRules) throws BanyanDBException {
        define(measure);
        defineIndexRules(measure, indexRules);
    }

    /**
     * Define a new TopNAggregation
     *
     * @param topNAggregation the topN rule to be created
     */
    public void define(TopNAggregation topNAggregation) throws BanyanDBException {
        TopNAggregationMetadataRegistry registry = new TopNAggregationMetadataRegistry(checkNotNull(this.channel));
        registry.create(topNAggregation);
    }

    /**
     * Define a new IndexRule
     * @param indexRule the index rule to be created
     */
    public void define(IndexRule indexRule) throws BanyanDBException {
        IndexRuleMetadataRegistry registry = new IndexRuleMetadataRegistry(checkNotNull(this.channel));
        registry.create(indexRule);
    }

    /**
     * Define a new IndexRuleBinding, if the beginAt and expireAt are not set, the default value will be used.
     * The default value of beginAt is the current time, and the default value of expireAt is 2099-01-01 00:00:00 UTC.
     * @param indexRuleBinding the index rule binding to be created
     */
    public void define(IndexRuleBinding indexRuleBinding) throws BanyanDBException {
        ZonedDateTime beginAt = indexRuleBinding.getBeginAt() == Timestamp.getDefaultInstance() ? ZonedDateTime.now() : TimeUtils.parseTimestamp(indexRuleBinding.getBeginAt());
        ZonedDateTime expireAt = indexRuleBinding.getExpireAt() == Timestamp.getDefaultInstance() ? DEFAULT_EXPIRE_AT : TimeUtils.parseTimestamp(indexRuleBinding.getExpireAt());
        this.define(indexRuleBinding, beginAt, expireAt);
    }

    /**
     * Define a new IndexRuleBinding
     * @param indexRuleBinding the index rule binding to be created
     * @param beginAt the beginning time of the index rule binding
     * @param expireAt the expiry time of the index rule binding
     */
    public void define(IndexRuleBinding indexRuleBinding, ZonedDateTime beginAt, ZonedDateTime expireAt) throws BanyanDBException {
        IndexRuleBindingMetadataRegistry registry = new IndexRuleBindingMetadataRegistry(checkNotNull(this.channel));
        indexRuleBinding = indexRuleBinding.toBuilder()
                                           .setBeginAt(TimeUtils.buildTimestamp(beginAt))
                                           .setExpireAt(TimeUtils.buildTimestamp(expireAt))
                                           .build();
        registry.create(indexRuleBinding);
    }

    /**
     * Bind index rule to the stream
     * By default, the index rule binding will be active from now, and it will never be expired.
     * @param stream     the subject of index rule binding
     * @param indexRules rules to be bounded
     */
    public void defineIndexRules(Stream stream, List<IndexRule> indexRules) throws BanyanDBException {
        Preconditions.checkArgument(stream != null, "stream cannot be null");

        IndexRuleMetadataRegistry irRegistry = new IndexRuleMetadataRegistry(checkNotNull(this.channel));
        for (final IndexRule ir : indexRules) {
            try {
                irRegistry.create(ir);
            } catch (BanyanDBException ex) {
                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                    continue;
                }
                throw ex;
            }
        }
        if (indexRules.isEmpty()) {
            return;
        }

        List<String> indexRuleNames = indexRules.stream()
                                                .map(indexRule -> indexRule.getMetadata().getName())
                                                .collect(Collectors.toList());

        IndexRuleBinding binding = IndexRuleBinding.newBuilder()
                                                   .setMetadata(Metadata.newBuilder()
                                                                        .setGroup(
                                                                            stream.getMetadata().getGroup())
                                                                        .setName(
                                                                            stream.getMetadata().getName()))
                                                   .setSubject(Subject.newBuilder()
                                                                      .setName(stream.getMetadata()
                                                                                     .getName())
                                                                      .setCatalog(
                                                                          BanyandbCommon.Catalog.CATALOG_STREAM))
                                                   .addAllRules(indexRuleNames).build();
        this.define(binding);
    }

    /**
     * Bind index rule to the measure.
     * By default, the index rule binding will be active from now, and it will never be expired.
     *
     * @param measure    the subject of index rule binding
     * @param indexRules rules to be bounded
     */
    public void defineIndexRules(Measure measure, List<IndexRule> indexRules) throws BanyanDBException {
        Preconditions.checkArgument(measure != null, "measure cannot be null");

        IndexRuleMetadataRegistry irRegistry = new IndexRuleMetadataRegistry(checkNotNull(this.channel));
        for (final IndexRule ir : indexRules) {
            try {
                irRegistry.create(ir);
            } catch (BanyanDBException ex) {
                // multiple entity can share a single index rule
                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                    continue;
                }
                throw ex;
            }
        }
        if (indexRules.isEmpty()) {
            return;
        }

        List<String> indexRuleNames = indexRules.stream().map(indexRule -> indexRule.getMetadata().getName()).collect(Collectors.toList());

        IndexRuleBinding binding = IndexRuleBinding.newBuilder()
                                                   .setMetadata(Metadata.newBuilder()
                                                                        .setGroup(
                                                                            measure.getMetadata().getGroup())
                                                                        .setName(
                                                                            measure.getMetadata().getName()))
                                                   .setSubject(Subject.newBuilder()
                                                                      .setName(measure.getMetadata()
                                                                                      .getName())
                                                                      .setCatalog(
                                                                          BanyandbCommon.Catalog.CATALOG_MEASURE))
                                                   .addAllRules(indexRuleNames).build();
        this.define(binding);
    }

    /**
     * Update the group
     *
     * @param group the group to be updated
     */
    public void update(Group group) throws BanyanDBException {
        GroupMetadataRegistry registry = new GroupMetadataRegistry(checkNotNull(this.channel));
        registry.update(group);
    }

    /**
     * Update the stream
     * @param stream the stream to be updated
     */
    public void update(Stream stream) throws BanyanDBException {
        StreamMetadataRegistry streamRegistry = new StreamMetadataRegistry(checkNotNull(this.channel));
        streamRegistry.update(stream);
    }

    /**
     * Update the measure
     *
     * @param measure the measure to be updated
     */
    public void update(Measure measure) throws BanyanDBException {
        MeasureMetadataRegistry measureRegistry = new MeasureMetadataRegistry(checkNotNull(this.channel));
        measureRegistry.update(measure);
    }

    /**
     * Update the TopNAggregation
     * @param topNAggregation the topN rule to be updated
     */
    public void update(TopNAggregation topNAggregation) throws BanyanDBException {
        TopNAggregationMetadataRegistry registry = new TopNAggregationMetadataRegistry(checkNotNull(this.channel));
        registry.update(topNAggregation);
    }

    /**
     * Update the IndexRule
     * @param indexRule the index rule to be updated
     */
    public void update(IndexRule indexRule) throws BanyanDBException {
        IndexRuleMetadataRegistry registry = new IndexRuleMetadataRegistry(checkNotNull(this.channel));
        registry.update(indexRule);
    }

    /**
     * Update the IndexRuleBinding
     * @param indexRuleBinding the index rule binding to be updated
     */
    public void update(IndexRuleBinding indexRuleBinding) throws BanyanDBException {
        IndexRuleBindingMetadataRegistry registry = new IndexRuleBindingMetadataRegistry(checkNotNull(this.channel));
        registry.update(indexRuleBinding);
    }

    /**
     * Delete the group
     * @param name name of the group
     * @return true if the group is deleted successfully
     */
    public boolean deleteGroup(String name) throws BanyanDBException {
        GroupMetadataRegistry registry = new GroupMetadataRegistry(checkNotNull(this.channel));
        return registry.delete(name, name);
    }

    /**
     * Delete a stream
     * @param group the group name of the stream
     * @param name  the name of the stream
     * @return true if the stream is deleted successfully
     */
    public boolean deleteStream(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        StreamMetadataRegistry streamRegistry = new StreamMetadataRegistry(checkNotNull(this.channel));
        return streamRegistry.delete(group, name);
    }

    /**
     * Delete a measure
     * @param group the group name of the measure
     * @param name  the name of the measure
     * @return true if the measure is deleted successfully
     */
    public boolean deleteMeasure(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        MeasureMetadataRegistry measureRegistry = new MeasureMetadataRegistry(checkNotNull(this.channel));
        return measureRegistry.delete(group, name);
    }

    /**
     * Delete the TopNAggregation
     * @param group the group name of the topN rule
     * @param name the name of the topN rule
     * @return true if the topN rule is deleted successfully
     */
    public boolean deleteTopNAggregation(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        TopNAggregationMetadataRegistry registry = new TopNAggregationMetadataRegistry(checkNotNull(this.channel));
        return registry.delete(group, name);
    }

    /**
     * Delete the IndexRule
     * @param group the group name of the index rule
     * @param name the name of the index rule
     * @return true if the index rule is deleted successfully
     */
    public boolean deleteIndexRule(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        IndexRuleMetadataRegistry registry = new IndexRuleMetadataRegistry(checkNotNull(this.channel));
        return registry.delete(group, name);
    }

    /**
     * Delete the IndexRuleBinding
     * @param group the group name of the index rule binding
     * @param name the name of the index rule binding
     * @return true if the index rule binding is deleted successfully
     */
    public boolean deleteIndexRuleBinding(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        IndexRuleBindingMetadataRegistry registry = new IndexRuleBindingMetadataRegistry(checkNotNull(this.channel));
        return registry.delete(group, name);
    }

    /**
     * Find the IndexRule
     * @param group the group name of the index rule
     * @param name the name of the index rule
     * @return the index rule if it can be found, otherwise null
     */
    public IndexRule findIndexRule(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        IndexRuleMetadataRegistry registry = new IndexRuleMetadataRegistry(checkNotNull(this.channel));
        try {
            return registry.get(group, name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * Find all IndexRules in the group
     * @param group the group name of the index rule
     * @return all index rules in the group
     */
    public List<IndexRule> findIndexRules(String group) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        IndexRuleMetadataRegistry registry = new IndexRuleMetadataRegistry(checkNotNull(this.channel));
        return registry.list(group);
    }

    /**
     * Find the IndexRuleBinding
     * @param group the group name of the index rule binding
     * @param name the name of the index rule binding
     * @return the index rule binding if it can be found, otherwise null
     */
    public IndexRuleBinding findIndexRuleBinding(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        IndexRuleBindingMetadataRegistry registry = new IndexRuleBindingMetadataRegistry(checkNotNull(this.channel));
        try {
            return registry.get(group, name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * Find all IndexRuleBindings in the group
     * @param group the group name of the index rule binding
     * @return all index rule bindings in the group
     */
    public List<IndexRuleBinding> findIndexRuleBindings(String group) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        IndexRuleBindingMetadataRegistry registry = new IndexRuleBindingMetadataRegistry(checkNotNull(this.channel));
        return registry.list(group);
    }

    /**
     * Define a new property.
     *
     * @param property the property to be stored in the BanyanBD
     * @throws BanyanDBException if the property is invalid
     */
    public void define(org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property property) throws BanyanDBException {
        PropertyMetadataRegistry registry = new PropertyMetadataRegistry(checkNotNull(this.channel));
        registry.create(property);
    }

    /**
     * Update the property.
     *
     * @param property the property to be stored in the BanyanBD
     * @throws BanyanDBException if the property is invalid
     */
    public void update(org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property property) throws BanyanDBException {
        PropertyMetadataRegistry registry = new PropertyMetadataRegistry(checkNotNull(this.channel));
        registry.update(property);
    }

    /**
     * Find the property with given group and name
     *
     * @param group group of the metadata
     * @param name  name of the metadata
     * @return the property found in BanyanDB. Otherwise, null is returned.
     */
    public org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property findPropertyDefinition(String group, String name) throws BanyanDBException {
        PropertyMetadataRegistry registry = new PropertyMetadataRegistry(checkNotNull(this.channel));
        return registry.get(group, name);
    }

    /**
     * Find the properties with given group
     *
     * @param group group of the metadata
     * @return the properties found in BanyanDB
     */
    public List<org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property> findPropertiesDefinition(String group) throws BanyanDBException {
        PropertyMetadataRegistry registry = new PropertyMetadataRegistry(checkNotNull(this.channel));
        return registry.list(group);
    }

    /**
     * Delete the property
     *
     * @param group group of the metadata
     * @param name  name of the metadata
     * @return if this property has been deleted
     */
    public boolean deletePropertyDefinition(String group, String name) throws BanyanDBException {
        PropertyMetadataRegistry registry = new PropertyMetadataRegistry(checkNotNull(this.channel));
        return registry.delete(group, name);
    }

    /**
     * Query properties
     *
     * @param request query request
     * @return query response
     */
    public BanyandbProperty.QueryResponse query(BanyandbProperty.QueryRequest request) throws BanyanDBException {
        PropertyStore store = new PropertyStore(checkNotNull(this.channel));
        return store.query(request);
    }

    /**
     * Define a new trace
     *
     * @param trace the trace to be stored in the BanyanDB
     * @throws BanyanDBException if the trace is invalid
     */
    public void define(Trace trace) throws BanyanDBException {
        TraceMetadataRegistry registry = new TraceMetadataRegistry(checkNotNull(this.channel));
        registry.create(trace);
    }

    /**
     * Update the trace.
     *
     * @param trace the trace to be stored in the BanyanDB
     * @throws BanyanDBException if the trace is invalid
     */
    public void update(Trace trace) throws BanyanDBException {
        TraceMetadataRegistry registry = new TraceMetadataRegistry(checkNotNull(this.channel));
        registry.update(trace);
    }

    /**
     * Find the trace with given group and name
     *
     * @param group group of the metadata
     * @param name  name of the metadata
     * @return the trace found in BanyanDB. Otherwise, null is returned.
     */
    public Trace findTrace(String group, String name) throws BanyanDBException {
        try {
            return new TraceMetadataRegistry(checkNotNull(this.channel)).get(group, name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * Find the traces with given group
     *
     * @param group group of the metadata
     * @return the traces found in BanyanDB
     */
    public List<Trace> findTraces(String group) throws BanyanDBException {
        TraceMetadataRegistry registry = new TraceMetadataRegistry(checkNotNull(this.channel));
        return registry.list(group);
    }

    /**
     * Delete the trace
     *
     * @param group group of the metadata
     * @param name  name of the metadata
     * @return if this trace has been deleted
     */
    public boolean deleteTrace(String group, String name) throws BanyanDBException {
        TraceMetadataRegistry registry = new TraceMetadataRegistry(checkNotNull(this.channel));
        return registry.delete(group, name);
    }

    /**
     * Try to find the group defined
     *
     * @param name name of the group
     * @return the group found in BanyanDB. Otherwise, null is returned.
     */
    public Group findGroup(String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));

        try {
            return new GroupMetadataRegistry(checkNotNull(this.channel)).get(name, name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * Try to find the groups defined
     *
     * @return the groups found in BanyanDB
     */
    public List<Group> findGroups() throws BanyanDBException {
        return new GroupMetadataRegistry(checkNotNull(this.channel)).list("");
    }

    /**
     * Try to find the TopNAggregation from the BanyanDB with given group and name.
     *
     * @param group group of the TopNAggregation
     * @param name  name of the TopNAggregation
     * @return TopNAggregation if found. Otherwise, null is returned.
     */
    public TopNAggregation findTopNAggregation(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        try {
            return new TopNAggregationMetadataRegistry(checkNotNull(this.channel)).get(group, name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * Try to find the TopNAggregations from the BanyanDB with given group.
     *
     * @param group group of the TopNAggregations
     * @return TopNAggregations if found.
     */
    public List<TopNAggregation> findTopNAggregations(String group) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        return new TopNAggregationMetadataRegistry(checkNotNull(this.channel)).list(group);
    }

    /**
     * Try to find the stream from the BanyanDB with given group and name.
     *
     * @param group group of the stream
     * @param name  name of the stream
     * @return Steam if found. Otherwise, null is returned.
     */
    public Stream findStream(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        try {
            return new StreamMetadataRegistry(checkNotNull(this.channel)).get(group, name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * Try to find the streams from the BanyanDB with given group.
     *
     * @param group group of the streams
     * @return Streams if found.
     */
    public List<Stream> findStreams(String group) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        StreamMetadataRegistry registry = new StreamMetadataRegistry(checkNotNull(this.channel));
        return registry.list(group);
    }

    /**
     * Try to find the measure from the BanyanDB with given group and name.
     *
     * @param group group of the measure
     * @param name  name of the measure
     * @return Measure with index rules if found. Otherwise, null is returned.
     */
    public Measure findMeasure(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
        try {
            return new MeasureMetadataRegistry(checkNotNull(this.channel)).get(group, name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * Try to find the measures from the BanyanDB with given group.
     *
     * @param group group of the measures
     * @return Measures if found.
     */
    public List<Measure> findMeasures(String group) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        MeasureMetadataRegistry registry = new MeasureMetadataRegistry(checkNotNull(this.channel));
        return registry.list(group);
    }

    private List<IndexRule> findIndexRulesByGroupAndBindingName(String group, String bindingName) throws
            BanyanDBException {
        IndexRuleBindingMetadataRegistry irbRegistry = new IndexRuleBindingMetadataRegistry(checkNotNull(this.channel));

        IndexRuleBinding irb;
        try {
            irb = irbRegistry.get(group, bindingName);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                return Collections.emptyList();
            }
            throw ex;
        }

        if (irb == null) {
            return Collections.emptyList();
        }

        List<IndexRule> indexRules = new ArrayList<>(irb.getRulesList().size());
        return indexRules;
    }

    /**
     * Check if the given stream exists.
     *
     * @param group group of the stream
     * @param name  name of the stream
     * @return ResourceExist which indicates whether group and stream exist
     */
    public ResourceExist existStream(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));

        return new StreamMetadataRegistry(checkNotNull(this.channel)).exist(group, name);
    }

    /**
     * Check if the given measure exists.
     *
     * @param group group of the measure
     * @param name  name of the measure
     * @return ResourceExist which indicates whether group and measure exist
     */
    public ResourceExist existMeasure(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));

        return new MeasureMetadataRegistry(checkNotNull(this.channel)).exist(group, name);
    }

    /**
     * Check if the given TopNAggregation exists.
     *
     * @param group group of the TopNAggregation
     * @param name  name of the TopNAggregation
     * @return ResourceExist which indicates whether group and TopNAggregation exist
     */
    public ResourceExist existTopNAggregation(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));

        return new TopNAggregationMetadataRegistry(checkNotNull(this.channel)).exist(group, name);
    }

    /**
     * Check if the given property exists.
     *
     * @param group group of the property
     * @param name name of the property
     * @return ResourceExist which indicates whether group and property exist
     */
    public ResourceExist existProperty(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));

        return new PropertyMetadataRegistry(checkNotNull(this.channel)).exist(group, name);
    }

    /**
     * Check whether the trace definition is existed in the server
     *
     * @param group group of the metadata
     * @param name  name of the metadata
     * @return ResourceExist which indicates whether group and trace exist
     */
    public ResourceExist existTrace(String group, String name) throws BanyanDBException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(group));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name));

        return new TraceMetadataRegistry(checkNotNull(this.channel)).exist(group, name);
    }

    /**
     * Get the API version of the server
     *
     * @return the API version of the server
     * @throws BanyanDBException if the server is not reachable
     */
    public BanyandbCommon.APIVersion getAPIVersion() throws BanyanDBException {
        ServiceGrpc.ServiceBlockingStub stub = ServiceGrpc.newBlockingStub(this.channel);
        return HandleExceptionsWith.callAndTranslateApiException(() -> {
            BanyandbCommon.GetAPIVersionResponse resp = stub.getAPIVersion(BanyandbCommon.GetAPIVersionRequest.getDefaultInstance());
            return resp.getVersion();
        });
    }

    @Override
    public void close() throws IOException {
        connectionEstablishLock.lock();
        if (!(this.channel instanceof ManagedChannel)) {
            return;
        }
        final ManagedChannel managedChannel = (ManagedChannel) this.channel;
        try {
            if (isConnected) {
                managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                isConnected = false;
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("fail to wait for channel termination, shutdown now!", interruptedException);
            managedChannel.shutdownNow();
            isConnected = false;
        } finally {
            connectionEstablishLock.unlock();
        }
    }
}

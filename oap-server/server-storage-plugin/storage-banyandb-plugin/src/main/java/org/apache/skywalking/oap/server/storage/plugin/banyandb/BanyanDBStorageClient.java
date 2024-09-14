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

import io.grpc.Status;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.MeasureBulkWriteProcessor;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.banyandb.v1.client.StreamBulkWriteProcessor;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.TopNQuery;
import org.apache.skywalking.banyandb.v1.client.TopNQueryResponse;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.Property;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.ApplyRequest.Strategy;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.DeleteResponse;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.AlreadyExistsException;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * BanyanDBStorageClient is a simple wrapper for the underlying {@link BanyanDBClient},
 * which implement {@link Client} and {@link HealthCheckable}.
 */
public class BanyanDBStorageClient implements Client, HealthCheckable {
    final BanyanDBClient client;
    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();
    private final int flushTimeout;

    public BanyanDBStorageClient(int flushTimeout, String... targets) {
        this.client = new BanyanDBClient(targets);
        this.flushTimeout = flushTimeout;
    }

    @Override
    public void connect() throws Exception {
        this.client.connect();
    }

    @Override
    public void shutdown() throws IOException {
        this.client.close();
    }

    public List<Property> listProperties(String group, String name) throws IOException {
        try {
            List<Property> properties = this.client.findProperties(group, name);
            this.healthChecker.health();
            return properties;
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                this.healthChecker.health();
                return Collections.emptyList();
            }

            healthChecker.unHealth(ex);
            throw new IOException("fail to list properties", ex);
        }
    }

    public Property queryProperty(String group, String name, String id) throws IOException {
        try {
            Property p = this.client.findProperty(group, name, id);
            this.healthChecker.health();
            return p;
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                this.healthChecker.health();
                return null;
            }

            healthChecker.unHealth(ex);
            throw new IOException("fail to query property", ex);
        }
    }

    public DeleteResponse deleteProperty(String group, String name, String id, String... tags) throws IOException {
        try {
            DeleteResponse result = this.client.deleteProperty(group, name, id, tags);
            this.healthChecker.health();
            return result;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to delete property", ex);
        }
    }

    public void keepAliveProperty(long leaseId) throws IOException {
        try {
            this.client.keepAliveProperty(leaseId);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to keep alive property", ex);
        }
    }

    public StreamQueryResponse query(StreamQuery q) throws IOException {
        try {
            StreamQueryResponse response = this.client.query(q);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query stream", ex);
        }
    }

    public MeasureQueryResponse query(MeasureQuery q) throws IOException {
        try {
            MeasureQueryResponse response = this.client.query(q);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query measure", ex);
        }
    }

    public TopNQueryResponse query(TopNQuery q) throws IOException {
        try {
            TopNQueryResponse response = this.client.query(q);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query topn", ex);
        }
    }

    /**
     * PropertyStore.Strategy is default to {@link Strategy#STRATEGY_MERGE}
     */
    public void define(Property property) throws IOException {
        try {
            this.client.apply(property);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to define property", ex);
        }
    }

    public void define(Property property, Strategy strategy) throws IOException {
        try {
            this.client.apply(property, strategy);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to define property", ex);
        }
    }

    public void define(Stream stream) throws BanyanDBException {
        try {
            this.client.define(stream);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw ex;
        }
    }

    public void define(Stream stream, List<BanyandbDatabase.IndexRule> indexRules) throws BanyanDBException {
        try {
            this.client.define(stream, indexRules);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw ex;
        }
    }

    public void define(Measure measure) throws BanyanDBException {
        try {
            this.client.define(measure);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw ex;
        }
    }

    public void define(Measure measure, List<BanyandbDatabase.IndexRule> indexRules) throws BanyanDBException {
        try {
            this.client.define(measure, indexRules);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw ex;
        }
    }

    public void defineIfEmpty(Group group) throws IOException {
        try {
            try {
                this.client.define(group);
            } catch (AlreadyExistsException ignored) {
            }
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to define group", ex);
        }
    }

    public void define(TopNAggregation topNAggregation) throws IOException {
        try {
            this.client.define(topNAggregation);
            this.healthChecker.health();
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to define TopNAggregation", ex);
        }
    }

    public StreamWrite createStreamWrite(String group, String name, String elementId) throws IOException {
        try {
            return this.client.createStreamWrite(group, name, elementId);
        } catch (BanyanDBException e) {
            throw new IOException("fail to create stream write", e);
        }
    }

    public MeasureWrite createMeasureWrite(String group, String name, long timestamp) throws IOException {
        try {
            return this.client.createMeasureWrite(group, name, timestamp);
        } catch (BanyanDBException e) {
            throw new IOException("fail to create measure write", e);
        }
    }

    public void write(StreamWrite streamWrite) {
        this.client.write(streamWrite);
    }

    public StreamBulkWriteProcessor createStreamBulkProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        return this.client.buildStreamWriteProcessor(maxBulkSize, flushInterval, concurrency, flushTimeout);
    }

    public MeasureBulkWriteProcessor createMeasureBulkProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        return this.client.buildMeasureWriteProcessor(maxBulkSize, flushInterval, concurrency, flushTimeout);
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }
}

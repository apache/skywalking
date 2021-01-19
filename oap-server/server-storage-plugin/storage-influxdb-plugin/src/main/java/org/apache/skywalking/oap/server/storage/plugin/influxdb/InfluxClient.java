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

package org.apache.skywalking.oap.server.storage.plugin.influxdb;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.time.TimeInterval;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.ti;

/**
 * InfluxDB connection maintainer, provides base data write/query API.
 */
@Slf4j
public class InfluxClient implements Client, HealthCheckable {
    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();
    private final InfluxStorageConfig config;
    private InfluxDB influx;
    /**
     * A constant, the name of time field in Time-series database.
     */
    public static final String TIME = "time";

    private final String database;

    public InfluxClient(InfluxStorageConfig config) {
        this.config = config;
        this.database = config.getDatabase();
    }

    public final String getDatabase() {
        return database;
    }

    @Override
    public void connect() {
        try {
            InfluxDB.ResponseFormat responseFormat = InfluxDB.ResponseFormat.valueOf(config.getConnectionResponseFormat());
            influx = InfluxDBFactory.connect(config.getUrl(), config.getUser(), config.getPassword(),
                    new OkHttpClient.Builder().readTimeout(3, TimeUnit.MINUTES)
                            .writeTimeout(3, TimeUnit.MINUTES),
                    responseFormat
            );
            influx.query(new Query("CREATE DATABASE " + database));
            influx.enableGzip();

            if (config.isBatchEnabled()) {
                influx.enableBatch(config.getActions(), config.getDuration(), TimeUnit.MILLISECONDS);
            }

            influx.setDatabase(database);
            healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw e;
        }
    }

    /**
     * To get a connection of InfluxDB.
     *
     * @return InfluxDB's connection
     */
    private InfluxDB getInflux() {
        return influx;
    }

    /**
     * Execute a query against InfluxDB and return a set of {@link QueryResult.Result}s. Normally, InfluxDB supports
     * combining multiple statements into one query, so that we do get multi-results.
     *
     * @throws IOException if there is an error on the InfluxDB server or communication error.
     */
    public List<QueryResult.Result> query(Query query) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("SQL Statement: {}", query.getCommand());
        }
        try {
            QueryResult result = getInflux().query(new Query(query.getCommand()));
            if (result.hasError()) {
                throw new IOException(result.getError());
            }
            healthChecker.health();
            return result.getResults();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + query.getCommand(), e);
        }
    }

    /**
     * Execute a query against InfluxDB with a single statement.
     *
     * @throws IOException if there is an error on the InfluxDB server or communication error
     */
    public List<QueryResult.Series> queryForSeries(Query query) throws IOException {
        List<QueryResult.Result> results = query(query);

        if (CollectionUtils.isEmpty(results)) {
            return null;
        }
        return results.get(0).getSeries();
    }

    /**
     * Execute a query against InfluxDB with a single statement but return a single {@link QueryResult.Series}.
     *
     * @throws IOException if there is an error on the InfluxDB server or communication error
     */
    public QueryResult.Series queryForSingleSeries(Query query) throws IOException {
        List<QueryResult.Series> series = queryForSeries(query);
        if (CollectionUtils.isEmpty(series)) {
            return null;
        }
        return series.get(0);
    }

    /**
     * Execute a query against InfluxDB with a `select count(*)` statement and return the count only.
     *
     * @throws IOException if there is an error on the InfluxDB server or communication error
     */
    public int getCounter(Query query) throws IOException {
        QueryResult.Series series = queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }
        if (Objects.isNull(series)) {
            return 0;
        }
        return ((Number) series.getValues().get(0).get(1)).intValue();
    }

    /**
     * Data management, to drop a time-series by measurement and time-series name specified. If an exception isn't
     * thrown, it means execution success. Notice, drop series don't support to drop series by range
     *
     * @throws IOException if there is an error on the InfluxDB server or communication error
     */
    public void dropSeries(String measurement, long timeBucket) throws IOException {
        Query query = new Query("DROP SERIES FROM " + measurement + " WHERE time_bucket='" + timeBucket + "'");
        this.query(query);
    }

    public void deleteByQuery(String measurement, long timestamp) throws IOException {
        this.query(new Query("delete from " + measurement + " where time < " + timestamp + "ms"));
    }

    /**
     * Write a {@link Point} into InfluxDB. Note that, the {@link Point} is written into buffer of InfluxDB Client and
     * wait for buffer flushing.
     */
    public void write(Point point) {
        try {
            getInflux().write(point);
            this.healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw e;
        }
    }

    /**
     * A batch operation of write. {@link Point}s flush directly.
     */
    public void write(BatchPoints points) {
        try {
            getInflux().write(points);
            this.healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw e;
        }
    }

    @Override
    public void shutdown() throws IOException {
        try {
            getInflux().close();
            this.healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new IOException(e);
        }
    }

    /**
     * Convert to InfluxDB {@link TimeInterval}.
     */
    public static TimeInterval timeIntervalTS(long timestamp) {
        return ti(timestamp, "ms");
    }

    /**
     * Convert to InfluxDB {@link TimeInterval}.
     */
    public static TimeInterval timeIntervalTB(long timeBucket) {
        return ti(TimeBucket.getTimestamp(timeBucket), "ms");
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }
}

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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.library.client.Client;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.time.TimeInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.ti;

/**
 * InfluxDB connection maintainer, provides base data write/query API.
 */
public class InfluxClient implements Client {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private InfluxStorageConfig config;
    private InfluxDB influx;

    /**
     * A constant, the name of time field in Time-series database.
     */
    public static final String TIME = "time";
    /**
     * A constant, the name of tag.
     */
    public static final String TAG_ENTITY_ID = "entity_id";

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
        influx = InfluxDBFactory.connect(config.getUrl(), config.getUser(), config.getPassword(),
            new OkHttpClient.Builder(), InfluxDB.ResponseFormat.MSGPACK);
        influx.query(new Query("CREATE DATABASE " + database));

        influx.enableBatch(config.getActions(), config.getDuration(), TimeUnit.MILLISECONDS);
        influx.setDatabase(database);
    }

    /**
     * To get a connection of InfluxDB.
     *
     * @return InfluxDB's connection
     */
    public InfluxDB getInflux() {
        return influx;
    }

    /**
     * Request with a {@link Query} to InfluxDB and return a set of {@link QueryResult.Result}s.
     *
     * @param query
     * @return a set of Result.
     * @throws IOException
     */
    public List<QueryResult.Result> query(Query query) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("SQL Statement: {}", query.getCommand());
        }

        try {
            QueryResult result = getInflux().query(query);
            if (result.hasError()) {
                throw new IOException(result.getError());
            }
            return result.getResults();
        } catch (Exception e) {
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + query.getCommand(), e);
        }
    }

    /**
     * Request with one statement to InfluxDB and return a set of {@link QueryResult.Series}s.
     *
     * @param query
     * @return a set of Series
     * @throws IOException
     */
    public List<QueryResult.Series> queryForSeries(Query query) throws IOException {
        return query(query).get(0).getSeries();
    }

    /**
     * Data management, to drop a time-series by measurement and time-series name specified. If an exception isn't
     * thrown, it means execution success.
     *
     * @param measurement
     * @param timeBucket
     * @throws IOException
     */
    public void dropSeries(String measurement, long timeBucket) throws IOException {
        Query query = new Query("DROP SERIES FROM " + measurement + " WHERE time_bucket<='" + timeBucket + "'");
        QueryResult result = getInflux().query(query);

        if (result.hasError()) {
            throw new IOException("Statement: " + query.getCommand() + ", ErrorMsg: " + result.getError());
        }
    }

    /**
     * Write a {@link Point} into InfluxDB.
     *
     * @param point
     */
    public void write(Point point) {
        getInflux().write(point);
    }

    /**
     * A batch operation of write.
     *
     * @param points
     */
    public void write(BatchPoints points) {
        getInflux().write(points);
    }

    @Override
    public void shutdown() throws IOException {
        influx.close();
    }

    /**
     * Convert to InfluxDB {@link TimeInterval}.
     *
     * @param timeBucket
     * @param downsampling
     * @return
     */
    public static TimeInterval timeInterval(long timeBucket, Downsampling downsampling) {
        return ti(TimeBucket.getTimestamp(timeBucket, downsampling), "ms");
    }

    /**
     * Convert to InfluxDB {@link TimeInterval}.
     *
     * @param timeBucket
     * @return
     */
    public static TimeInterval timeInterval(long timeBucket) {
        return ti(TimeBucket.getTimestamp(timeBucket), "ms");
    }
}

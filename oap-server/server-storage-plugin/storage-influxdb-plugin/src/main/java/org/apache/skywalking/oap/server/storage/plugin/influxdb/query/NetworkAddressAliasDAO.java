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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.TableMetaInfo;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class NetworkAddressAliasDAO implements INetworkAddressAliasDAO {
    private final NetworkAddressAlias.Builder builder = new NetworkAddressAlias.Builder();
    private final InfluxClient client;

    public NetworkAddressAliasDAO(final InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(final long timeBucket) {
        List<NetworkAddressAlias> networkAddressAliases = new ArrayList<>();

        WhereQueryImpl<SelectQueryImpl> query = select().raw(InfluxConstants.ALL_FIELDS)
                                                        .from(client.getDatabase(), NetworkAddressAlias.INDEX_NAME)
                                                        .where(gte(
                                                            NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET,
                                                            timeBucket
                                                        ));
        try {
            QueryResult.Series series = client.queryForSingleSeries(query);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query.getCommand(), series);
            }
            if (Objects.isNull(series)) {
                return networkAddressAliases;
            }

            List<List<Object>> result = series.getValues();
            List<String> columns = series.getColumns();

            Map<String, String> columnAndFieldMap = TableMetaInfo.get(NetworkAddressAlias.INDEX_NAME)
                                                                 .getStorageAndColumnMap();
            for (List<Object> values : result) {
                Map<String, Object> map = Maps.newHashMap();
                for (int i = 1; i < columns.size(); i++) {
                    map.put(columnAndFieldMap.get(columns.get(i)), values.get(i));
                }
                networkAddressAliases.add(builder.map2Data(map));
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return networkAddressAliases;
    }
}

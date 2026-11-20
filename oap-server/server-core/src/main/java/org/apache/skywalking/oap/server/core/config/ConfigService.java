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

package org.apache.skywalking.oap.server.core.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.Service;

@Getter
public class ConfigService implements Service {
    private final String gRPCHost;
    private final int gRPCPort;
    private final SearchableTracesTagsWatcher searchableTracesTags;
    private final String searchableLogsTags;
    private final String searchableAlarmTags;
    private final int metricsDataTTL;
    private final int recordDataTTL;
    private final int persistentPeriod;
    private final Set<String> searchableLogsTagKeys = ConcurrentHashMap.newKeySet();

    public ConfigService(CoreModuleConfig moduleConfig, ModuleProvider provider) {
        this.gRPCHost = moduleConfig.getGRPCHost();
        this.gRPCPort = moduleConfig.getGRPCPort();

        this.searchableTracesTags =
                new SearchableTracesTagsWatcher(moduleConfig.getSearchableTracesTags(), provider);
        moduleConfig.setSearchableTracesTagsWatcher(this.searchableTracesTags);

        this.searchableLogsTags = moduleConfig.getSearchableLogsTags();
        this.searchableLogsTagKeys.addAll(splitTagKeys(this.searchableLogsTags));
        this.searchableAlarmTags = moduleConfig.getSearchableAlarmTags();
        this.metricsDataTTL = moduleConfig.getMetricsDataTTL();
        this.recordDataTTL = moduleConfig.getRecordDataTTL();
        this.persistentPeriod = moduleConfig.getPersistentPeriod();
    }

    /**
     * The log tag keys searchable on any log: {@link #searchableLogsTags}, which the logs of the SkyWalking native
     * protocols index, plus the keys a receiver with its own list registered through
     * {@link #addSearchableLogsTagKeys}. All logs share one table, so a log query accepts a key of either.
     *
     * @return a live, read-only view
     */
    public Set<String> getSearchableLogsTagKeys() {
        return Collections.unmodifiableSet(searchableLogsTagKeys);
    }

    /**
     * Split a comma-separated tag key list, trimming each key and dropping empty ones, so {@code "level, http.status_code"}
     * and {@code "level,http.status_code"} name the same keys.
     *
     * @param tagKeys a comma-separated list, or null
     * @return the keys in order
     */
    public static List<String> splitTagKeys(final String tagKeys) {
        if (tagKeys == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(tagKeys.split(Const.COMMA))
                     .map(String::trim)
                     .filter(key -> !key.isEmpty())
                     .collect(Collectors.toList());
    }

    /**
     * Register the tag keys a receiver indexes on its own logs in place of {@link #searchableLogsTags}, so the log
     * query accepts them.
     *
     * @param keys the receiver's searchable log tag keys
     */
    public void addSearchableLogsTagKeys(final Collection<String> keys) {
        searchableLogsTagKeys.addAll(keys);
    }
}

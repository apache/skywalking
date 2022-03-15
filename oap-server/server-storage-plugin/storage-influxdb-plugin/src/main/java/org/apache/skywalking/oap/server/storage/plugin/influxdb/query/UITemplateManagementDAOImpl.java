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
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@RequiredArgsConstructor
@Slf4j
public class UITemplateManagementDAOImpl implements UITemplateManagementDAO {
    private final InfluxClient client;

    @Override
    public DashboardConfiguration getTemplate(final String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        final SelectQueryImpl query = select().all()
                                              .from(client.getDatabase(), UITemplate.INDEX_NAME)
                                              .where(eq(InfluxConstants.TagName.ID_COLUMN, id))
                                              .limit(1);

        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }
        final UITemplate.Builder builder = new UITemplate.Builder();

        if (Objects.nonNull(series)) {
            List<String> columnNames = series.getColumns();
            List<Object> columnValues = series.getValues().get(0);

            Map<String, Object> data = Maps.newHashMap();
            for (int i = 1; i < columnNames.size(); i++) {
                data.put(columnNames.get(i), columnValues.get(i));
            }
            UITemplate uiTemplate = builder.storage2Entity(new HashMapConverter.ToEntity(data));
            return new DashboardConfiguration().fromEntity(uiTemplate);

        }
        return null;
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> where = select().raw("*::field")
                                                              .from(client.getDatabase(), UITemplate.INDEX_NAME)
                                                              .where();
        if (!includingDisabled) {
            where.and(eq(UITemplate.DISABLED, BooleanUtils.booleanToValue(includingDisabled)));
        }
        final QueryResult.Series series = client.queryForSingleSeries(where);
        final List<DashboardConfiguration> configs = new ArrayList<>();
        final UITemplate.Builder builder = new UITemplate.Builder();
        if (Objects.nonNull(series)) {
            List<String> columnNames = series.getColumns();

            final int size = series.getValues().size();
            for (int offset = 0; offset < size; offset++) {
                List<Object> columnValues = series.getValues().get(offset);

                Map<String, Object> data = Maps.newHashMap();
                for (int i = 1; i < columnNames.size(); i++) {
                    data.put(columnNames.get(i), columnValues.get(i));
                }
                UITemplate uiTemplate = builder.storage2Entity(new HashMapConverter.ToEntity(data));
                configs.add(new DashboardConfiguration().fromEntity(uiTemplate));
            }
        }
        return configs;
    }

    @Override
    public TemplateChangeStatus addTemplate(final DashboardSetting setting) {
        final UITemplate.Builder builder = new UITemplate.Builder();
        final UITemplate uiTemplate = setting.toEntity();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        builder.entity2Storage(uiTemplate, toStorage);
        final Point point = Point.measurement(UITemplate.INDEX_NAME)
                                 .tag(InfluxConstants.TagName.ID_COLUMN, uiTemplate.id())
                                 .fields(toStorage.obtain())
                                 .time(1L, TimeUnit.NANOSECONDS)
                                 .build();
        client.write(point);
        return TemplateChangeStatus.builder().status(true).id(setting.getId()).build();
    }

    @Override
    public TemplateChangeStatus changeTemplate(final DashboardSetting setting) throws IOException {
        final UITemplate.Builder builder = new UITemplate.Builder();
        final UITemplate uiTemplate = setting.toEntity();

        WhereQueryImpl<SelectQueryImpl> query = select().all()
                                                        .from(client.getDatabase(), UITemplate.INDEX_NAME)
                                                        .where(eq(InfluxConstants.TagName.ID_COLUMN, uiTemplate.id()));

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (Objects.nonNull(series)) {
            final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
            builder.entity2Storage(uiTemplate, toStorage);
            final Point point = Point.measurement(UITemplate.INDEX_NAME)
                                     .fields(toStorage.obtain())
                                     .tag(InfluxConstants.TagName.ID_COLUMN, uiTemplate.id())
                                     .time(1L, TimeUnit.NANOSECONDS)
                                     .build();
            client.write(point);
            return TemplateChangeStatus.builder().status(true).id(setting.getId()).build();
        } else {
            return TemplateChangeStatus.builder()
                                       .status(false)
                                       .id(setting.getId())
                                       .message("Can't find the template")
                                       .build();
        }
    }

    @Override
    public TemplateChangeStatus disableTemplate(final String id) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select().all()
                                                        .from(client.getDatabase(), UITemplate.INDEX_NAME)
                                                        .where(eq(InfluxConstants.TagName.ID_COLUMN, id));
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (Objects.nonNull(series)) {
            final Point point = Point.measurement(UITemplate.INDEX_NAME)
                                     .tag(InfluxConstants.TagName.ID_COLUMN, id)
                                     .addField(UITemplate.DISABLED, BooleanUtils.TRUE)
                                     .time(1L, TimeUnit.NANOSECONDS)
                                     .build();
            client.write(point);
            return TemplateChangeStatus.builder().status(true).id(id).build();
        } else {
            return TemplateChangeStatus.builder().status(false).id(id).message("Can't find the template").build();
        }
    }
}

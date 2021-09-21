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

package org.apache.skywalking.oap.server.storage.plugin.iotdb;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.MultipleQueryUnifiedIndex;
import org.apache.skywalking.oap.server.core.storage.annotation.QueryUnifiedIndex;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class IoTDBClientTest {
    private IoTDBClient client;

    @Rule
    public GenericContainer iotdb = new GenericContainer(DockerImageName.parse("apache/iotdb:0.12.2-node")).withExposedPorts(6667);

    @Before
    public void addTableMetaInfo() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost(iotdb.getHost());
        config.setRpcPort(iotdb.getFirstMappedPort());
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        client = new IoTDBClient(config);
        client.connect();

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ProfileTaskLogRecord.class, ProfileTaskLogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model profileTaskLogRecordModel = new Model(
                ProfileTaskLogRecord.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ProfileTaskLogRecord.class), timeRelativeID);

        scopeId = 1;
        record = false;
        superDataset = false;
        timeRelativeID = true;
        modelColumns = new ArrayList<>();
        extraQueryIndices = new ArrayList<>();
        retrieval(InstanceTraffic.class, InstanceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model instanceTrafficModel = new Model(
                InstanceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(InstanceTraffic.class), timeRelativeID);

        IoTDBTableMetaInfo.addModel(profileTaskLogRecordModel);
        IoTDBTableMetaInfo.addModel(instanceTrafficModel);
    }

    @Test
    public void write() {
        StorageHashMapBuilder<ProfileTaskLogRecord> profileTaskLogBuilder = new ProfileTaskLogRecord.Builder();
        Map<String, Object> profileTaskLogRecordMap = new HashMap<>();
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.TASK_ID, "task_id_1");
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.INSTANCE_ID, "instance_id_1");
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.OPERATION_TYPE, 1);
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.OPERATION_TIME, 2L);
        profileTaskLogRecordMap.put(ProfileTaskLogRecord.TIME_BUCKET, 3L);
        ProfileTaskLogRecord profileTaskLogRecord = profileTaskLogBuilder.storage2Entity(profileTaskLogRecordMap);

        StorageHashMapBuilder<InstanceTraffic> instanceTrafficBuilder = new InstanceTraffic.Builder();
        Map<String, Object> instanceTrafficMap = new HashMap<>();
        instanceTrafficMap.put(InstanceTraffic.SERVICE_ID, "service_id_1");
        instanceTrafficMap.put(InstanceTraffic.NAME, "name_1");
        instanceTrafficMap.put(InstanceTraffic.LAST_PING_TIME_BUCKET, 1L);
        instanceTrafficMap.put(InstanceTraffic.PROPERTIES, "{test: 1}");
        instanceTrafficMap.put(InstanceTraffic.ENTITY_ID, "entity_id_1");
        instanceTrafficMap.put(InstanceTraffic.TIME_BUCKET, 4L);
        InstanceTraffic instanceTraffic = instanceTrafficBuilder.storage2Entity(instanceTrafficMap);

        IoTDBInsertRequest recordRequest = new IoTDBInsertRequest(ProfileTaskLogRecord.INDEX_NAME, 3L, profileTaskLogRecord, profileTaskLogBuilder);
        IoTDBInsertRequest metricsRequest = new IoTDBInsertRequest(InstanceTraffic.INDEX_NAME, 4L, instanceTraffic, instanceTrafficBuilder);
        try {
            client.write(recordRequest);
            client.write(metricsRequest);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testWrite() {
    }

    @Test
    public void queryForList() throws IOException {
        String modelName = "instance_traffic";
        String querySQL = "select * from root.skywalking.instance_traffic align by device";
        StorageHashMapBuilder<InstanceTraffic> storageBuilder = new InstanceTraffic.Builder();

        List<InstanceTraffic> instanceTrafficList = new ArrayList<>();
        List<? super StorageData> storageDataList = client.filterQuery(modelName, querySQL, storageBuilder);
        storageDataList.forEach(storageData -> instanceTrafficList.add((InstanceTraffic) storageData));

        for (InstanceTraffic instanceTraffic : instanceTrafficList) {
            if (instanceTraffic.getName().equals("name_1")) {
                assertThat(instanceTraffic.id()).isEqualTo("service_id_1_bmFtZV8x");
                assertThat(instanceTraffic.getServiceId()).isEqualTo("service_id_1");
            }
        }
    }

    @Test
    public void queryForListWithContains() {
    }

    @Test
    public void queryWithSelect() {
    }

    @Test
    public void queryWithAgg() {
    }

    @Test
    public void deleteData() {
    }

    public static void retrieval(final Class<?> clazz,
                                 final String modelName,
                                 final List<ModelColumn> modelColumns,
                                 final List<ExtraQueryIndex> extraQueryIndices,
                                 final int scopeId) {
        if (log.isDebugEnabled()) {
            log.debug("Analysis {} to generate Model.", clazz.getName());
        }

        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                // Use the column#length as the default column length, as read the system env as the override mechanism.
                // Log the error but don't block the startup sequence.
                int columnLength = column.length();
                final String lengthEnvVariable = column.lengthEnvVariable();
                if (StringUtil.isNotEmpty(lengthEnvVariable)) {
                    final String envValue = System.getenv(lengthEnvVariable);
                    if (StringUtil.isNotEmpty(envValue)) {
                        try {
                            columnLength = Integer.parseInt(envValue);
                        } catch (NumberFormatException e) {
                            log.error("Model [{}] Column [{}], illegal value {} of column length from system env [{}]",
                                    modelName, column.columnName(), envValue, lengthEnvVariable
                            );
                        }
                    }
                }
                modelColumns.add(
                        new ModelColumn(
                                new ColumnName(modelName, column.columnName()), field.getType(), field.getGenericType(),
                                column.matchQuery(), column.storageOnly(), column.dataType().isValue(), columnLength,
                                column.analyzer()
                        ));
                if (log.isDebugEnabled()) {
                    log.debug("The field named {} with the {} type", column.columnName(), field.getType());
                }
                if (column.dataType().isValue()) {
                    ValueColumnMetadata.INSTANCE.putIfAbsent(
                            modelName, column.columnName(), column.dataType(), column.function(),
                            column.defaultValue(), scopeId
                    );
                }

                List<QueryUnifiedIndex> indexDefinitions = new ArrayList<>();
                if (field.isAnnotationPresent(QueryUnifiedIndex.class)) {
                    indexDefinitions.add(field.getAnnotation(QueryUnifiedIndex.class));
                }

                if (field.isAnnotationPresent(MultipleQueryUnifiedIndex.class)) {
                    Collections.addAll(indexDefinitions, field.getAnnotation(MultipleQueryUnifiedIndex.class).value());
                }

                indexDefinitions.forEach(indexDefinition -> extraQueryIndices.add(new ExtraQueryIndex(
                        column.columnName(),
                        indexDefinition.withColumns()
                )));
            }
        }

        if (Objects.nonNull(clazz.getSuperclass())) {
            retrieval(clazz.getSuperclass(), modelName, modelColumns, extraQueryIndices, scopeId);
        }
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofDataQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCEntityConverters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

@RequiredArgsConstructor
public class JDBCPprofDataQueryDAO implements IPprofDataQueryDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<PprofProfilingDataRecord> getByTaskIdAndInstances(String taskId,
                                                                  List<String> instanceIds) throws IOException {
        if (StringUtil.isBlank(taskId)) {
            return new ArrayList<>();
        }
        List<String> tables = tableHelper.getTablesWithinTTL(PprofProfilingDataRecord.INDEX_NAME);
        List<PprofProfilingDataRecord> results = new ArrayList<>();
        for (final var table : tables) {
            List<Object> condition = new ArrayList<>(4);
            StringBuilder sql = new StringBuilder()
                .append("select * from ").append(table)
                .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            condition.add(PprofProfilingDataRecord.INDEX_NAME);

            if (CollectionUtils.isNotEmpty(instanceIds)) {
                sql.append(" and ").append(PprofProfilingDataRecord.INSTANCE_ID).append(" in (?) ");
                String joinedInstanceIds = String.join(",", instanceIds);
                condition.add(joinedInstanceIds);
            }

            results.addAll(
                jdbcClient.executeQuery(
                    sql.toString(),
                    resultSet -> {
                        final var result = new ArrayList<PprofProfilingDataRecord>();
                        while (resultSet.next()) {
                            result.add(parseData(resultSet));
                        }
                        return result;
                    },
                    condition.toArray(new Object[0])
                )
            );
        }
        return results;
    }

    private PprofProfilingDataRecord parseData(ResultSet data) {
        final PprofProfilingDataRecord.Builder builder = new PprofProfilingDataRecord.Builder();
        PprofProfilingDataRecord pprofProfilingDataRecord = builder.storage2Entity(JDBCEntityConverters.toEntity(data));
        byte[] dataBinary = pprofProfilingDataRecord.getDataBinary();
        if (dataBinary != null) {
            byte[] decodeResult = Base64.getDecoder().decode(dataBinary);
            pprofProfilingDataRecord.setDataBinary(decodeResult);
        }
        return pprofProfilingDataRecord;
    }
}
 
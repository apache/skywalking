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

 package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

 import com.google.common.collect.ImmutableSet;
 import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
 import org.apache.skywalking.banyandb.v1.client.DataPoint;
 import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
 import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
 import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
 import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
 import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
 import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
 import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
 
 import java.io.IOException;
 import java.util.Collections;
 import java.util.List;
 import java.util.Set;
 import java.util.stream.Collectors;
 
 public class BanyanDBEBPFProfilingScheduleQueryDAO extends AbstractBanyanDBDAO implements IEBPFProfilingScheduleDAO {
     private static final Set<String> TAGS = ImmutableSet.of(EBPFProfilingScheduleRecord.START_TIME,
             EBPFProfilingScheduleRecord.EBPF_PROFILING_SCHEDULE_ID,
             EBPFProfilingScheduleRecord.TASK_ID,
             EBPFProfilingScheduleRecord.PROCESS_ID,
             EBPFProfilingScheduleRecord.END_TIME);
 
     public BanyanDBEBPFProfilingScheduleQueryDAO(BanyanDBStorageClient client) {
         super(client);
     }
 
     @Override
     public List<EBPFProfilingSchedule> querySchedules(String taskId) throws IOException {
         MeasureQueryResponse resp = query(EBPFProfilingScheduleRecord.INDEX_NAME,
                 TAGS,
                 Collections.emptySet(), new QueryBuilder<MeasureQuery>() {
                     @Override
                     protected void apply(MeasureQuery query) {
                         query.and(eq(EBPFProfilingScheduleRecord.TASK_ID, taskId));
                         query.setOrderBy(new AbstractQuery.OrderBy(EBPFProfilingScheduleRecord.START_TIME, AbstractQuery.Sort.DESC));
                     }
                 });
 
         return resp.getDataPoints().stream().map(this::buildEBPFProfilingSchedule).collect(Collectors.toList());
     }
 
     private EBPFProfilingSchedule buildEBPFProfilingSchedule(DataPoint dataPoint) {
         final EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
         schedule.setScheduleId(dataPoint.getTagValue(EBPFProfilingScheduleRecord.EBPF_PROFILING_SCHEDULE_ID));
         schedule.setTaskId(dataPoint.getTagValue(EBPFProfilingScheduleRecord.TASK_ID));
         schedule.setProcessId(dataPoint.getTagValue(EBPFProfilingScheduleRecord.PROCESS_ID));
         schedule.setStartTime(((Number) dataPoint.getTagValue(EBPFProfilingScheduleRecord.START_TIME)).longValue());
         schedule.setEndTime(((Number) dataPoint.getTagValue(EBPFProfilingScheduleRecord.END_TIME)).longValue());
         return schedule;
     }
 }
 
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

package org.apache.skywalking.apm.collector.ui.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.ui.common.ResponseTimeTrend;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.ThroughputTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.ui.server.CPUTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.GCTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.MemoryTrend;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;

/**
 * @author peng-yongsheng
 */
public class ServerService {

    private final Gson gson = new Gson();
    private final IInstanceUIDAO instanceUIDAO;
    private final IInstanceMetricUIDAO instanceMetricUIDAO;
    private final ICpuMetricUIDAO cpuMetricUIDAO;
    private final IGCMetricUIDAO gcMetricUIDAO;
    private final IMemoryMetricUIDAO memoryMetricUIDAO;

    public ServerService(ModuleManager moduleManager) {
        this.instanceUIDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceUIDAO.class);
        this.instanceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceMetricUIDAO.class);
        this.cpuMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(ICpuMetricUIDAO.class);
        this.gcMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IGCMetricUIDAO.class);
        this.memoryMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IMemoryMetricUIDAO.class);
    }

    public List<AppServerInfo> searchServer(String keyword, long start, long end) {
        List<AppServerInfo> serverInfos = instanceUIDAO.searchServer(keyword, start, end);
        serverInfos.forEach(serverInfo -> {
            if (serverInfo.getId() == Const.NONE_INSTANCE_ID) {
                serverInfos.remove(serverInfo);
            }
        });

        buildAppServerInfo(serverInfos);
        return serverInfos;
    }

    public List<AppServerInfo> getAllServer(int applicationId, long start, long end) {
        List<AppServerInfo> serverInfos = instanceUIDAO.getAllServer(applicationId, start, end);
        buildAppServerInfo(serverInfos);
        return serverInfos;
    }

    public ResponseTimeTrend getServerResponseTimeTrend(int instanceId, Step step, long start,
        long end) throws ParseException {
        ResponseTimeTrend responseTimeTrend = new ResponseTimeTrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, start, end);
        List<Integer> trends = instanceMetricUIDAO.getResponseTimeTrend(instanceId, step, durationPoints);
        responseTimeTrend.setTrendList(trends);
        return responseTimeTrend;
    }

    public ThroughputTrend getServerTPSTrend(int instanceId, Step step, long start, long end) throws ParseException {
        ThroughputTrend throughputTrend = new ThroughputTrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, start, end);
        List<Integer> trends = instanceMetricUIDAO.getServerTPSTrend(instanceId, step, durationPoints);
        throughputTrend.setTrendList(trends);
        return throughputTrend;
    }

    public CPUTrend getCPUTrend(int instanceId, Step step, long start, long end) throws ParseException {
        CPUTrend cpuTrend = new CPUTrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, start, end);
        List<Integer> trends = cpuMetricUIDAO.getCPUTrend(instanceId, step, durationPoints);
        cpuTrend.setCost(trends);
        return cpuTrend;
    }

    public GCTrend getGCTrend(int instanceId, Step step, long start, long end) throws ParseException {
        GCTrend gcTrend = new GCTrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, start, end);
        List<Integer> youngGCTrend = gcMetricUIDAO.getYoungGCTrend(instanceId, step, durationPoints);
        gcTrend.setYoungGC(youngGCTrend);
        List<Integer> oldGCTrend = gcMetricUIDAO.getOldGCTrend(instanceId, step, durationPoints);
        gcTrend.setOldGC(oldGCTrend);
        return gcTrend;
    }

    public MemoryTrend getMemoryTrend(int instanceId, Step step, long start, long end) throws ParseException {
        MemoryTrend memoryTrend = new MemoryTrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, start, end);
        IMemoryMetricUIDAO.Trend heapMemoryTrend = memoryMetricUIDAO.getHeapMemoryTrend(instanceId, step, durationPoints);
        memoryTrend.setHeap(heapMemoryTrend.getMetrics());
        memoryTrend.setMaxHeap(heapMemoryTrend.getMaxMetrics());

        IMemoryMetricUIDAO.Trend noHeapMemoryTrend = memoryMetricUIDAO.getNoHeapMemoryTrend(instanceId, step, durationPoints);
        memoryTrend.setNoheap(noHeapMemoryTrend.getMetrics());
        memoryTrend.setMaxNoheap(noHeapMemoryTrend.getMaxMetrics());

        return memoryTrend;
    }

    private void buildAppServerInfo(List<AppServerInfo> serverInfos) {
        serverInfos.forEach(serverInfo -> {
            if (StringUtils.isNotEmpty(serverInfo.getOsInfo())) {
                JsonObject osInfoJson = gson.fromJson(serverInfo.getOsInfo(), JsonObject.class);
                if (osInfoJson.has("osName")) {
                    serverInfo.setName(osInfoJson.get("osName").getAsString());
                }
                if (osInfoJson.has("hostName")) {
                    serverInfo.setHost(osInfoJson.get("hostName").getAsString());
                }
                if (osInfoJson.has("processId")) {
                    serverInfo.setPid(osInfoJson.get("processId").getAsInt());
                }

                if (osInfoJson.has("ipv4s")) {
                    JsonArray ipv4Array = osInfoJson.get("ipv4s").getAsJsonArray();

                    List<String> ipv4s = new LinkedList<>();
                    ipv4Array.forEach(ipv4 -> ipv4s.add(ipv4.getAsString()));
                    serverInfo.setIpv4(ipv4s);
                }
            }
        });
    }
}

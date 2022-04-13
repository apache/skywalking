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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler;

import com.google.gson.JsonObject;
import io.grpc.stub.StreamObserver;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFHostProcessDownstream;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFHostProcessMetadata;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFProcessDownstream;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFProcessEntityMetadata;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFProcessPingPkgList;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFProcessProperties;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFProcessReportList;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFProcessServiceGrpc;
import org.apache.skywalking.apm.network.ebpf.profiling.process.v3.EBPFReportProcessDownstream;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.Process;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;

import java.util.ArrayList;

public class EBPFProcessServiceHandler extends EBPFProcessServiceGrpc.EBPFProcessServiceImplBase implements GRPCHandler {

    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;

    public EBPFProcessServiceHandler(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.namingControl = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(NamingControl.class);
    }

    @Override
    public void reportProcesses(EBPFProcessReportList request, StreamObserver<EBPFReportProcessDownstream> responseObserver) {
        final String agentId = request.getEbpfAgentID();

        // build per process data
        final ArrayList<Tuple2<Process, EBPFProcessDownstream>> processes = new ArrayList<>();
        for (EBPFProcessProperties ebpfProcessProperties : request.getProcessesList()) {
            Tuple2<Process, EBPFProcessDownstream> processData = null;
            if (ebpfProcessProperties.hasHostProcess()) {
                processData = prepareReportHostProcess(ebpfProcessProperties.getHostProcess(), agentId);
            }

            if (processData != null) {
                processes.add(processData);
            }
        }

        // report process and downstream the process id data
        final EBPFReportProcessDownstream.Builder builder = EBPFReportProcessDownstream.newBuilder();
        processes.stream().forEach(e -> {
            sourceReceiver.receive(e._1);
            builder.addProcesses(e._2);
        });

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void keepAlive(EBPFProcessPingPkgList request, StreamObserver<Commands> responseObserver) {
        final long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute);

        request.getProcessesList().forEach(p -> {
            final EBPFProcessEntityMetadata entity = p.getEntityMetadata();
            final String serviceName = namingControl.formatServiceName(entity.getServiceName());
            final String instanceName = namingControl.formatInstanceName(entity.getInstanceName());
            final Layer layer = Layer.valueOf(entity.getLayer());

            // process
            final Process processUpdate = new Process();
            processUpdate.setServiceName(serviceName);
            processUpdate.setInstanceName(instanceName);
            processUpdate.setLayer(layer);
            processUpdate.setServiceNormal(true);
            processUpdate.setName(entity.getProcessName());
            processUpdate.setTimeBucket(timeBucket);
            sourceReceiver.receive(processUpdate);

            // instance
            final ServiceInstanceUpdate serviceInstanceUpdate = new ServiceInstanceUpdate();
            serviceInstanceUpdate.setServiceId(IDManager.ServiceID.buildId(serviceName, true));
            serviceInstanceUpdate.setName(instanceName);
            serviceInstanceUpdate.setTimeBucket(timeBucket);
            serviceInstanceUpdate.setLayer(layer);
            sourceReceiver.receive(serviceInstanceUpdate);

            // service
            final ServiceMeta serviceMeta = new ServiceMeta();
            serviceMeta.setName(serviceName);
            serviceMeta.setTimeBucket(timeBucket);
            serviceMeta.setLayer(layer);
            sourceReceiver.receive(serviceMeta);
        });

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

    private Tuple2<Process, EBPFProcessDownstream> prepareReportHostProcess(EBPFHostProcessMetadata hostProcess, String agentId) {
        final Process process = new Process();

        // entity
        process.setServiceName(namingControl.formatServiceName(hostProcess.getEntity().getServiceName()));
        process.setServiceNormal(true);
        process.setLayer(Layer.valueOf(hostProcess.getEntity().getLayer()));
        process.setInstanceName(namingControl.formatInstanceName(hostProcess.getEntity().getInstanceName()));
        process.setName(hostProcess.getEntity().getProcessName());

        // metadata
        process.setDetectType(ProcessDetectType.VM);
        process.setAgentId(agentId);
        final JsonObject properties = new JsonObject();
        properties.addProperty(ProcessTraffic.PropertyUtil.HOST_IP, hostProcess.getHostIP());
        properties.addProperty(ProcessTraffic.PropertyUtil.PID, hostProcess.getPid());
        properties.addProperty(ProcessTraffic.PropertyUtil.COMMAND_LINE, hostProcess.getCmd());
        process.setProperties(properties);

        // timestamp
        process.setTimeBucket(
                TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute));

        process.prepare();
        final String processId = process.getEntityId();
        final EBPFProcessDownstream downstream = EBPFProcessDownstream.newBuilder()
                .setProcessId(processId)
                .setHostProcess(EBPFHostProcessDownstream.newBuilder()
                        .setPid(hostProcess.getPid())
                        .build())
                .build();
        return Tuple.of(process, downstream);
    }
}

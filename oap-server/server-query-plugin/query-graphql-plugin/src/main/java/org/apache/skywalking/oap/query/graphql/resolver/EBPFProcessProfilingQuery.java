package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingCondition;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;
import java.util.List;

public class EBPFProcessProfilingQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private EBPFProfilingQueryService queryService;

    public EBPFProcessProfilingQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public EBPFProfilingQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME)
                    .provider()
                    .getService(EBPFProfilingQueryService.class);
        }
        return queryService;
    }

    public List<EBPFProfilingTask> queryEBPFProfilingTasks(EBPFProfilingCondition query) throws IOException {
        return getQueryService().queryEBPFProfilingTasks(query);
    }

    public List<EBPFProfilingSchedule> queryEBPFProfilingSchedules(String taskId, Duration duration) throws IOException {
        return getQueryService().queryEBPFProfilingSchedules(taskId, duration);
    }

    public EBPFProfilingAnalyzation getEBPFProfilingAnalyzation(String taskId, List<EBPFProfilingAnalyzeTimeRange> timeRanges) throws IOException {
        return getQueryService().getEBPFProfilingAnalyzation(taskId, timeRanges);
    }
}
package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import java.util.List;
import org.apache.skywalking.oap.server.library.module.Service;

public interface IMeterProcessService extends Service {

    void initMeters();

    MeterProcessor createProcessor();

    List<MeterBuilder> enabledBuilders();

}

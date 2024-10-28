package org.apache.skywalking.oap.server.receiver.asyncprofiler.module;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class AsyncProfilerModuleConfig extends ModuleConfig {
    /**
     * Used to manage the maximum size of the jfr file that can be received, the unit is Byte
     * default is 30M
     */
    private int jfrMaxSize = 30*1024*1024;
}

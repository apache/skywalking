package org.skywalking.apm.collector.remote;

import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.remote.service.DataService;

/**
 * @author peng-yongsheng
 */
public class RemoteModule extends Module {

    public static final String NAME = "remote";

    @Override public String name() {
        return NAME;
    }

    @Override public Class[] services() {
        return new Class[] {DataService.class};
    }
}

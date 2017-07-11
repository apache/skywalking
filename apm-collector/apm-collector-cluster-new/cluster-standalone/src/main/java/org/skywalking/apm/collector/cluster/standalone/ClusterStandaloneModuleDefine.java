package org.skywalking.apm.collector.cluster.standalone;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationWriter;
import org.skywalking.apm.collector.core.framework.DataInitializer;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;

/**
 * @author pengys5
 */
public class ClusterStandaloneModuleDefine extends ClusterModuleDefine {

    @Override public ModuleGroup group() {
        return ModuleGroup.Cluster;
    }

    @Override public String name() {
        return "standalone";
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterStandaloneConfigParser();
    }

    @Override protected Client client() {
        return new H2Client();
    }

    @Override protected DataInitializer dataInitializer() {
        return new ClusterStandaloneDataInitializer();
    }

    @Override protected ClusterModuleRegistrationWriter registrationWriter() {
        return new ClusterStandaloneModuleRegistrationWriter();
    }
}

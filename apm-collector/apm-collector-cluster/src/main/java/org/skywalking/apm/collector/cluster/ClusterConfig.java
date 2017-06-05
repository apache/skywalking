package org.skywalking.apm.collector.cluster;

/**
 * A static class contains some config values of cluster.
 * {@link Cluster.Current#HOSTNAME} is a ip address of server which start this process.
 * {@link Cluster.Current#PORT} is a PORT of server use to bind
 * {@link Cluster.Current#ROLES} is a ROLES of workers that use to create workers which
 * has those role in this process.
 * {@link Cluster#SEED_NODES} is a SEED_NODES which cluster have, List of strings, e.g. SEED_NODES = "ip:PORT,ip:PORT"..
 *
 * @author pengys5
 */
public class ClusterConfig {

    public static class Cluster {
        public static class Current {
            public static String HOSTNAME = "";
            public static String PORT = "";
            public static String ROLES = "";
        }

        public static String SEED_NODES = "";
    }
}

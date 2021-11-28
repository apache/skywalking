package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic},
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic}
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic}
 * are all streams.
 */
public class BanyanDBMetadataQueryDAO implements IMetadataQueryDAO {
    @Override
    public List<Service> getAllServices(String group) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Service> getAllBrowserServices() throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Service> searchServices(NodeType nodeType, String keyword) throws IOException {
        return null;
    }

    @Override
    public Service searchService(NodeType nodeType, String serviceCode) throws IOException {
        return null;
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, String serviceId, int limit) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp, String serviceId) throws IOException {
        return Collections.emptyList();
    }
}

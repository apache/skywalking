package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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
    public List<Service> searchServices(String keyword) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public Service searchService(String serviceCode) throws IOException {
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

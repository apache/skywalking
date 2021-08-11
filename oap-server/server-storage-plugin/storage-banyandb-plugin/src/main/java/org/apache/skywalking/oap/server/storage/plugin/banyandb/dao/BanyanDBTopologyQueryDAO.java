package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BanyanDBTopologyQueryDAO implements ITopologyQueryDAO {
    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB, List<String> serviceIds) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB, List<String> serviceIds) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(long startTB, long endTB) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(long startTB, long endTB) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId, String serverServiceId, long startTB, long endTB) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId, String serverServiceId, long startTB, long endTB) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(long startTB, long endTB, String destEndpointId) throws IOException {
        return Collections.emptyList();
    }
}

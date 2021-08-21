package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.junit.Before;
import org.junit.Test;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;

public class IoTDBTopologyQueryDAOTest {
    private IoTDBTopologyQueryDAO topologyQueryDAO;

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost("127.0.0.1");
        config.setRpcPort(6667);
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        IoTDBClient client = new IoTDBClient(config);
        client.connect();

        topologyQueryDAO = new IoTDBTopologyQueryDAO(client);

        addServiceRelationServerSideMetrics(client);
        addServiceRelationClientSideMetrics(client);
        addServiceInstanceRelationServerSideMetrics(client);
        addServiceInstanceRelationClientSideMetrics(client);
        addEndpointRelationServerSideMetrics(client);
    }

    @Test
    public void loadServiceRelationsDetectedAtServerSide() throws IOException {
        List<String> serviceIds = new ArrayList<>();
        serviceIds.add("service-id1");
        List<Call.CallDetail> calls = topologyQueryDAO
                .loadServiceRelationsDetectedAtServerSide(10000000000001L, 10000000000002L, serviceIds);
        Call.CallDetail targetCall = new Call.CallDetail();
        targetCall.buildFromServiceRelation("entity-id1", 1, DetectPoint.SERVER);
        for (Call.CallDetail call : calls) {
            assert call.getId().equals(targetCall.getId());
            assert call.getSource().equals(targetCall.getSource());
            assert call.getTarget().equals(targetCall.getTarget());
            assert call.getComponentId().equals(targetCall.getComponentId());
            assert call.getDetectPoint() == targetCall.getDetectPoint();
        }
    }

    @Test
    public void loadServiceRelationDetectedAtClientSide() throws IOException {
        List<String> serviceIds = new ArrayList<>();
        serviceIds.add("service-id1");
        List<Call.CallDetail> calls = topologyQueryDAO
                .loadServiceRelationDetectedAtClientSide(10000000000001L, 10000000000002L, serviceIds);
        Call.CallDetail targetCall = new Call.CallDetail();
        targetCall.buildFromServiceRelation("entity-id1", 1, DetectPoint.CLIENT);
        for (Call.CallDetail call : calls) {
            assert call.getId().equals(targetCall.getId());
            assert call.getSource().equals(targetCall.getSource());
            assert call.getTarget().equals(targetCall.getTarget());
            assert call.getComponentId().equals(targetCall.getComponentId());
            assert call.getDetectPoint() == targetCall.getDetectPoint();
        }
    }

    @Test
    public void testLoadServiceRelationsDetectedAtServerSide() throws IOException {
        List<Call.CallDetail> calls = topologyQueryDAO
                .loadServiceRelationsDetectedAtServerSide(10000000000001L, 10000000000002L);
        Call.CallDetail targetCall = new Call.CallDetail();
        targetCall.buildFromServiceRelation("entity-id1", 1, DetectPoint.SERVER);
        for (Call.CallDetail call : calls) {
            assert call.getId().equals(targetCall.getId());
            assert call.getSource().equals(targetCall.getSource());
            assert call.getTarget().equals(targetCall.getTarget());
            assert call.getComponentId().equals(targetCall.getComponentId());
            assert call.getDetectPoint() == targetCall.getDetectPoint();
        }
    }

    @Test
    public void testLoadServiceRelationDetectedAtClientSide() throws IOException {
        List<Call.CallDetail> calls = topologyQueryDAO
                .loadServiceRelationDetectedAtClientSide(10000000000001L, 10000000000002L);
        Call.CallDetail targetCall = new Call.CallDetail();
        targetCall.buildFromServiceRelation("entity-id1", 1, DetectPoint.CLIENT);
        for (Call.CallDetail call : calls) {
            assert call.getId().equals(targetCall.getId());
            assert call.getSource().equals(targetCall.getSource());
            assert call.getTarget().equals(targetCall.getTarget());
            assert call.getComponentId().equals(targetCall.getComponentId());
            assert call.getDetectPoint() == targetCall.getDetectPoint();
        }
    }

    @Test
    public void loadInstanceRelationDetectedAtServerSide() throws IOException {
        List<Call.CallDetail> calls = topologyQueryDAO.loadInstanceRelationDetectedAtServerSide(
                "service-id1", "service-id2", 10000000000001L, 10000000000002L);
        Call.CallDetail targetCall = new Call.CallDetail();
        targetCall.buildFromInstanceRelation("entity-id1", 1, DetectPoint.SERVER);
        for (Call.CallDetail call : calls) {
            assert call.getId().equals(targetCall.getId());
            assert call.getSource().equals(targetCall.getSource());
            assert call.getTarget().equals(targetCall.getTarget());
            assert call.getComponentId().equals(targetCall.getComponentId());
            assert call.getDetectPoint() == targetCall.getDetectPoint();
        }
    }

    @Test
    public void loadInstanceRelationDetectedAtClientSide() throws IOException {
        List<Call.CallDetail> calls = topologyQueryDAO.loadInstanceRelationDetectedAtClientSide(
                "service-id1", "service-id2", 10000000000001L, 10000000000002L);
        Call.CallDetail targetCall = new Call.CallDetail();
        targetCall.buildFromInstanceRelation("entity-id1", 1, DetectPoint.CLIENT);
        for (Call.CallDetail call : calls) {
            assert call.getId().equals(targetCall.getId());
            assert call.getSource().equals(targetCall.getSource());
            assert call.getTarget().equals(targetCall.getTarget());
            assert call.getComponentId().equals(targetCall.getComponentId());
            assert call.getDetectPoint() == targetCall.getDetectPoint();
        }
    }

    @Test
    public void loadEndpointRelation() throws IOException {
        Call.CallDetail targetCall = new Call.CallDetail();
        targetCall.buildFromEndpointRelation("VXNlcg==.0-VXNlcg==-amV0dHljbGllbnQtc2NlbmFyaW8=\\.1-L2Nhc2UvaGVhbHRoQ2hlY2s=", DetectPoint.SERVER);
        List<Call.CallDetail> calls = topologyQueryDAO.loadEndpointRelation(
                10000000000001L, 10000000000002L, "endpoint-1");
        for (Call.CallDetail call : calls) {
            assert call.getId().equals(targetCall.getId());
            assert call.getSource().equals(targetCall.getSource());
            assert call.getTarget().equals(targetCall.getTarget());
            assert call.getComponentId().equals(targetCall.getComponentId());
            assert call.getDetectPoint() == targetCall.getDetectPoint();
        }
    }

    private void addServiceRelationServerSideMetrics(IoTDBClient client) throws IOException {
        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ServiceRelationServerSideMetrics.class, ServiceRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model serviceRelationServerSideMetricsModel = new Model(
                ServiceRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ServiceRelationServerSideMetrics.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(serviceRelationServerSideMetricsModel);

        StorageHashMapBuilder<ServiceRelationServerSideMetrics> storageBuilder = new ServiceRelationServerSideMetrics.Builder();
        Map<String, Object> map = new HashMap<>();
        map.put(ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, "service-id1");
        map.put(ServiceRelationServerSideMetrics.DEST_SERVICE_ID, "service-id2");
        map.put(ServiceRelationServerSideMetrics.COMPONENT_ID, 1);
        map.put(ServiceRelationServerSideMetrics.ENTITY_ID, "entity-id1");
        map.put(ServiceRelationServerSideMetrics.TIME_BUCKET, 1L);
        ServiceRelationServerSideMetrics serviceRelationServerSideMetrics = storageBuilder.storage2Entity(map);
        IoTDBInsertRequest request = new IoTDBInsertRequest(ServiceRelationServerSideMetrics.INDEX_NAME, TimeBucket.getTimestamp(10000000000001L), serviceRelationServerSideMetrics, storageBuilder);
        client.write(request);
    }

    private void addServiceRelationClientSideMetrics(IoTDBClient client) throws IOException {
        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ServiceRelationClientSideMetrics.class, ServiceRelationClientSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model serviceRelationClientSideMetricsModel = new Model(
                ServiceRelationClientSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ServiceRelationClientSideMetrics.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(serviceRelationClientSideMetricsModel);

        StorageHashMapBuilder<ServiceRelationClientSideMetrics> storageBuilder = new ServiceRelationClientSideMetrics.Builder();
        Map<String, Object> map = new HashMap<>();
        map.put(ServiceRelationClientSideMetrics.SOURCE_SERVICE_ID, "service-id1");
        map.put(ServiceRelationClientSideMetrics.DEST_SERVICE_ID, "service-id2");
        map.put(ServiceRelationClientSideMetrics.COMPONENT_ID, 1);
        map.put(ServiceRelationClientSideMetrics.ENTITY_ID, "entity-id1");
        map.put(ServiceRelationClientSideMetrics.TIME_BUCKET, 1L);
        ServiceRelationClientSideMetrics serviceRelationClientSideMetrics = storageBuilder.storage2Entity(map);
        IoTDBInsertRequest request = new IoTDBInsertRequest(ServiceRelationClientSideMetrics.INDEX_NAME, TimeBucket.getTimestamp(10000000000001L), serviceRelationClientSideMetrics, storageBuilder);
        client.write(request);
    }

    private void addServiceInstanceRelationServerSideMetrics(IoTDBClient client) throws IOException {
        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ServiceInstanceRelationServerSideMetrics.class, ServiceInstanceRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model serviceInstanceRelationServerSideMetricsModel = new Model(
                ServiceInstanceRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ServiceInstanceRelationServerSideMetrics.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(serviceInstanceRelationServerSideMetricsModel);

        StorageHashMapBuilder<ServiceInstanceRelationServerSideMetrics> storageBuilder = new ServiceInstanceRelationServerSideMetrics.Builder();
        Map<String, Object> map = new HashMap<>();
        map.put(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID, "service-id1");
        map.put(ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_INSTANCE_ID, "instance_id_1");
        map.put(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID, "service-id2");
        map.put(ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_INSTANCE_ID, "instance_1");
        map.put(ServiceInstanceRelationServerSideMetrics.COMPONENT_ID, 1);
        map.put(ServiceInstanceRelationServerSideMetrics.ENTITY_ID, "entity-id1");
        map.put(ServiceInstanceRelationServerSideMetrics.TIME_BUCKET, 1L);
        ServiceInstanceRelationServerSideMetrics serviceInstanceRelationServerSideMetrics = storageBuilder.storage2Entity(map);
        IoTDBInsertRequest request = new IoTDBInsertRequest(ServiceInstanceRelationServerSideMetrics.INDEX_NAME, TimeBucket.getTimestamp(10000000000001L), serviceInstanceRelationServerSideMetrics, storageBuilder);
        client.write(request);
    }

    private void addServiceInstanceRelationClientSideMetrics(IoTDBClient client) throws IOException {
        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ServiceInstanceRelationClientSideMetrics.class, ServiceInstanceRelationClientSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model serviceInstanceRelationClientSideMetricsModel = new Model(
                ServiceInstanceRelationClientSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ServiceInstanceRelationClientSideMetrics.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(serviceInstanceRelationClientSideMetricsModel);

        StorageHashMapBuilder<ServiceInstanceRelationClientSideMetrics> storageBuilder = new ServiceInstanceRelationClientSideMetrics.Builder();
        Map<String, Object> map = new HashMap<>();
        map.put(ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID, "service-id1");
        map.put(ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_INSTANCE_ID, "instance_id_1");
        map.put(ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID, "service-id2");
        map.put(ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_INSTANCE_ID, "instance_1");
        map.put(ServiceInstanceRelationClientSideMetrics.COMPONENT_ID, 1);
        map.put(ServiceInstanceRelationClientSideMetrics.ENTITY_ID, "entity-id1");
        map.put(ServiceInstanceRelationClientSideMetrics.TIME_BUCKET, 1L);
        ServiceInstanceRelationClientSideMetrics serviceInstanceRelationClientSideMetrics = storageBuilder.storage2Entity(map);
        IoTDBInsertRequest request = new IoTDBInsertRequest(ServiceInstanceRelationClientSideMetrics.INDEX_NAME, TimeBucket.getTimestamp(10000000000001L), serviceInstanceRelationClientSideMetrics, storageBuilder);
        client.write(request);
    }

    private void addEndpointRelationServerSideMetrics(IoTDBClient client) throws IOException {
        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(EndpointRelationServerSideMetrics.class, EndpointRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model endpointRelationServerSideMetricsModel = new Model(
                EndpointRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(EndpointRelationServerSideMetrics.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(endpointRelationServerSideMetricsModel);

        StorageHashMapBuilder<EndpointRelationServerSideMetrics> storageBuilder = new EndpointRelationServerSideMetrics.Builder();
        Map<String, Object> map = new HashMap<>();
        map.put(EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, "endpoint-1");
        map.put(EndpointRelationServerSideMetrics.DEST_ENDPOINT, "endpoint_2");
        map.put(EndpointRelationServerSideMetrics.COMPONENT_ID, 1);
        // TODO to build a legal entity_id which cannot contain "//"
        map.put(EndpointRelationServerSideMetrics.ENTITY_ID, "");
        map.put(EndpointRelationServerSideMetrics.TIME_BUCKET, 1L);
        EndpointRelationServerSideMetrics endpointRelationServerSideMetrics = storageBuilder.storage2Entity(map);
        IoTDBInsertRequest request = new IoTDBInsertRequest(EndpointRelationServerSideMetrics.INDEX_NAME, TimeBucket.getTimestamp(10000000000001L), endpointRelationServerSideMetrics, storageBuilder);
        client.write(request);
    }
}
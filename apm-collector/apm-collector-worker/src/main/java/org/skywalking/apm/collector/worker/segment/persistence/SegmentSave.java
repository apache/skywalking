package org.skywalking.apm.collector.worker.segment.persistence;

import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.PersistenceMember;
import org.skywalking.apm.collector.worker.config.CacheSizeConfig;
import org.skywalking.apm.collector.worker.segment.SegmentIndex;
import org.skywalking.apm.collector.worker.segment.entity.SegmentAndBase64;
import org.skywalking.apm.collector.worker.storage.AbstractIndex;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;
import org.skywalking.apm.collector.worker.storage.SegmentData;
import org.skywalking.apm.collector.worker.storage.SegmentPersistenceData;

/**
 * @author pengys5
 */
public class SegmentSave extends PersistenceMember<SegmentPersistenceData, SegmentData> {

    @Override
    public String esIndex() {
        return SegmentIndex.INDEX;
    }

    @Override
    public String esType() {
        return AbstractIndex.TYPE_RECORD;
    }

    public SegmentSave(org.skywalking.apm.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public SegmentPersistenceData initializeData() {
        return new SegmentPersistenceData();
    }

    @Override final public void analyse(Object message) {
        if (message instanceof SegmentAndBase64) {
            SegmentAndBase64 segmentAndBase64 = (SegmentAndBase64)message;
            SegmentPersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(segmentAndBase64.getObject().getTraceSegmentId()).setSegmentStr(segmentAndBase64.getSegmentJsonStr());
            if (data.size() >= CacheSizeConfig.Cache.Persistence.SIZE) {
                persistence(data.asMap());
            }
            data.release();
        } else {
            logger().error("unhandled message, message instance must Segment, but is %s", message.getClass().toString());
        }
    }

    private void persistence(Map<String, SegmentData> dataMap) {
        List<IndexRequestBuilder> builderList = new LinkedList<>();
        Client client = EsClient.INSTANCE.getClient();
        dataMap.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.getSegmentStr());
            builderList.add(builder);
        });
        EsClient.INSTANCE.bulk(builderList);
        dataMap.clear();
    }

    @Override final protected void prepareIndex(List<IndexRequestBuilder> builderList) {
        Map<String, SegmentData> lastData = getPersistenceData().getLast().asMap();

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.getSegmentStr());
            builderList.add(builder);
        });
        lastData.clear();
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentSave> {
        @Override
        public Role role() {
            return Role.INSTANCE;
        }

        @Override
        public SegmentSave workerInstance(ClusterWorkerContext clusterContext) {
            SegmentSave worker = new SegmentSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}

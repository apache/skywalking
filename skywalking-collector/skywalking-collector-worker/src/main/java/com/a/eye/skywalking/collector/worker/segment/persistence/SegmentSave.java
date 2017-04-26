package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.PersistenceMember;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.storage.AbstractIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.PersistenceWorkerListener;
import com.a.eye.skywalking.collector.worker.storage.SegmentData;
import com.a.eye.skywalking.collector.worker.storage.SegmentPersistenceData;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;

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

    public SegmentSave(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
                       LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public SegmentPersistenceData initializeData() {
        return new SegmentPersistenceData();
    }

    @Override
    final public void analyse(Object message) throws Exception {
        if (message instanceof Segment) {
            Segment segment = (Segment) message;
            SegmentPersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(segment.getTraceSegmentId()).setSegmentStr(segment.getJsonStr());
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

    @Override
    final protected void prepareIndex(List<IndexRequestBuilder> builderList) {
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

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
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

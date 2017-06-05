package org.skywalking.apm.collector.worker.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.IndexNotFoundException;
import org.skywalking.apm.collector.worker.config.EsConfig;

import java.io.IOException;

/**
 * @author pengys5
 */
public abstract class AbstractIndex {
    private static final Logger logger = LogManager.getFormatterLogger(AbstractIndex.class);

    public static final String TYPE_MINUTE = "minute";
    public static final String TYPE_HOUR = "hour";
    public static final String TYPE_DAY = "day";
    public static final String TYPE_RECORD = "record";
    public static final String AGG_COLUMN = "aggId";
    public static final String TIME_SLICE = "timeSlice";

    final XContentBuilder createSettingBuilder() throws IOException {
        return XContentFactory.jsonBuilder()
            .startObject()
            .field("index.number_of_shards", EsConfig.Es.Index.Shards.NUMBER)
            .field("index.number_of_replicas", EsConfig.Es.Index.Replicas.NUMBER)
            .field("index.refresh_interval", String.valueOf(refreshInterval()) + "s")
            .endObject();
    }

    public abstract int refreshInterval();

    public abstract boolean isRecord();

    public abstract XContentBuilder createMappingBuilder() throws IOException;

    final void createIndex() {
        // settings
        String settingSource = "";

        // mapping
        XContentBuilder mappingBuilder = null;
        try {
            XContentBuilder settingsBuilder = createSettingBuilder();

            settingSource = settingsBuilder.string();

            mappingBuilder = createMappingBuilder();
            logger.info("mapping builder str: %s", mappingBuilder.string());
        } catch (Exception e) {
            logger.error("create %s index mapping builder error", index());
        }
        Settings settings = Settings.builder().loadFromSource(settingSource).build();
        IndicesAdminClient client = EsClient.INSTANCE.getClient().admin().indices();

        if (isRecord()) {
            CreateIndexResponse response = client.prepareCreate(index()).setSettings(settings).addMapping(TYPE_RECORD, mappingBuilder).get();
            logger.info("create %s index with type of %s finished, isAcknowledged: %s", index(), TYPE_RECORD, response.isAcknowledged());
        } else {
            CreateIndexResponse response = client.prepareCreate(index()).setSettings(settings).addMapping(TYPE_MINUTE, mappingBuilder).get();
            logger.info("create %s index with type of %s finished, isAcknowledged: %s", index(), TYPE_MINUTE, response.isAcknowledged());
            PutMappingResponse putMappingResponse = client.preparePutMapping(index()).setType(TYPE_HOUR).setSource(mappingBuilder).get();
            logger.info("create %s index with type of %s finished, isAcknowledged: %s", index(), TYPE_HOUR, putMappingResponse.isAcknowledged());
            putMappingResponse = client.preparePutMapping(index()).setType(TYPE_DAY).setSource(mappingBuilder).get();
            logger.info("create %s index with type of %s finished, isAcknowledged: %s", index(), TYPE_DAY, putMappingResponse.isAcknowledged());
        }
    }

    final boolean deleteIndex() {
        IndicesAdminClient client = EsClient.INSTANCE.getClient().admin().indices();
        try {
            DeleteIndexResponse response = client.prepareDelete(index()).get();
            logger.info("delete %s index finished, isAcknowledged: %s", index(), response.isAcknowledged());
            return response.isAcknowledged();
        } catch (IndexNotFoundException e) {
            logger.info("%s index not found", index());
        }
        return false;
    }

    final boolean isExists() {
        IndicesAdminClient client = EsClient.INSTANCE.getClient().admin().indices();
        IndicesExistsResponse response = client.prepareExists(index()).get();
        return response.isExists();
    }

    public abstract String index();
}

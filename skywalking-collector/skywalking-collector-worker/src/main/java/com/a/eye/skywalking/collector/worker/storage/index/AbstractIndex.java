package com.a.eye.skywalking.collector.worker.storage.index;

import com.a.eye.skywalking.collector.worker.storage.EsClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.IndexNotFoundException;

import java.io.IOException;

/**
 * @author pengys5
 */
public abstract class AbstractIndex {

    private Logger logger = LogManager.getFormatterLogger(AbstractIndex.class);

    public static final String Type_Minute = "minute";
    public static final String Type_Hour = "hour";
    public static final String Type_Day = "day";

    final public XContentBuilder createSettingBuilder() throws IOException {
        XContentBuilder settingsBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .field("index.number_of_shards", 2)
                .field("index.number_of_replicas", 0)
                .endObject();
        return settingsBuilder;
    }

    public abstract XContentBuilder createMappingBuilder() throws IOException;

    final public void createIndex() {
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
        IndicesAdminClient client = EsClient.getClient().admin().indices();
        CreateIndexResponse response = client.prepareCreate(index()).setSettings(settings).addMapping(Type_Minute, mappingBuilder).get();

        client.preparePutMapping(index()).setType(Type_Hour).setSource(mappingBuilder).get();
        client.preparePutMapping(index()).setType(Type_Day).setSource(mappingBuilder).get();
        logger.info("create %s index with type of %s finished, isAcknowledged: %s", index(), "aaa", response.isAcknowledged());
    }

    final public boolean deleteIndex() {
        IndicesAdminClient client = EsClient.getClient().admin().indices();
        try {
            DeleteIndexResponse response = client.prepareDelete(index()).get();
            logger.info("delete %s index finished, isAcknowledged: %s", index(), response.isAcknowledged());
            return response.isAcknowledged();
        } catch (IndexNotFoundException e) {
            logger.info("%s index not found", index());
        }
        return false;
    }

    public abstract String index();
}

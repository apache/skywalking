package org.skywalking.apm.collector.worker.storage;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {AbstractIndexTestCase.IndexTest.class, EsClient.class})
@PowerMockIgnore( {"javax.management.*"})
public class AbstractIndexTestCase {

    @Test
    public void testCreateSettingBuilder() throws IOException {
        IndexTest indexTest = new IndexTest();
        Assert.assertEquals("{\"index.number_of_shards\":\"\",\"index.number_of_replicas\":\"\",\"index.refresh_interval\":\"0s\"}", indexTest.createSettingBuilder().string());
    }

    class IndexTest extends AbstractIndex {
        @Override
        public boolean isRecord() {
            return false;
        }

        @Override
        public XContentBuilder createMappingBuilder() throws IOException {
            XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject(TIME_SLICE)
                .field("type", "long")
                .field("index", "not_analyzed")
                .endObject()
                .endObject()
                .endObject();
            return mappingBuilder;
        }

        @Override
        public String index() {
            return "Index_Test";
        }

        @Override
        public int refreshInterval() {
            return 0;
        }
    }
}

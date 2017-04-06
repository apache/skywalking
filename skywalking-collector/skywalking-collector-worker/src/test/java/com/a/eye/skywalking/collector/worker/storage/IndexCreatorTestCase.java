package com.a.eye.skywalking.collector.worker.storage;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({IndexCreator.class, IndexCreatorTestCase.TestIndex.class})
@PowerMockIgnore({"javax.management.*"})
public class IndexCreatorTestCase {

    @Test
    public void testLoadIndex() throws Exception {
        IndexCreator indexCreator = spy(IndexCreator.INSTANCE);
        Set<AbstractIndex> indexSet = Whitebox.invokeMethod(indexCreator, "loadIndex");
        Assert.assertEquals(8, indexSet.size());

        Set<String> indexName = new HashSet<>();
        for (AbstractIndex index : indexSet) {
            indexName.add(index.index());
        }

        Assert.assertEquals(true, indexName.contains("node_ref_idx"));
        Assert.assertEquals(true, indexName.contains("node_ref_res_sum_idx"));
        Assert.assertEquals(true, indexName.contains("global_trace_idx"));
        Assert.assertEquals(true, indexName.contains("segment_cost_idx"));
        Assert.assertEquals(true, indexName.contains("node_mapping_idx"));
        Assert.assertEquals(true, indexName.contains("segment_idx"));
        Assert.assertEquals(true, indexName.contains("segment_exp_idx"));
        Assert.assertEquals(true, indexName.contains("node_comp_idx"));
    }

    @Test
    public void testCreate() throws Exception {
        TestIndex testIndex = mock(TestIndex.class);

        IndexCreator indexCreator = mock(IndexCreator.class);
        doCallRealMethod().when(indexCreator).create();

        Set<AbstractIndex> indexSet = new HashSet<>();
        indexSet.add(testIndex);

        when(indexCreator, "loadIndex").thenReturn(indexSet);

        indexCreator.create();
        Mockito.verify(testIndex).createIndex();
        Mockito.verify(testIndex).deleteIndex();
    }

    class TestIndex extends AbstractIndex {

        @Override
        public String index() {
            return "TestIndex";
        }

        @Override
        public boolean isRecord() {
            return false;
        }

        @Override
        public XContentBuilder createMappingBuilder() throws IOException {
            return XContentFactory.jsonBuilder();
        }
    }
}

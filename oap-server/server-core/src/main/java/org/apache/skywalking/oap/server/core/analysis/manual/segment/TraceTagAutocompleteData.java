package org.apache.skywalking.oap.server.core.analysis.manual.segment;

import lombok.EqualsAndHashCode;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = TraceTagAutocompleteData.INDEX_NAME, scopeId = DefaultScopeDefine.TRACE_TAG_AUTOCOMPLETE,
    builder = TraceTagAutocompleteData.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false, timeRelativeID = true)
@EqualsAndHashCode(callSuper = true)
public class TraceTagAutocompleteData extends TagAutocompleteData {
    public static final String INDEX_NAME = "trace_tag_autocomplete";

    public static class Builder implements StorageBuilder<TraceTagAutocompleteData> {
        @Override
        public TraceTagAutocompleteData storage2Entity(final Convert2Entity converter) {
            TraceTagAutocompleteData record = new TraceTagAutocompleteData();
            record.setTagKey((String) converter.get(TAG_KEY));
            record.setTagValue((String) converter.get(TAG_VALUE));

            return record;
        }

        @Override
        public void entity2Storage(final TraceTagAutocompleteData storageData, final Convert2Storage converter) {
            converter.accept(TAG_KEY, storageData.getTagKey());
            converter.accept(TAG_VALUE, storageData.getTagValue());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}

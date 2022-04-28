package org.apache.skywalking.oap.server.core.analysis.manual.segment;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.TraceTagAutocomplete;

public class TraceTagAutocompleteDispatcher implements SourceDispatcher<TraceTagAutocomplete> {

    @Override
    public void dispatch(TraceTagAutocomplete source) {
        TraceTagAutocompleteData autocomplete = new TraceTagAutocompleteData();
        autocomplete.setTag(source.getTag());
        autocomplete.setTagKey(source.getTagKey());
        autocomplete.setTagValue(source.getTagValue());
        autocomplete.setTimeBucket(source.getTimeBucket());
        MetricsStreamProcessor.getInstance().in(autocomplete);
    }
}

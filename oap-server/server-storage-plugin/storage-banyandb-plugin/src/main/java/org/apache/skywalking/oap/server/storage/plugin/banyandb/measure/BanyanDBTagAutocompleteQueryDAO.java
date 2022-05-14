package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BanyanDBTagAutocompleteQueryDAO extends AbstractBanyanDBDAO implements ITagAutoCompleteQueryDAO {
    public BanyanDBTagAutocompleteQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Set<String> queryTagAutocompleteKeys(TagType tagType, long startSecondTB, long endSecondTB) throws IOException {
        TimestampRange range = null;
        if (startSecondTB > 0 && endSecondTB > 0) {
            range = new TimestampRange(TimeBucket.getTimestamp(startSecondTB), TimeBucket.getTimestamp(endSecondTB));
        }
        MeasureQueryResponse resp = query(TagAutocompleteData.INDEX_NAME,
                ImmutableSet.of(TagAutocompleteData.TAG_TYPE,
                        TagAutocompleteData.TAG_KEY), Collections.emptySet(),
                range,
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(TagAutocompleteData.TAG_TYPE, tagType.name()));
                    }
                }
        );

        if (resp.size() == 0) {
            return Collections.emptySet();
        }

        Set<String> keys = new HashSet<>();
        for (final DataPoint dp : resp.getDataPoints()) {
            keys.add(dp.getTagValue(TagAutocompleteData.TAG_KEY));
        }
        return keys;
    }

    @Override
    public Set<String> queryTagAutocompleteValues(TagType tagType, String tagKey, int limit, long startSecondTB, long endSecondTB) throws IOException {
        TimestampRange range = null;
        if (startSecondTB > 0 && endSecondTB > 0) {
            range = new TimestampRange(TimeBucket.getTimestamp(startSecondTB), TimeBucket.getTimestamp(endSecondTB));
        }
        MeasureQueryResponse resp = query(TagAutocompleteData.INDEX_NAME,
                ImmutableSet.of(TagAutocompleteData.TAG_TYPE,
                        TagAutocompleteData.TAG_KEY,
                        TagAutocompleteData.TAG_VALUE), Collections.emptySet(),
                range,
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(TagAutocompleteData.TAG_TYPE, tagType.name()));
                        query.and(eq(TagAutocompleteData.TAG_KEY, tagKey));
                    }
                }
        );

        if (resp.size() == 0) {
            return Collections.emptySet();
        }

        Set<String> values = new HashSet<>();
        for (final DataPoint dp : resp.getDataPoints()) {
            values.add(dp.getTagValue(TagAutocompleteData.TAG_VALUE));
        }
        return values;
    }
}

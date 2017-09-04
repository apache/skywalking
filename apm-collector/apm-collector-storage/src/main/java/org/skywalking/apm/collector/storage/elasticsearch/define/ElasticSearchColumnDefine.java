package org.skywalking.apm.collector.storage.elasticsearch.define;

import org.skywalking.apm.collector.core.storage.ColumnDefine;

/**
 * @author pengys5
 */
public class ElasticSearchColumnDefine extends ColumnDefine {
    public ElasticSearchColumnDefine(String name, String type) {
        super(name, type);
    }

    public enum Type {
        Binary, Boolean, Keyword, Long, Integer, Double, Text
    }
}

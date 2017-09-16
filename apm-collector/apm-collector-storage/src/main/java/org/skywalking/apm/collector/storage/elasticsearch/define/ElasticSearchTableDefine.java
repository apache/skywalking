package org.skywalking.apm.collector.storage.elasticsearch.define;

import org.skywalking.apm.collector.core.storage.TableDefine;

/**
 * @author pengys5
 */
public abstract class ElasticSearchTableDefine extends TableDefine {

    public ElasticSearchTableDefine(String name) {
        super(name);
    }

    public final String type() {
        return "type";
    }

    public abstract int refreshInterval();
}

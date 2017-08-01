package org.skywalking.apm.collector.storage.h2.define;

import org.skywalking.apm.collector.core.storage.ColumnDefine;

/**
 * @author pengys5
 */
public class H2ColumnDefine extends ColumnDefine {

    public H2ColumnDefine(String name, String type) {
        super(name, type);
    }

    public enum Type {
        Boolean, Varchar, Int, Bigint, BINARY
    }
}

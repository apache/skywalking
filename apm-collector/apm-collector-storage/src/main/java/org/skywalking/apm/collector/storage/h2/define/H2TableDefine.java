package org.skywalking.apm.collector.storage.h2.define;

import org.skywalking.apm.collector.core.storage.TableDefine;

/**
 * @author pengys5
 */
public abstract class H2TableDefine extends TableDefine {

    public H2TableDefine(String name) {
        super(name);
    }
}

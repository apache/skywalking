package org.skywalking.apm.collector.agentstream.worker.node.define;

import org.skywalking.apm.collector.stream.impl.data.Attribute;
import org.skywalking.apm.collector.stream.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class NodeComponentDataDefine extends DataDefine {

    @Override protected int defineId() {
        return 0;
    }

    @Override protected int initialCapacity() {
        return 4;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute("id", AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute("name", AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute("peers", AttributeType.STRING, new CoverOperation()));
        addAttribute(3, new Attribute("aggregation", AttributeType.STRING, new CoverOperation()));
    }
}

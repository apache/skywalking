package org.skywalking.apm.collector.agentstream.worker.node.component;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.skywalking.apm.collector.agentstream.worker.node.define.proto.NodeComponent;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

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

    @Override public Data parseFrom(ByteString bytesData) throws InvalidProtocolBufferException {
        NodeComponent.Message message = NodeComponent.Message.parseFrom(bytesData);
        Data data = build();
        data.setDataString(0, message.getId());
        data.setDataString(1, message.getName());
        data.setDataString(2, message.getPeers());
        data.setDataString(3, message.getAggregation());
        return data;
    }
}

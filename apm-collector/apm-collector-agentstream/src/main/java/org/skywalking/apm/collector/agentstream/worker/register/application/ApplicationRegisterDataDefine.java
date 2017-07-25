package org.skywalking.apm.collector.agentstream.worker.register.application;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.skywalking.apm.collector.agentstream.worker.register.application.proto.ApplicationRegisterOuterClass;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class ApplicationRegisterDataDefine extends DataDefine {

    @Override protected int defineId() {
        return 101;
    }

    @Override protected int initialCapacity() {
        return 3;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute("id", AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ApplicationRegisterTable.COLUMN_APPLICATION_CODE, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(ApplicationRegisterTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
    }

    @Override public Data parseFrom(ByteString bytesData) throws InvalidProtocolBufferException {
        ApplicationRegisterOuterClass.ApplicationRegister applicationRegister = ApplicationRegisterOuterClass.ApplicationRegister.parseFrom(bytesData);
        Data data = build();
        data.setDataString(1, applicationRegister.getApplicationCode());
        return data;
    }
}

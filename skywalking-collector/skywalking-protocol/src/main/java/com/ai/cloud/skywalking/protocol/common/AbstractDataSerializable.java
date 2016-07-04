package com.ai.cloud.skywalking.protocol.common;

import com.ai.cloud.skywalking.protocol.NullClass;
import com.ai.cloud.skywalking.protocol.SerializableDataTypeRegister;
import com.ai.cloud.skywalking.util.IntegerAssist;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by wusheng on 16/7/4.
 */
public abstract class AbstractDataSerializable implements ISerializable, NullableClass {
    private static Set<Integer> DATA_TYPE_SCOPE = new HashSet<Integer>();

    public AbstractDataSerializable(){
        SerializableDataTypeRegister.init(getDataType(), this.getClass());
    }

    public abstract int getDataType();

    public abstract byte[] getData();

    @Override
    public byte[] convert2Bytes() {
        byte[] type = IntegerAssist.intToBytes(SerializableDataTypeRegister.getType(this.getClass()));

        //TODO:类型+ data = 消息包
        return getData();
    }

    @Override
    public Object convert2Object(byte[] data) {
        // TODO:data的前4位转成type;
        int dataType =  1;
        if(!SerializableDataTypeRegister.isTypeAndClassMatch(dataType, this.getClass())){
            return new NullClass();
        }
        // TODO: 反序列化
        return null;
    }


}

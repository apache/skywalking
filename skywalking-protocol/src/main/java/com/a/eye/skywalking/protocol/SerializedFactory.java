package com.a.eye.skywalking.protocol;

import com.a.eye.skywalking.protocol.common.NullableClass;
import com.a.eye.skywalking.protocol.util.IntegerAssist;
import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class SerializedFactory {
    public static Map<Integer, AbstractDataSerializable> serializableMap =
            new HashMap<Integer, AbstractDataSerializable>();


    static {
        ServiceLoader<AbstractDataSerializable> loaders = ServiceLoader.load(AbstractDataSerializable.class);

        for (AbstractDataSerializable serializable : loaders) {
            serializableMap.put(serializable.getDataType(), serializable);
        }
    }

    public static AbstractDataSerializable deserialize(byte[] bytes) throws ConvertFailedException {
        try {
            AbstractDataSerializable abstractDataSerializable = serializableMap.get(IntegerAssist.bytesToInt(bytes, 0));
            if (abstractDataSerializable != null) {
                NullableClass nullableClass = abstractDataSerializable.convert2Object(bytes);
                if (!nullableClass.isNull()) {
                    return (AbstractDataSerializable) nullableClass;
                }
            }
        } catch (Exception e) {
            throw new ConvertFailedException(e.getMessage(), e);
        }
        return null;
    }

    public static byte[] serialize(AbstractDataSerializable dataSerializable) {
        return dataSerializable.convert2Bytes();
    }

    public static boolean isCanSerialized(int dataType) {
        return serializableMap.get(dataType) != null ? true : false;
    }
}

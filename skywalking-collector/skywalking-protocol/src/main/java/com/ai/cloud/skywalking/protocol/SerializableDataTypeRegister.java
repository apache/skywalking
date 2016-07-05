package com.ai.cloud.skywalking.protocol;

import com.ai.cloud.skywalking.protocol.common.AbstractDataSerializable;
import com.ai.cloud.skywalking.protocol.common.ISerializable;
import com.ai.cloud.skywalking.protocol.common.NullableClass;
import com.ai.cloud.skywalking.protocol.exception.SerializableDataTypeRegisterException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by wusheng on 16/7/4.
 */
public class SerializableDataTypeRegister {
    private static Map<Integer, Class<?>> DATA_TYPE_MAPPING_CLASS = new HashMap<Integer, Class<?>>();

    private static Map<Class<?>, Integer> CLASS_MAPPING_DATA_TYPE = new HashMap<Class<?>, Integer>();

    public static void init(Integer dataType, Class<?> clazz) {
        if (DATA_TYPE_MAPPING_CLASS.containsKey(dataType)) {
            if (!DATA_TYPE_MAPPING_CLASS.get(dataType).equals(clazz)) {
                throw new SerializableDataTypeRegisterException("dataType=" + dataType + " has been registered to " + DATA_TYPE_MAPPING_CLASS.get(dataType));
            } else {
                return;
            }
        }
        DATA_TYPE_MAPPING_CLASS.put(dataType, clazz);
        CLASS_MAPPING_DATA_TYPE.put(clazz, dataType);
    }

    public static boolean isTypeAndClassMatch(Integer dataType, Class<?> clazz) {
        if (DATA_TYPE_MAPPING_CLASS.containsKey(dataType)) {
            return true;
        } else {
            return false;
        }
    }

    public static ISerializable findSerializableClassAndSerialize(byte[] data) {
        Set<Class<?>> registerSerializableClasses = CLASS_MAPPING_DATA_TYPE.keySet();
        for (Class<?> serializableClass : registerSerializableClasses) {
            try {
                ISerializable result = ((ISerializable) serializableClass.newInstance());
                //TODO
               NullableClass nullableClass = result.convert2Object(data);
               if (!nullableClass.isNull()){

               }
            } catch (Exception e) {

            }

        }
        return null;
    }

    public static Integer getType(Class<?> clazz) {
        if (CLASS_MAPPING_DATA_TYPE.containsKey(clazz)) {
            return CLASS_MAPPING_DATA_TYPE.get(clazz);
        } else {
            throw new SerializableDataTypeRegisterException("class " + clazz + " not found in SerializableDataTypeRegister.");
        }
    }
}

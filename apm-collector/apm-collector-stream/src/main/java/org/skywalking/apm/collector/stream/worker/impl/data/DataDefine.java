package org.skywalking.apm.collector.stream.worker.impl.data;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;

/**
 * @author pengys5
 */
public abstract class DataDefine {
    private Attribute[] attributes;
    private int stringCapacity;
    private int longCapacity;
    private int floatCapacity;
    private int integerCapacity;
    private int booleanCapacity;
    private int byteCapacity;

    public DataDefine() {
        initial();
    }

    private void initial() {
        attributes = new Attribute[initialCapacity()];
        attributeDefine();
        for (Attribute attribute : attributes) {
            if (AttributeType.STRING.equals(attribute.getType())) {
                stringCapacity++;
            } else if (AttributeType.LONG.equals(attribute.getType())) {
                longCapacity++;
            } else if (AttributeType.FLOAT.equals(attribute.getType())) {
                floatCapacity++;
            } else if (AttributeType.INTEGER.equals(attribute.getType())) {
                integerCapacity++;
            } else if (AttributeType.BOOLEAN.equals(attribute.getType())) {
                booleanCapacity++;
            } else if (AttributeType.BYTE.equals(attribute.getType())) {
                byteCapacity++;
            }
        }
    }

    public final void addAttribute(int position, Attribute attribute) {
        attributes[position] = attribute;
    }

    public abstract int defineId();

    protected abstract int initialCapacity();

    protected abstract void attributeDefine();

    public final Data build(String id) {
        return new Data(id, defineId(), stringCapacity, longCapacity, floatCapacity, integerCapacity, booleanCapacity, byteCapacity);
    }

    public void mergeData(Data newData, Data oldData) {
        int stringPosition = 0;
        int longPosition = 0;
        int floatPosition = 0;
        int integerPosition = 0;
        int booleanPosition = 0;
        int bytePosition = 0;
        for (int i = 0; i < initialCapacity(); i++) {
            Attribute attribute = attributes[i];
            if (AttributeType.STRING.equals(attribute.getType())) {
                attribute.getOperation().operate(newData.getDataString(stringPosition), oldData.getDataString(stringPosition));
                stringPosition++;
            } else if (AttributeType.LONG.equals(attribute.getType())) {
                attribute.getOperation().operate(newData.getDataLong(longPosition), oldData.getDataLong(longPosition));
                longPosition++;
            } else if (AttributeType.FLOAT.equals(attribute.getType())) {
                attribute.getOperation().operate(newData.getDataFloat(floatPosition), oldData.getDataFloat(floatPosition));
                floatPosition++;
            } else if (AttributeType.INTEGER.equals(attribute.getType())) {
                attribute.getOperation().operate(newData.getDataInteger(integerPosition), oldData.getDataInteger(integerPosition));
                integerPosition++;
            } else if (AttributeType.BOOLEAN.equals(attribute.getType())) {
                attribute.getOperation().operate(newData.getDataBoolean(booleanPosition), oldData.getDataBoolean(booleanPosition));
                integerPosition++;
            } else if (AttributeType.BYTE.equals(attribute.getType())) {
                attribute.getOperation().operate(newData.getDataBytes(bytePosition), oldData.getDataBytes(integerPosition));
                bytePosition++;
            }
        }
    }

    public abstract Object deserialize(RemoteData remoteData);

    public abstract RemoteData serialize(Object object);
}

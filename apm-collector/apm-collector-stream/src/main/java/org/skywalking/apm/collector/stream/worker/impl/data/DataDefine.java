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

    public DataDefine() {
        stringCapacity = 0;
        longCapacity = 0;
        floatCapacity = 0;
        integerCapacity = 0;
    }

    public final void initial() {
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
            }
        }
    }

    public final void addAttribute(int position, Attribute attribute) {
        attributes[position] = attribute;
    }

    public abstract int defineId();

    protected abstract int initialCapacity();

    protected abstract void attributeDefine();

    public final Data build() {
        return new Data(defineId(), stringCapacity, longCapacity, floatCapacity, integerCapacity);
    }

    public void mergeData(Data newData, Data oldData) {
        int stringPosition = 0;
        int longPosition = 0;
        int floatPosition = 0;
        int integerPosition = 0;
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
            } else if (AttributeType.FLOAT.equals(attribute.getType())) {
                attribute.getOperation().operate(newData.getDataInteger(integerPosition), oldData.getDataInteger(integerPosition));
                integerPosition++;
            }
        }
    }

    public abstract Object deserialize(RemoteData remoteData);

    public abstract RemoteData serialize(Object object);
}

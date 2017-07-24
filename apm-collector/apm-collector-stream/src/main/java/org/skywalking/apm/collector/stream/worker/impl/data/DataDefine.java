package org.skywalking.apm.collector.stream.worker.impl.data;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * @author pengys5
 */
public abstract class DataDefine {
    private Attribute[] attributes;
    private int stringCapacity;
    private int longCapacity;
    private int floatCapacity;

    public DataDefine() {
        stringCapacity = 0;
        longCapacity = 0;
        floatCapacity = 0;
    }

    public final void initial() {
        for (Attribute attribute : attributes) {
            if (AttributeType.STRING.equals(attribute.getType())) {
                stringCapacity++;
            } else if (AttributeType.LONG.equals(attribute.getType())) {
                longCapacity++;
            } else if (AttributeType.FLOAT.equals(attribute.getType())) {
                floatCapacity++;
            }
        }
    }

    public final void addAttribute(int position, Attribute attribute) {
        attributes[position] = attribute;
    }

    public final void define() {
        attributes = new Attribute[initialCapacity()];
    }

    protected abstract int defineId();

    protected abstract int initialCapacity();

    protected abstract void attributeDefine();

    public int getStringCapacity() {
        return stringCapacity;
    }

    public int getLongCapacity() {
        return longCapacity;
    }

    public int getFloatCapacity() {
        return floatCapacity;
    }

    public Data build() {
        return new Data(defineId(), getStringCapacity(), getLongCapacity(), getFloatCapacity());
    }

    public void mergeData(Data newData, Data oldData) {
        int stringPosition = 0;
        int longPosition = 0;
        int floatPosition = 0;
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
            }
        }
    }

    public abstract Data parseFrom(ByteString bytesData) throws InvalidProtocolBufferException;
}

/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.define;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;

/**
 * @author peng-yongsheng
 */
public abstract class DataDefine {
    private Attribute[] attributes;
    private int stringCapacity;
    private int longCapacity;
    private int doubleCapacity;
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
            } else if (AttributeType.DOUBLE.equals(attribute.getType())) {
                doubleCapacity++;
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

    protected abstract int initialCapacity();

    protected abstract void attributeDefine();

    public final Data build(String id) {
        return new Data(id, stringCapacity, longCapacity, doubleCapacity, integerCapacity, booleanCapacity, byteCapacity);
    }

    public void mergeData(Data newData, Data oldData) {
        int stringPosition = 0;
        int longPosition = 0;
        int doublePosition = 0;
        int integerPosition = 0;
        int booleanPosition = 0;
        int bytePosition = 0;
        for (int i = 0; i < initialCapacity(); i++) {
            Attribute attribute = attributes[i];
            if (AttributeType.STRING.equals(attribute.getType())) {
                String stringData = attribute.getOperation().operate(newData.getDataString(stringPosition), oldData.getDataString(stringPosition));
                newData.setDataString(stringPosition, stringData);
                stringPosition++;
            } else if (AttributeType.LONG.equals(attribute.getType())) {
                Long longData = attribute.getOperation().operate(newData.getDataLong(longPosition), oldData.getDataLong(longPosition));
                newData.setDataLong(longPosition, longData);
                longPosition++;
            } else if (AttributeType.DOUBLE.equals(attribute.getType())) {
                Double doubleData = attribute.getOperation().operate(newData.getDataDouble(doublePosition), oldData.getDataDouble(doublePosition));
                newData.setDataDouble(doublePosition, doubleData);
                doublePosition++;
            } else if (AttributeType.INTEGER.equals(attribute.getType())) {
                Integer integerData = attribute.getOperation().operate(newData.getDataInteger(integerPosition), oldData.getDataInteger(integerPosition));
                newData.setDataInteger(integerPosition, integerData);
                integerPosition++;
            } else if (AttributeType.BOOLEAN.equals(attribute.getType())) {
                Boolean booleanData = attribute.getOperation().operate(newData.getDataBoolean(booleanPosition), oldData.getDataBoolean(booleanPosition));
                newData.setDataBoolean(booleanPosition, booleanData);
                booleanPosition++;
            } else if (AttributeType.BYTE.equals(attribute.getType())) {
                byte[] byteData = attribute.getOperation().operate(newData.getDataBytes(bytePosition), oldData.getDataBytes(integerPosition));
                newData.setDataBytes(bytePosition, byteData);
                bytePosition++;
            }
        }
    }

    public abstract Object deserialize(RemoteData remoteData);

    public abstract RemoteData serialize(Object object);
}

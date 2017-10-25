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

package org.skywalking.apm.collector.core.stream;

/**
 * @author peng-yongsheng
 */
public class Data extends AbstractHashMessage {
    private final int stringCapacity;
    private final int longCapacity;
    private final int doubleCapacity;
    private final int integerCapacity;
    private final int booleanCapacity;
    private final int byteCapacity;
    private String[] dataStrings;
    private Long[] dataLongs;
    private Double[] dataDoubles;
    private Integer[] dataIntegers;
    private Boolean[] dataBooleans;
    private byte[][] dataBytes;

    public Data(String id, int stringCapacity, int longCapacity, int doubleCapacity, int integerCapacity,
        int booleanCapacity, int byteCapacity) {
        super(id);
        this.dataStrings = new String[stringCapacity];
        this.dataStrings[0] = id;
        this.dataLongs = new Long[longCapacity];
        this.dataDoubles = new Double[doubleCapacity];
        this.dataIntegers = new Integer[integerCapacity];
        this.dataBooleans = new Boolean[booleanCapacity];
        this.dataBytes = new byte[byteCapacity][];
        this.stringCapacity = stringCapacity;
        this.longCapacity = longCapacity;
        this.doubleCapacity = doubleCapacity;
        this.integerCapacity = integerCapacity;
        this.booleanCapacity = booleanCapacity;
        this.byteCapacity = byteCapacity;
    }

    public void setDataString(int position, String value) {
        dataStrings[position] = value;
    }

    public void setDataLong(int position, Long value) {
        dataLongs[position] = value;
    }

    public void setDataDouble(int position, Double value) {
        dataDoubles[position] = value;
    }

    public void setDataInteger(int position, Integer value) {
        dataIntegers[position] = value;
    }

    public void setDataBoolean(int position, Boolean value) {
        dataBooleans[position] = value;
    }

    public void setDataBytes(int position, byte[] dataBytes) {
        this.dataBytes[position] = dataBytes;
    }

    public String getDataString(int position) {
        return dataStrings[position];
    }

    public Long getDataLong(int position) {
        return dataLongs[position];
    }

    public Double getDataDouble(int position) {
        return dataDoubles[position];
    }

    public Integer getDataInteger(int position) {
        return dataIntegers[position];
    }

    public Boolean getDataBoolean(int position) {
        return dataBooleans[position];
    }

    public byte[] getDataBytes(int position) {
        return dataBytes[position];
    }

    public String id() {
        return dataStrings[0];
    }

    @Override public String toString() {
        StringBuilder dataStr = new StringBuilder();
        dataStr.append("string: [");
        for (int i = 0; i < dataStrings.length; i++) {
            dataStr.append(dataStrings[i]).append(",");
        }
        dataStr.append("], longs: [");
        for (int i = 0; i < dataLongs.length; i++) {
            dataStr.append(dataLongs[i]).append(",");
        }
        dataStr.append("], double: [");
        for (int i = 0; i < dataDoubles.length; i++) {
            dataStr.append(dataDoubles[i]).append(",");
        }
        dataStr.append("], integer: [");
        for (int i = 0; i < dataIntegers.length; i++) {
            dataStr.append(dataIntegers[i]).append(",");
        }
        dataStr.append("], boolean: [");
        for (int i = 0; i < dataBooleans.length; i++) {
            dataStr.append(dataBooleans[i]).append(",");
        }
        return dataStr.toString();
    }
}

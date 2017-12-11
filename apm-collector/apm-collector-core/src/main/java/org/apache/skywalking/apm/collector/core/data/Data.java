/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.collector.core.data;

/**
 * @author peng-yongsheng
 */
public abstract class Data extends EndOfBatchQueueMessage {
    private String[] dataStrings;
    private Long[] dataLongs;
    private Double[] dataDoubles;
    private Integer[] dataIntegers;
    private Boolean[] dataBooleans;
    private byte[][] dataBytes;
    private final Column[] stringColumns;
    private final Column[] longColumns;
    private final Column[] doubleColumns;
    private final Column[] integerColumns;
    private final Column[] booleanColumns;
    private final Column[] byteColumns;

    public Data(String id, Column[] stringColumns, Column[] longColumns, Column[] doubleColumns,
        Column[] integerColumns, Column[] booleanColumns, Column[] byteColumns) {
        super(id);
        this.dataStrings = new String[stringColumns.length];
        this.dataStrings[0] = id;
        this.dataLongs = new Long[longColumns.length];
        this.dataDoubles = new Double[doubleColumns.length];
        this.dataIntegers = new Integer[integerColumns.length];
        this.dataBooleans = new Boolean[booleanColumns.length];
        this.dataBytes = new byte[byteColumns.length][];
        this.stringColumns = stringColumns;
        this.longColumns = longColumns;
        this.doubleColumns = doubleColumns;
        this.integerColumns = integerColumns;
        this.booleanColumns = booleanColumns;
        this.byteColumns = byteColumns;
    }

    public int getDataStringsCount() {
        return dataStrings.length;
    }

    public int getDataLongsCount() {
        return dataLongs.length;
    }

    public int getDataDoublesCount() {
        return dataDoubles.length;
    }

    public int getDataIntegersCount() {
        return dataIntegers.length;
    }

    public int getDataBooleansCount() {
        return dataBooleans.length;
    }

    public int getDataBytesCount() {
        return dataBytes.length;
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
        if (position + 1 > dataLongs.length) {
            throw new IndexOutOfBoundsException();
        } else if (dataLongs[position] == null) {
            return 0L;
        } else {
            return dataLongs[position];
        }
    }

    public Double getDataDouble(int position) {
        if (position + 1 > dataDoubles.length) {
            throw new IndexOutOfBoundsException();
        } else if (dataDoubles[position] == null) {
            return 0D;
        } else {
            return dataDoubles[position];
        }
    }

    public Integer getDataInteger(int position) {
        if (position + 1 > dataIntegers.length) {
            throw new IndexOutOfBoundsException();
        } else if (dataIntegers[position] == null) {
            return 0;
        } else {
            return dataIntegers[position];
        }
    }

    public Boolean getDataBoolean(int position) {
        return dataBooleans[position];
    }

    public byte[] getDataBytes(int position) {
        return dataBytes[position];
    }

    public String getId() {
        return dataStrings[0];
    }

    public void setId(String id) {
        setKey(id);
        this.dataStrings[0] = id;
    }

    public void mergeData(Data newData) {
        for (int i = 0; i < stringColumns.length; i++) {
            String stringData = stringColumns[i].getOperation().operate(newData.getDataString(i), this.getDataString(i));
            this.dataStrings[i] = stringData;
        }
        for (int i = 0; i < longColumns.length; i++) {
            Long longData = longColumns[i].getOperation().operate(newData.getDataLong(i), this.getDataLong(i));
            this.dataLongs[i] = longData;
        }
        for (int i = 0; i < doubleColumns.length; i++) {
            Double doubleData = doubleColumns[i].getOperation().operate(newData.getDataDouble(i), this.getDataDouble(i));
            this.dataDoubles[i] = doubleData;
        }
        for (int i = 0; i < integerColumns.length; i++) {
            Integer integerData = integerColumns[i].getOperation().operate(newData.getDataInteger(i), this.getDataInteger(i));
            this.dataIntegers[i] = integerData;
        }
        for (int i = 0; i < booleanColumns.length; i++) {
            Boolean booleanData = booleanColumns[i].getOperation().operate(newData.getDataBoolean(i), this.getDataBoolean(i));
            this.dataBooleans[i] = booleanData;
        }
        for (int i = 0; i < byteColumns.length; i++) {
            byte[] byteData = byteColumns[i].getOperation().operate(newData.getDataBytes(i), this.getDataBytes(i));
            this.dataBytes[i] = byteData;
        }
    }

    @Override public String toString() {
        StringBuilder dataStr = new StringBuilder();
        dataStr.append("string: [");
        for (String dataString : dataStrings) {
            dataStr.append(dataString).append(",");
        }
        dataStr.append("], longs: [");
        for (Long dataLong : dataLongs) {
            dataStr.append(dataLong).append(",");
        }
        dataStr.append("], double: [");
        for (Double dataDouble : dataDoubles) {
            dataStr.append(dataDouble).append(",");
        }
        dataStr.append("], integer: [");
        for (Integer dataInteger : dataIntegers) {
            dataStr.append(dataInteger).append(",");
        }
        dataStr.append("], boolean: [");
        for (Boolean dataBoolean : dataBooleans) {
            dataStr.append(dataBoolean).append(",");
        }
        dataStr.append("]");
        return dataStr.toString();
    }
}

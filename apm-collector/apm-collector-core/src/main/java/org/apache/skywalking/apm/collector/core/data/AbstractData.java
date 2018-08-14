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

import org.apache.skywalking.apm.collector.core.data.column.*;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractData implements RemoteData {
    private final String[] dataStrings;
    private final Long[] dataLongs;
    private final Double[] dataDoubles;
    private final Integer[] dataIntegers;
    private final byte[][] dataBytes;

    private final StringLinkedList[] dataStringLists;
    private final LongLinkedList[] dataLongLists;
    private final DoubleLinkedList[] dataDoubleLists;
    private final IntegerLinkedList[] dataIntegerLists;

    private final StringColumn[] stringColumns;
    private final LongColumn[] longColumns;
    private final IntegerColumn[] integerColumns;
    private final DoubleColumn[] doubleColumns;
    private final ByteColumn[] byteColumns;

    private final StringListColumn[] stringListColumns;
    private final LongListColumn[] longListColumns;
    private final IntegerListColumn[] integerListColumns;
    private final DoubleListColumn[] doubleListColumns;

    AbstractData(StringColumn[] stringColumns, LongColumn[] longColumns,
        IntegerColumn[] integerColumns,
        DoubleColumn[] doubleColumns, ByteColumn[] byteColumns,
        StringListColumn[] stringListColumns,
        LongListColumn[] longListColumns,
        IntegerListColumn[] integerListColumns, DoubleListColumn[] doubleListColumns) {
        this.stringColumns = stringColumns;
        this.longColumns = longColumns;
        this.integerColumns = integerColumns;
        this.doubleColumns = doubleColumns;
        this.byteColumns = byteColumns;

        this.stringListColumns = stringListColumns;
        this.longListColumns = longListColumns;
        this.integerListColumns = integerListColumns;
        this.doubleListColumns = doubleListColumns;

        this.dataStrings = new String[stringColumns.length];
        this.dataLongs = new Long[longColumns.length];
        this.dataIntegers = new Integer[integerColumns.length];
        this.dataDoubles = new Double[doubleColumns.length];
        this.dataBytes = new byte[byteColumns.length][];

        this.dataStringLists = new StringLinkedList[stringListColumns.length];
        for (int i = 0; i < this.dataStringLists.length; i++) {
            this.dataStringLists[i] = new StringLinkedList();
        }

        this.dataLongLists = new LongLinkedList[longListColumns.length];
        for (int i = 0; i < this.dataLongLists.length; i++) {
            this.dataLongLists[i] = new LongLinkedList();
        }

        this.dataIntegerLists = new IntegerLinkedList[integerListColumns.length];
        for (int i = 0; i < this.dataIntegerLists.length; i++) {
            this.dataIntegerLists[i] = new IntegerLinkedList();
        }

        this.dataDoubleLists = new DoubleLinkedList[doubleListColumns.length];
        for (int i = 0; i < this.dataDoubleLists.length; i++) {
            this.dataDoubleLists[i] = new DoubleLinkedList();
        }
    }

    @Override public final int getDataStringsCount() {
        return dataStrings.length;
    }

    @Override public final int getDataLongsCount() {
        return dataLongs.length;
    }

    @Override public final int getDataDoublesCount() {
        return dataDoubles.length;
    }

    @Override public final int getDataIntegersCount() {
        return dataIntegers.length;
    }

    @Override public final int getDataBytesCount() {
        return dataBytes.length;
    }

    @Override public int getDataStringListsCount() {
        return dataStringLists.length;
    }

    @Override public int getDataLongListsCount() {
        return dataLongLists.length;
    }

    @Override public int getDataDoubleListsCount() {
        return dataDoubleLists.length;
    }

    @Override public int getDataIntegerListsCount() {
        return dataIntegerLists.length;
    }

    @Override public final void setDataString(int position, String value) {
        dataStrings[position] = value;
    }

    @Override public final void setDataLong(int position, Long value) {
        dataLongs[position] = value;
    }

    @Override public final void setDataDouble(int position, Double value) {
        dataDoubles[position] = value;
    }

    @Override public final void setDataInteger(int position, Integer value) {
        dataIntegers[position] = value;
    }

    @Override public final void setDataBytes(int position, byte[] dataBytes) {
        this.dataBytes[position] = dataBytes;
    }

    @Override public final String getDataString(int position) {
        return dataStrings[position];
    }

    @Override public final Long getDataLong(int position) {
        if (position + 1 > dataLongs.length) {
            throw new IndexOutOfBoundsException();
        } else if (dataLongs[position] == null) {
            return 0L;
        } else {
            return dataLongs[position];
        }
    }

    @Override public final Double getDataDouble(int position) {
        if (position + 1 > dataDoubles.length) {
            throw new IndexOutOfBoundsException();
        } else if (dataDoubles[position] == null) {
            return 0D;
        } else {
            return dataDoubles[position];
        }
    }

    @Override public final Integer getDataInteger(int position) {
        if (position + 1 > dataIntegers.length) {
            throw new IndexOutOfBoundsException();
        } else if (dataIntegers[position] == null) {
            return 0;
        } else {
            return dataIntegers[position];
        }
    }

    @Override public final byte[] getDataBytes(int position) {
        return dataBytes[position];
    }

    @Override public StringLinkedList getDataStringList(int position) {
        if (position + 1 > dataStringLists.length) {
            throw new IndexOutOfBoundsException();
        } else {
            return dataStringLists[position];
        }
    }

    @Override public LongLinkedList getDataLongList(int position) {
        if (position + 1 > dataLongLists.length) {
            throw new IndexOutOfBoundsException();
        } else {
            return dataLongLists[position];
        }
    }

    @Override public DoubleLinkedList getDataDoubleList(int position) {
        if (position + 1 > dataDoubleLists.length) {
            throw new IndexOutOfBoundsException();
        } else {
            return dataDoubleLists[position];
        }
    }

    @Override public IntegerLinkedList getDataIntegerList(int position) {
        if (position + 1 > dataIntegerLists.length) {
            throw new IndexOutOfBoundsException();
        } else {
            return dataIntegerLists[position];
        }
    }

    public final void mergeAndFormulaCalculateData(AbstractData newData) {
        mergeData(newData);
        calculateFormula();
    }

    private void mergeData(AbstractData newData) {
        for (int i = 0; i < stringColumns.length; i++) {
            String stringData = stringColumns[i].getMergeOperation().operate(newData.getDataString(i), this.getDataString(i));
            this.dataStrings[i] = stringData;
        }
        for (int i = 0; i < longColumns.length; i++) {
            Long longData = longColumns[i].getMergeOperation().operate(newData.getDataLong(i), this.getDataLong(i));
            this.dataLongs[i] = longData;
        }
        for (int i = 0; i < doubleColumns.length; i++) {
            Double doubleData = doubleColumns[i].getMergeOperation().operate(newData.getDataDouble(i), this.getDataDouble(i));
            this.dataDoubles[i] = doubleData;
        }
        for (int i = 0; i < integerColumns.length; i++) {
            Integer integerData = integerColumns[i].getMergeOperation().operate(newData.getDataInteger(i), this.getDataInteger(i));
            this.dataIntegers[i] = integerData;
        }
        for (int i = 0; i < byteColumns.length; i++) {
            byte[] byteData = byteColumns[i].getMergeOperation().operate(newData.getDataBytes(i), this.getDataBytes(i));
            this.dataBytes[i] = byteData;
        }
        for (int i = 0; i < stringListColumns.length; i++) {
            StringLinkedList stringListData = stringListColumns[i].getMergeOperation().operate(newData.getDataStringList(i), this.getDataStringList(i));
            this.dataStringLists[i] = stringListData;
        }
        for (int i = 0; i < longListColumns.length; i++) {
            LongLinkedList longListData = longListColumns[i].getMergeOperation().operate(newData.getDataLongList(i), this.getDataLongList(i));
            this.dataLongLists[i] = longListData;
        }
        for (int i = 0; i < doubleListColumns.length; i++) {
            DoubleLinkedList doubleListData = doubleListColumns[i].getMergeOperation().operate(newData.getDataDoubleList(i), this.getDataDoubleList(i));
            this.dataDoubleLists[i] = doubleListData;
        }
        for (int i = 0; i < integerListColumns.length; i++) {
            IntegerLinkedList integerListData = integerListColumns[i].getMergeOperation().operate(newData.getDataIntegerList(i), this.getDataIntegerList(i));
            this.dataIntegerLists[i] = integerListData;
        }
    }

    @SuppressWarnings("unchecked")
    public void calculateFormula() {
        for (int i = 0; i < stringColumns.length; i++) {
            if (nonNull(stringColumns[i].getFormulaOperation())) {
                String stringData = (String)stringColumns[i].getFormulaOperation().operate(this);
                this.dataStrings[i] = stringData;
            }
        }
        for (int i = 0; i < longColumns.length; i++) {
            if (nonNull(longColumns[i].getFormulaOperation())) {
                Long longData = (Long)longColumns[i].getFormulaOperation().operate(this);
                this.dataLongs[i] = longData;
            }
        }
        for (int i = 0; i < doubleColumns.length; i++) {
            if (nonNull(doubleColumns[i].getFormulaOperation())) {
                Double doubleData = (Double)doubleColumns[i].getFormulaOperation().operate(this);
                this.dataDoubles[i] = doubleData;
            }
        }
        for (int i = 0; i < integerColumns.length; i++) {
            if (nonNull(integerColumns[i].getFormulaOperation())) {
                Integer integerData = (Integer)integerColumns[i].getFormulaOperation().operate(this);
                this.dataIntegers[i] = integerData;
            }
        }
    }

    @Override public final String toString() {
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
        dataStr.append("]");
        return dataStr.toString();
    }
}

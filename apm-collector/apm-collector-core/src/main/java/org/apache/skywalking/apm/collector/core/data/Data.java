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
public interface Data {
    int getDataStringsCount();

    int getDataLongsCount();

    int getDataDoublesCount();

    int getDataIntegersCount();

    int getDataStringListsCount();

    int getDataLongListsCount();

    int getDataDoubleListsCount();

    int getDataIntegerListsCount();

    int getDataBytesCount();

    void setDataString(int position, String value);

    void setDataLong(int position, Long value);

    void setDataDouble(int position, Double value);

    void setDataInteger(int position, Integer value);

    void setDataBytes(int position, byte[] dataBytes);

    String getDataString(int position);

    Long getDataLong(int position);

    Double getDataDouble(int position);

    Integer getDataInteger(int position);

    StringLinkedList getDataStringList(int position);

    LongLinkedList getDataLongList(int position);

    DoubleLinkedList getDataDoubleList(int position);

    IntegerLinkedList getDataIntegerList(int position);

    byte[] getDataBytes(int position);
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.mockcollector.entity;

public class ValidateData {
    public static ValidateData INSTANCE = new ValidateData();
    private RegistryItem registryItem;
    private SegmentItems segmentItem;

    public ValidateData() {
        registryItem = new RegistryItem();
        segmentItem = new SegmentItems();
    }

    public RegistryItem getRegistryItem() {
        return registryItem;
    }

    public SegmentItems getSegmentItem() {
        return segmentItem;
    }

    public static void clearData(){
        System.out.println("Clear Data");
        INSTANCE.segmentItem = new SegmentItems();
        INSTANCE.registryItem.getOperationNames().clear();
    }
}

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

package org.apache.skywalking.apm.collector.storage.ui.server;

import java.util.*;

/**
 * @author peng-yongsheng
 */
public class GCTrend {
    private List<Integer> youngGCCount;
    private List<Integer> oldGCount;
    private List<Integer> youngGCTime;
    private List<Integer> oldGCTime;

    public GCTrend() {
        this.youngGCCount = new LinkedList<>();
        this.oldGCount = new LinkedList<>();
        this.youngGCTime = new LinkedList<>();
        this.oldGCTime = new LinkedList<>();
    }

    public List<Integer> getYoungGCCount() {
        return youngGCCount;
    }

    public void setYoungGCCount(List<Integer> youngGCCount) {
        this.youngGCCount = youngGCCount;
    }

    public List<Integer> getOldGCount() {
        return oldGCount;
    }

    public void setOldGCount(List<Integer> oldGCount) {
        this.oldGCount = oldGCount;
    }

    public List<Integer> getYoungGCTime() {
        return youngGCTime;
    }

    public void setYoungGCTime(List<Integer> youngGCTime) {
        this.youngGCTime = youngGCTime;
    }

    public List<Integer> getOldGCTime() {
        return oldGCTime;
    }

    public void setOldGCTime(List<Integer> oldGCTime) {
        this.oldGCTime = oldGCTime;
    }
}

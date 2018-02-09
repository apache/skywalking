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

import java.util.List;

/**
 * @author peng-yongsheng
 */
public class MemoryTrend {
    private List<Integer> heap;
    private List<Integer> maxHeap;
    private List<Integer> noheap;
    private List<Integer> maxNoheap;

    public List<Integer> getHeap() {
        return heap;
    }

    public void setHeap(List<Integer> heap) {
        this.heap = heap;
    }

    public List<Integer> getMaxHeap() {
        return maxHeap;
    }

    public void setMaxHeap(List<Integer> maxHeap) {
        this.maxHeap = maxHeap;
    }

    public List<Integer> getNoheap() {
        return noheap;
    }

    public void setNoheap(List<Integer> noheap) {
        this.noheap = noheap;
    }

    public List<Integer> getMaxNoheap() {
        return maxNoheap;
    }

    public void setMaxNoheap(List<Integer> maxNoheap) {
        this.maxNoheap = maxNoheap;
    }
}

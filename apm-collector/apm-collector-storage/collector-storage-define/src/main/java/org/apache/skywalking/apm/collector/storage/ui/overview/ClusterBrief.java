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

package org.apache.skywalking.apm.collector.storage.ui.overview;

/**
 * @author peng-yongsheng
 */
public class ClusterBrief {
    private int numOfApplication;
    private int numOfService;
    private int numOfDatabase;
    private int numOfCache;
    private int numOfMQ;

    public int getNumOfApplication() {
        return numOfApplication;
    }

    public void setNumOfApplication(int numOfApplication) {
        this.numOfApplication = numOfApplication;
    }

    public int getNumOfService() {
        return numOfService;
    }

    public void setNumOfService(int numOfService) {
        this.numOfService = numOfService;
    }

    public int getNumOfDatabase() {
        return numOfDatabase;
    }

    public void setNumOfDatabase(int numOfDatabase) {
        this.numOfDatabase = numOfDatabase;
    }

    public int getNumOfCache() {
        return numOfCache;
    }

    public void setNumOfCache(int numOfCache) {
        this.numOfCache = numOfCache;
    }

    public int getNumOfMQ() {
        return numOfMQ;
    }

    public void setNumOfMQ(int numOfMQ) {
        this.numOfMQ = numOfMQ;
    }
}

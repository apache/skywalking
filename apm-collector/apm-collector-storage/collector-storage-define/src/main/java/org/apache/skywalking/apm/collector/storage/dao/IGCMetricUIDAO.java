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


package org.apache.skywalking.apm.collector.storage.dao;

import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.storage.base.dao.DAO;

/**
 * @author peng-yongsheng
 */
public interface IGCMetricUIDAO extends DAO {

    GCCount getGCCount(long[] timeBuckets, int instanceId);

    JsonObject getMetric(int instanceId, long timeBucket);

    JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket);

    class GCCount {
        private int young;
        private int old;
        private int full;

        public int getYoung() {
            return young;
        }

        public int getOld() {
            return old;
        }

        public int getFull() {
            return full;
        }

        public void setYoung(int young) {
            this.young = young;
        }

        public void setOld(int old) {
            this.old = old;
        }

        public void setFull(int full) {
            this.full = full;
        }
    }
}

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

package org.apache.skywalking.apm.collector.storage.ui.trace;

/**
 *   http: BriefInfo!
 *   rpc: BriefInfo!
 *   mq: BriefInfo!
 *   db: BriefInfo!
 *   cache: BriefInfo!
 *
 * @author wusheng
 */
public class SegmentBrief {
    private BriefInfo http;
    private BriefInfo rpc;
    private BriefInfo mq;
    private BriefInfo db;
    private BriefInfo cache;

    public SegmentBrief() {
        http = new BriefInfo();
        rpc = new BriefInfo();
        mq = new BriefInfo();
        db = new BriefInfo();
        cache = new BriefInfo();
    }

    public BriefInfo getHttp() {
        return http;
    }

    public void setHttp(BriefInfo http) {
        this.http = http;
    }

    public BriefInfo getRpc() {
        return rpc;
    }

    public void setRpc(BriefInfo rpc) {
        this.rpc = rpc;
    }

    public BriefInfo getMq() {
        return mq;
    }

    public void setMq(BriefInfo mq) {
        this.mq = mq;
    }

    public BriefInfo getDb() {
        return db;
    }

    public void setDb(BriefInfo db) {
        this.db = db;
    }

    public BriefInfo getCache() {
        return cache;
    }

    public void setCache(BriefInfo cache) {
        this.cache = cache;
    }
}

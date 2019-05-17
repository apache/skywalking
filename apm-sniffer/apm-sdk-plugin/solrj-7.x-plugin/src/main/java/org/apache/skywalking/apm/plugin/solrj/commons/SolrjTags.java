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
package org.apache.skywalking.apm.plugin.solrj.commons;

import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

public class SolrjTags {

    //    public static StringTag DB_TYPE = new StringTag(1, "db.type");
    public static StringTag TAG_QT = new StringTag(1,"qt");
    public static StringTag TAG_PATH = new StringTag(2, "path");
    public static StringTag TAG_COLLECTION = new StringTag(3, "collection");
    public static StringTag TAG_ACTION = new StringTag(4, "action");
    public static StringTag TAG_METHOD = new StringTag(5, "method");
    public static StringTag TAG_COMMIT_WITHIN = new StringTag(6, "commitWithin");
    public static StringTag TAG_STATUS = new StringTag(7, "status");
    public static StringTag TAG_Q_TIME = new StringTag(8, "QTime");
    public static StringTag TAG_NUM_FOUND = new StringTag(9, "numFound");


    public static StringTag TAG_COMMIT = new StringTag(15, "commit");
    public static StringTag TAG_SOFT_COMMIT = new StringTag(16, "solrCommit");

    public static StringTag TAG_OPTIMIZE = new StringTag(17, "optimize");
    public static StringTag TAG_MAX_OPTIMIZE_SEGMENTS = new StringTag(18, "maxOptimizeSegs");


//    static StringTag HANDLE = new StringTag(1,"qt");
//    static StringTag QT = new StringTag(1,"qt");
//    static StringTag QT = new StringTag(1,"qt");


    public static StringTag TAG_STATUS_CODE = new StringTag(10, "statusCode");
    public static StringTag TAG_ELAPSE_TIME = new StringTag(11, "elapseTime");

    public static StringTag TAG_CONTENT_TYPE = new StringTag(12, "contentType");
    public static StringTag TAG_CONTENT_LENGTH = new StringTag(13, "contentLength");
    public static StringTag TAG_CONTENT_ENCODING = new StringTag(14, "contentEncoding");

    public static void addElapseTime(AbstractSpan span, long etime) {
    	span.tag(TAG_ELAPSE_TIME, Long.toString(etime));
    }
    
    public static void addHttpResponse(AbstractSpan span, SolrjInstance instance) {
    	span.tag(TAG_CONTENT_TYPE, instance.content_type);
        span.tag(TAG_CONTENT_ENCODING, instance.content_encoding);

        span.tag(TAG_STATUS_CODE, Integer.toString(instance.statusCode));
        span.tag(TAG_CONTENT_LENGTH, Long.toString(instance.content_length));
    }
    

}

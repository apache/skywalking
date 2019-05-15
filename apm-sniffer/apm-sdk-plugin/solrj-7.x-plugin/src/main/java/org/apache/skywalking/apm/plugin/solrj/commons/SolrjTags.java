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
	
    public static StringTag DB_TYPE = new StringTag(1, "db.type");
    public static StringTag PATH = new StringTag(2, "path");
    public static StringTag COLLECTION = new StringTag(3, "collection");
    public static StringTag METHOD = new StringTag(4, "method");
    public static StringTag ACTION = new StringTag(5, "action");
    public static StringTag COMMIT_WITHIN = new StringTag(6, "commitWithin");
    public static StringTag STATUS = new StringTag(7, "status");
    public static StringTag Q_TIME = new StringTag(8, "QTime");
    public static StringTag NUM_FOUND = new StringTag(9, "numFound");

	static StringTag TAG_STATUS = new StringTag(10, "status");
	static StringTag TAG_ELAPSE_TIME = new StringTag(11, "elapse_time");

	static StringTag TAG_CONTENT_TYPE = new StringTag(20, "content.type");
	static StringTag TAG_CONTENT_LENGTH = new StringTag(21, "content.length");
	static StringTag TAG_CONTENT_ENCODING = new StringTag(22, "content.encoding");

    public static void addStatus(AbstractSpan span, int value) {
    	span.tag(TAG_STATUS, String.valueOf(value));
    }

    public static void addElapseTime(AbstractSpan span, long etime) {
    	span.tag(TAG_ELAPSE_TIME, Long.toString(etime));
    }
    
    public static void addHttpEntity(AbstractSpan span, SolrjInstance instance) {
    	span.tag(TAG_CONTENT_TYPE, instance.content_type);
    	span.tag(TAG_CONTENT_ENCODING, instance.content_encoding);
    	
    	span.tag(TAG_CONTENT_LENGTH, Long.toString(instance.content_length));
    }
    

}

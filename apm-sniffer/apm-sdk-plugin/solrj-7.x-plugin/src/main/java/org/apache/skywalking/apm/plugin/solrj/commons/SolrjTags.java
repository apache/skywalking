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

public class SolrjTags {
    public static StringTag TAG_QT = new StringTag("qt");
    public static StringTag TAG_COLLECTION = new StringTag("collection");

    public static StringTag TAG_Q_TIME = new StringTag("QTime");
    public static StringTag TAG_STATUS = new StringTag("status");

    public static StringTag TAG_START = new StringTag("start");
    public static StringTag TAG_SORT_BY = new StringTag("sort");
    public static StringTag TAG_NUM_FOUND = new StringTag("numFound");

    public static StringTag TAG_SOFT_COMMIT = new StringTag("softCommit");
    public static StringTag TAG_COMMIT_WITHIN = new StringTag("commitWithin");
    public static StringTag TAG_MAX_OPTIMIZE_SEGMENTS = new StringTag("maxOptimizeSegs");

    public static StringTag TAG_DOCS_SIZE = new StringTag("docsSize");
    public static StringTag TAG_DELETE_VALUE = new StringTag("delete.by");
}

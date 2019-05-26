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

import org.apache.http.Header;
import org.apache.http.HttpEntity;

public class Context {
    private long contentLength = 0;
    private String contentType = "";
    private String contentEncoding = "";
    private int statusCode = -1;

    private long startTime = 0;

    public Context() {
        startTime = System.currentTimeMillis();
    }

    public final void withHttpResponse(int statusCode, HttpEntity entity) {
        Header header = null;
        header = entity.getContentEncoding();
        if (header != null) {
            this.contentEncoding = header.toString();
        }
        header = entity.getContentType();
        if (header != null) {
            this.contentType = header.toString();
        }

        this.statusCode = statusCode;
        this.contentLength = entity.getContentLength();
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getStartTime() {
        return startTime;
    }
}

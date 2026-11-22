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

package org.apache.skywalking.oal.v2.generator.custom;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksMeasurement;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.Service;

/**
 * A source with the attribute types a custom source may have, under the name of the scope it extends.
 */
public class Endpoint extends BaseEndpoint implements Headers<String> {
    private Integer queueDepth = 1;
    private short statusCode;
    private byte retries;
    private Object objectTag;
    private CharSequence route;
    private List rawTags;
    private int bucketWidth;
    private String etag;
    private char result;
    private boolean feature;
    private List<Map<String, String>> entries;
    private Origin origin;
    private Map<String, String> headers;
    private Service peer;
    private long[] samples;
    private IndexedTags<Integer> indexTags;
    private List<String> path;
    private int counter;
    private RequestType kind;
    private ChecksMeasurement measurement;

    public Integer getQueueDepth() {
        return queueDepth;
    }

    public short getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(short statusCode) {
        this.statusCode = statusCode;
    }

    public byte getRetries() {
        return retries;
    }

    public void setRetries(byte retries) {
        this.retries = retries;
    }

    public Object getObjectTag() {
        return objectTag;
    }

    public void setObjectTag(Object objectTag) {
        this.objectTag = objectTag;
    }

    public CharSequence getRoute() {
        return route;
    }

    public void setRoute(CharSequence route) {
        this.route = route;
    }

    public List getRawTags() {
        return rawTags;
    }

    public void setRawTags(List rawTags) {
        this.rawTags = rawTags;
    }

    public int getBucketWidth() {
        return bucketWidth;
    }

    public void setBucketWidth(int bucketWidth) {
        this.bucketWidth = bucketWidth;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public char getResult() {
        return result;
    }

    public void setResult(char result) {
        this.result = result;
    }

    public boolean getFeature() {
        return feature;
    }

    public void setFeature(boolean feature) {
        this.feature = feature;
    }

    public List<Map<String, String>> getEntries() {
        return entries;
    }

    public Origin getOrigin() {
        return origin;
    }

    public void setOrigin(Origin origin) {
        this.origin = origin;
    }

    public String getHeader(CharSequence name) {
        return headers.get(name.toString());
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Service getPeer() {
        return peer;
    }

    public void setPeer(Service peer) {
        this.peer = peer;
    }

    public long[] getSamples() {
        return samples;
    }

    public void setSamples(long[] samples) {
        this.samples = samples;
    }

    @Override
    public String getHeaderValue(CharSequence name) {
        return headers.get(name.toString());
    }

    public IndexedTags<Integer> getIndexTags() {
        return indexTags;
    }

    public void setIndexTags(IndexedTags<Integer> indexTags) {
        this.indexTags = indexTags;
    }

    public List<String> getHeaderValues(CharSequence name) {
        return headers == null || !headers.containsKey(name.toString())
            ? null : Collections.singletonList(headers.get(name.toString()));
    }

    public RequestType getKind(CharSequence key) {
        return kind;
    }

    public void setKind(RequestType kind) {
        this.kind = kind;
    }

    public int getCounter(CharSequence name) {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public ChecksMeasurement getMeasurement() {
        return measurement;
    }

    public void setMeasurement(ChecksMeasurement measurement) {
        this.measurement = measurement;
    }

    public List<String> getPath() {
        return path;
    }

    public void setPath(List<String> path) {
        this.path = path;
    }
}

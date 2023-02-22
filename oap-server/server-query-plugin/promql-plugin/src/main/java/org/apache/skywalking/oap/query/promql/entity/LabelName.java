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

package org.apache.skywalking.oap.query.promql.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@JsonSerialize()
public enum LabelName {
    NAME("__name__"),
    LAYER("layer"),
    SCOPE("scope"),
    SERVICE("service"),
    SERVICE_INSTANCE("service_instance"),
    ENDPOINT("endpoint"),
    //For labeled value query
    LABELS("labels"),
    RELABELS("relabels"),
    //For labeled value
    LABEL("label"),
    //For tonN and record
    PARENT_SERVICE("parent_service"),
    TOP_N("top_n"),
    ORDER("order"),
    RECORD("record"),
    //For endpoint_traffic
    LIMIT("limit"),
    KEYWORD("keyword");

    final String label;

    private static final Map<String, LabelName> DICTIONARY = new HashMap<>();

    static {
        Arrays.stream(LabelName.values()).forEach(l -> {
            DICTIONARY.put(l.label, l);
        });
    }

    LabelName(final String label) {
        this.label = label;
    }

    public static LabelName labelOf(String label) {
        LabelName labelName = DICTIONARY.get(label);
        if (labelName == null) {
            throw new IllegalArgumentException("Unknown Label Name: " + label);
        }
        return labelName;
    }

    @JsonValue
    public String getLabel() {
        return this.label;
    }

    @Override
    public String toString() {
        return label;
    }
}

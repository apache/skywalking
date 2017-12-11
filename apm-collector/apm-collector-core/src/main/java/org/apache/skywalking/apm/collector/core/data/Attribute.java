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


package org.apache.skywalking.apm.collector.core.data;

/**
 * @author peng-yongsheng
 */
public class Attribute {
    private final String name;
    private final AttributeType type;
    private final Operation operation;

    public Attribute(String name, AttributeType type, Operation operation) {
        this.name = name;
        this.type = type;
        this.operation = operation;
    }

    public String getName() {
        return name;
    }

    public AttributeType getType() {
        return type;
    }

    public Operation getOperation() {
        return operation;
    }
}

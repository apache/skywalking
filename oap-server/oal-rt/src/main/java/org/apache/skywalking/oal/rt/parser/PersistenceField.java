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

package org.apache.skywalking.oal.rt.parser;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oal.rt.util.ClassMethodUtil;

@Getter
@Setter
public class PersistenceField {
    private String fieldName;
    private String setter;
    private String getter;
    private String fieldType;

    public PersistenceField(String fieldName, String fieldType) {
        this.fieldName = fieldName;
        this.setter = ClassMethodUtil.toSetMethod(fieldName);
        this.getter = ClassMethodUtil.toGetMethod(fieldName);
        this.fieldType = fieldType;
    }
}

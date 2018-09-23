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

package org.apache.skywalking.oap.server.core.query.entity;

/**
 * @author peng-yongsheng
 */
public enum LanguageTrans {
    INSTANCE;

    public int id(Language language) {
        switch (language) {
            case UNKNOWN:
                return 1;
            case JAVA:
                return 2;
            case DOTNET:
                return 3;
            case NODEJS:
                return 4;
            case PYTHON:
                return 5;
            case RUBY:
                return 6;
            default:
                return 1;
        }
    }

    public Language value(int id) {
        switch (id) {
            case 1:
                return Language.UNKNOWN;
            case 2:
                return Language.JAVA;
            case 3:
                return Language.DOTNET;
            case 4:
                return Language.NODEJS;
            case 5:
                return Language.PYTHON;
            case 6:
                return Language.RUBY;
            default:
                return Language.UNKNOWN;
        }
    }
}

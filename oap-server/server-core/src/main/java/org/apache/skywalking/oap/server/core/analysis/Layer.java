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

package org.apache.skywalking.oap.server.core.analysis;

import org.apache.skywalking.oap.server.core.UnexpectedException;

public enum Layer {

    undefined(0),

    mesh(1),

    general(2),

    os_linux(3),

    k8s(4),

    faas(5),

    mesh_cp(6),

    mesh_dp(7),

    database(8),

    cache(9),

    browser(10),

    so11y_oap(11),

    so11y_satellite(12),

    mq(13),

    virtual_database(14),

    virtual_mq(15);

    private final int value;

    Layer(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static Layer valueOf(int value) {
        switch (value) {
            case 0:
                return undefined;
            case 1:
                return mesh;
            case 2:
                return general;
            case 3:
                return os_linux;
            case 4:
                return k8s;
            case 5:
                return faas;
            case 6:
                return mesh_cp;
            case 7:
                return mesh_dp;
            case 8:
                return database;
            case 9:
                return cache;
            case 10:
                return browser;
            case 11:
                return so11y_oap;
            case 12:
                return so11y_satellite;
            case 13:
                return mq;
            case 14:
                return virtual_database;
            case 16:
                return virtual_mq;
            default:
                throw new UnexpectedException("Unknown Layer value");
        }
    }
}

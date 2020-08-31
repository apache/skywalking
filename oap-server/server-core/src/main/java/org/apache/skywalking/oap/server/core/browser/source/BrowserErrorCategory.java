/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.browser.source;

import lombok.Getter;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;

public enum BrowserErrorCategory {
    AJAX(0), RESOURCE(1), VUE(2), PROMISE(3), JS(4), UNKNOWN(5);

    public static BrowserErrorCategory fromErrorCategory(ErrorCategory category) {
        switch (category) {
            case ajax:
                return BrowserErrorCategory.AJAX;
            case resource:
                return BrowserErrorCategory.RESOURCE;
            case vue:
                return BrowserErrorCategory.VUE;
            case promise:
                return BrowserErrorCategory.PROMISE;
            case js:
                return BrowserErrorCategory.JS;
            default:
                return BrowserErrorCategory.UNKNOWN;
        }
    }

    @Getter
    private final int value;

    BrowserErrorCategory(int value) {
        this.value = value;
    }
}

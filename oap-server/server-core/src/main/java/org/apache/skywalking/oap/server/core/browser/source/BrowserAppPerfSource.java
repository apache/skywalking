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
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.source.Source;

public abstract class BrowserAppPerfSource extends Source {

    @Getter
    @Setter
    protected String name;
    @Setter
    @Getter
    protected NodeType nodeType;
    @Getter
    @Setter
    private int redirectTime;
    @Getter
    @Setter
    private int dnsTime;
    @Getter
    @Setter
    private int reqTime;
    @Getter
    @Setter
    private int domAnalysisTime;
    @Getter
    @Setter
    private int domReadyTime;
    @Getter
    @Setter
    private int blankTime;
}

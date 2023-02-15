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
 */

package org.apache.skywalking.library.elasticsearch.requests.factory.v7plus;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.factory.AliasFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.BulkFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.DocumentFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.IndexFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.RequestFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.SearchFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.TemplateFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.common.CommonAliasFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.common.CommonBulkFactory;
import org.apache.skywalking.library.elasticsearch.requests.factory.common.CommonSearchFactory;

@Getter
@Accessors(fluent = true)
public final class V81RequestFactory implements RequestFactory {
    private final TemplateFactory template;
    private final IndexFactory index;
    private final AliasFactory alias;
    private final DocumentFactory document;
    private final SearchFactory search;
    private final BulkFactory bulk;

    public V81RequestFactory(final ElasticSearchVersion version) {
        template = new V78TemplateFactory(version);
        index = new V7IndexFactory(version);
        alias = new CommonAliasFactory(version);
        document = new V81DocumentFactory(version);
        search = new CommonSearchFactory(version);
        bulk = new CommonBulkFactory(version);
    }
}

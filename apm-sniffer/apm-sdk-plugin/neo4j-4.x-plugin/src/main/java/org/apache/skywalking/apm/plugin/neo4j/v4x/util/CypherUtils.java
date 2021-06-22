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

package org.apache.skywalking.apm.plugin.neo4j.v4x.util;

import org.apache.skywalking.apm.plugin.neo4j.v4x.Neo4jPluginConfig.Plugin.Neo4j;
import org.apache.skywalking.apm.plugin.neo4j.v4x.Neo4jPluginConstants;

/**
 * Cypher language utils
 */
public final class CypherUtils {

    /**
     * Limit cypher body according to {@link Neo4j#CYPHER_BODY_MAX_LENGTH}
     *
     * @param body cypher query body
     * @return limited body
     */
    public static String limitBodySize(String body) {
        if (body == null) {
            return Neo4jPluginConstants.EMPTY_STRING;
        }

        if (Neo4j.CYPHER_BODY_MAX_LENGTH > 0 && body.length() > Neo4j.CYPHER_BODY_MAX_LENGTH) {
            return body.substring(0, Neo4j.CYPHER_BODY_MAX_LENGTH) + "...";
        }

        return body;
    }

    /**
     * Limit cypher query parameters size according to {@link Neo4j#CYPHER_PARAMETERS_MAX_LENGTH}
     *
     * @param parameters cypher query parameters
     * @return limited parameters
     */
    public static String limitParametersSize(String parameters) {
        if (parameters == null) {
            return Neo4jPluginConstants.EMPTY_STRING;
        }

        if (Neo4j.CYPHER_PARAMETERS_MAX_LENGTH > 0 && parameters.length() > Neo4j.CYPHER_PARAMETERS_MAX_LENGTH) {
            return parameters.substring(0, Neo4j.CYPHER_PARAMETERS_MAX_LENGTH) + "...";
        }

        return parameters;
    }
}

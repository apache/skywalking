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

package org.apache.skywalking.apm.plugin.seata;

import org.apache.skywalking.apm.agent.core.context.tag.StringTag;

/**
 * @author kezhenxu94
 */
public class Constants {
    public static final StringTag XID = new StringTag("XID");
    public static final StringTag TRANSACTION_ID = new StringTag("TransactionID");
    public static final StringTag BRANCH_ID = new StringTag("BranchId");
    public static final StringTag RESOURCE_ID = new StringTag("ResourceId");
    public static final StringTag LOG_OPERATION = new StringTag("LogOperation");
}

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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.Thermodynamic;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.DAO;

/**
 * @author peng-yongsheng
 */
public interface IMetricsQueryDAO extends DAO {

    IntValues getValues(String indName, Downsampling downsampling, long startTB, long endTB, Where where,
        String valueCName, Function function) throws IOException;

    IntValues getLinearIntValues(String indName, Downsampling downsampling, List<String> ids,
        String valueCName) throws IOException;

    IntValues[] getMultipleLinearIntValues(String indName, Downsampling downsampling, List<String> ids,
        List<Integer> linearIndex, String valueCName) throws IOException;

    Thermodynamic getThermodynamic(String indName, Downsampling downsampling, List<String> ids,
        String valueCName) throws IOException;
}

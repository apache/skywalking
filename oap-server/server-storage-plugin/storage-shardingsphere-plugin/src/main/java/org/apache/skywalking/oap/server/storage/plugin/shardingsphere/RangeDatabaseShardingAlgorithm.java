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

package org.apache.skywalking.oap.server.storage.plugin.shardingsphere;

import org.apache.shardingsphere.api.sharding.standard.RangeShardingAlgorithm;
import org.apache.shardingsphere.api.sharding.standard.RangeShardingValue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;

public final class RangeDatabaseShardingAlgorithm implements RangeShardingAlgorithm<String> {
    
    private final static int DB_NUMBER = 4;
    
    private final static long ONE_SECOND = 1000;
    
    private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    
    @Override
    public Collection<String> doSharding(final Collection<String> availableTargetNames, final RangeShardingValue<String> shardingValue) {
        Collection<String> result = new LinkedHashSet<>(availableTargetNames.size());
        Date left = parseDate(shardingValue.getValueRange().lowerEndpoint());
        Date right = parseDate(shardingValue.getValueRange().upperEndpoint());
        while (left.compareTo(right) <= 0) {
            result.addAll(getTargets(availableTargetNames, left));
            left.setTime(left.getTime() + ONE_SECOND);
        }
        return result;
    }
    
    private Date parseDate(final String dateValue) {
        try {
            return DATE_FORMAT.parse(dateValue);
        } catch (final ParseException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private Collection<String> getTargets(final Collection<String> availableTargetNames, final Date currentDate) {
        Collection<String> result = new LinkedHashSet<>();
        for (String each : availableTargetNames) {
            long current = Long.parseLong(DATE_FORMAT.format(currentDate));
            if (each.endsWith(current % DB_NUMBER + "")) {
                result.add(each);
            }
        }
        return result;
    }
}

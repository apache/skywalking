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

package org.apache.skywalking.oap.server.core.analysis.indicator;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.*;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * PxxIndicator is a parent indicator for p99/p95/p90/p75/p50 indicators. P(xx) indicator is also for P(xx) percentile.
 *
 * A percentile (or a centile) is a measure used in statistics indicating the value below which a given percentage of
 * observations in a group of observations fall. For example, the 20th percentile is the value (or score) below which
 * 20% of the observations may be found.
 *
 * @author wusheng, peng-yongsheng
 */
public abstract class PxxIndicator extends Indicator implements IntValueHolder {
    protected static final String DETAIL_GROUP = "detail_group";
    protected static final String VALUE = "value";
    protected static final String PRECISION = "precision";

    @Getter @Setter @Column(columnName = VALUE, isValue = true, function = Function.Avg) private int value;
    @Getter @Setter @Column(columnName = PRECISION) private int precision;
    @Getter @Setter @Column(columnName = DETAIL_GROUP) private IntKeyLongValueArray detailGroup;

    private final int percentileRank;
    private Map<Integer, IntKeyLongValue> detailIndex;

    public PxxIndicator(int percentileRank) {
        this.percentileRank = percentileRank;
        detailGroup = new IntKeyLongValueArray(30);
    }

    @Entrance
    public final void combine(@SourceFrom int value, @Arg int precision) {
        this.precision = precision;

        this.indexCheckAndInit();

        int index = value / precision;
        IntKeyLongValue element = detailIndex.get(index);
        if (element == null) {
            element = new IntKeyLongValue();
            element.setKey(index);
            element.setValue(1);
            addElement(element);
        } else {
            element.addValue(1);
        }
    }

    @Override
    public void combine(Indicator indicator) {
        PxxIndicator pxxIndicator = (PxxIndicator)indicator;
        this.indexCheckAndInit();
        pxxIndicator.indexCheckAndInit();

        pxxIndicator.detailIndex.forEach((key, element) -> {
            IntKeyLongValue existingElement = this.detailIndex.get(key);
            if (existingElement == null) {
                existingElement = new IntKeyLongValue();
                existingElement.setKey(key);
                existingElement.setValue(element.getValue());
                addElement(element);
            } else {
                existingElement.addValue(element.getValue());
            }
        });
    }

    @Override
    public final void calculate() {
        Collections.sort(detailGroup);
        int total = detailGroup.stream().mapToInt(element -> (int)element.getValue()).sum();
        int roof = Math.round(total * percentileRank * 1.0f / 100);

        int count = 0;
        for (IntKeyLongValue element : detailGroup) {
            count += element.getValue();
            if (count >= roof) {
                value = element.getKey() * precision;
                return;
            }
        }
    }

    private void addElement(IntKeyLongValue element) {
        detailGroup.add(element);
        detailIndex.put(element.getKey(), element);
    }

    private void indexCheckAndInit() {
        if (detailIndex == null) {
            detailIndex = new HashMap<>();
            detailGroup.forEach(element -> detailIndex.put(element.getKey(), element));
        }
    }
}

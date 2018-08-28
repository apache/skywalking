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

package org.apache.skywalking.oap.server.core.alarm;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import org.apache.skywalking.oap.server.core.analysis.indicator.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.indicator.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.indicator.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorType;
import org.apache.skywalking.oap.server.core.annotation.AnnotationListener;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntity;

/**
 * Indicator Alarm listener does the pre-analysis of each indicator implementation, when alarm happens, it drives
 * notification based on indicator value data type.
 *
 * @author wusheng
 */
public class IndicatorAlarmListener implements AnnotationListener {
    public static final IndicatorAlarmListener INSTANCE = new IndicatorAlarmListener();

    private final HashMap<Class, NotifyTarget> notifyTarget;

    IndicatorAlarmListener() {
        notifyTarget = new HashMap<>();
    }

    @Override public Class<? extends Annotation> annotation() {
        return IndicatorType.class;
    }

    @Override public void notify(Class aClass) {
        StorageEntity storageEntityAnnotation = (StorageEntity)aClass.getAnnotation(StorageEntity.class);
        if (storageEntityAnnotation == null) {
            return;
        }
        String indicatorName = storageEntityAnnotation.name();

        NotifyTarget target = new NotifyTarget();
        target.setIndicatorName(indicatorName);
        if (DoubleValueHolder.class.isAssignableFrom(aClass)) {
            target.setDataType(IndicatorAlarmDataType.DOUBLE);
        } else if (LongValueHolder.class.isAssignableFrom(aClass)) {
            target.setDataType(IndicatorAlarmDataType.LONG);
        } else if (IntValueHolder.class.isAssignableFrom(aClass)) {
            target.setDataType(IndicatorAlarmDataType.INT);
        } else {
            // If don't declare as any value holder, this is not an alarm candidate value.
            return;
        }

        notifyTarget.put(aClass, target);
    }

    public NotifyTarget getTarget(Class indicatorClass) {
        return notifyTarget.get(indicatorClass);
    }
}

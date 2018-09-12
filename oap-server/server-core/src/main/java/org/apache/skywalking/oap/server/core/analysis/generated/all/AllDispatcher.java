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

package org.apache.skywalking.oap.server.core.analysis.generated.all;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.source.*;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class AllDispatcher implements SourceDispatcher<All> {

    @Override public void dispatch(All source) {
        doAllP99(source);
        doAllP95(source);
        doAllP90(source);
        doAllP75(source);
        doAllP50(source);
        doAllHeatmap(source);
    }

    private void doAllP99(All source) {
        AllP99Indicator indicator = new AllP99Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doAllP95(All source) {
        AllP95Indicator indicator = new AllP95Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doAllP90(All source) {
        AllP90Indicator indicator = new AllP90Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doAllP75(All source) {
        AllP75Indicator indicator = new AllP75Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doAllP50(All source) {
        AllP50Indicator indicator = new AllP50Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }

    private void doAllHeatmap(All source) {
        AllHeatmapIndicator indicator = new AllHeatmapIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.combine(source.getLatency(), 100, 20);
        IndicatorProcess.INSTANCE.in(indicator);
    }
}

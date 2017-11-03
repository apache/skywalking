/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

requirejs.config({
    waitSeconds: 0,
    urlArgs: "r=" + (new Date()).getTime(),
    paths: {
        "jquery": "/webjars/jquery/2.2.4/jquery.min",
        "timers": "/public/js/timers",
        "jsCookie": "/public/js/js.cookie-2.1.4.min",
        "text": "/webjars/requirejs-text/2.0.15/text",
        "bootstrap": "/webjars/bootstrap/3.3.6/js/bootstrap.min",
        "metisMenu": "/webjars/metisMenu/2.5.2/dist/metisMenu.min",
        "slimscroll": "/webjars/jquery-slimscroll/1.3.6/jquery.slimscroll.min",
        "moment": "/webjars/momentjs/2.18.1/min/moment.min",
        "vis": "/webjars/vis/4.19.1/dist/vis.min",
        "datatables": "/webjars/datatables/1.10.13/media/js/jquery.dataTables.min",
        "head": "/head/head",
        "dagDraw": "/dag/dagDraw",
        "nodeCanvas": "/public/js/node.canvas",
        "dagHtml": "/dag/dag.html",
        "rangeSlider": "/webjars/ion.rangeSlider/2.1.4/js/ion.rangeSlider.min",
        "daterangepicker": "/webjars/bootstrap-daterangepicker/2.1.24/js/bootstrap-daterangepicker",
        "walden": "/public/js/walden",
        "vue": "/webjars/vue/2.1.3/vue.min",
        "vueResource": "/webjars/vue-resource/1.3.1/dist/vue-resource.min",
        "treeTable": "/webjars/jquery-treetable/3.2.0/jquery.treetable",
        "chartJs": "/webjars/chart.js/2.5.0/dist/Chart",
        "cpuChart": "/public/js/cpu-chart",
        "gcChart": "/public/js/gc-chart",
        "tpsChart": "/public/js/tps-chart",
        "applicationList": "/application/list",
        "applicationListHtml": "/application/list.html",
        "entryServiceList": "/service/entry/list",
        "entryServiceListHtml": "/service/entry/list.html",
        "serviceTreeList": "/service/treeList",
        "serviceTreeListHtml": "/service/treeList.html",
        "timeAxis": "/timeAxis/timeAxis",
        "timeAxisHtml": "/timeAxis/timeAxis.html",
        "memoryChart": "/public/js/memory-chart",
        "machineInfo": "/instance/machineInfo",
        "machineInfoHtml": "/instance/machineInfo.html",
        "metricCharts": "/instance/metricCharts",
        "metricSelectorHtml": "/instance/metricSelector.html",
        "appInstance": "/health/appInstance",
        "appInstanceHtml": "/health/appInstance.html",
        "instanceChart": "/public/js/instance-chart",
        "responseTimeConditionHtml": "/health/responseTimeCondition.html",
        "metric-chart": "/public/js/metric-chart"
    },
    shim: {
        bootstrap: ['jquery']
    }
});

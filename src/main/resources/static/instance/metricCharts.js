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

define(['jquery', 'vue', 'text!metricSelectorHtml', 'cpuChart', 'gcChart', 'memoryChart', 'tpsChart', 'chartJs', 'moment'],
    function ($, Vue, metricSelectorHtml, cpuChart, gcChart, memoryChart, tpsChart, Chart, moment) {
        window.chartColors = {
            blue: 'rgb(188, 214, 236)',
            blueBorder: 'rgb(185,203,218)',
            ogcDataColor: 'rgb(234, 155, 107)',
            ygcDataColor: 'rgb(118, 188, 236)',
            jvmUsedColor: 'rgb(222,232,245)',
            jvmUsedBgColor: 'rgb(194,211,235)',
            jvmMaxColor: 'rgb(253, 149, 77)',
            respTimeColor: 'rgb(188, 214, 236)',
            tps: 'rgb(95, 142, 173)',
            tpsBorder: 'rgb(248,134,59)'
        };

        function drawMetricSelector() {
            $("#metricSelectorDiv").html(metricSelectorHtml);
            new Vue({
                el: "#metricSelectorDiv",
                data: {},
                methods: {
                    displayChart: function (chartName, event) {
                        if ($(event.target).prop("checked")) {
                            metricChartsController.queryParam.metricNames.push(chartName);
                            metricChartsController.charts.push(initChart(chartName, moment(metricChartsController.currentXAxesOriginPoint, "YYYYMMDDHHmmss").subtract(5, "minutes").format("YYYYMMDDHHmmss")));
                            updateMetricCharts(metricChartsController.currentXAxesOriginPoint);
                        } else {
                            $("#" + chartName + "-div").remove();
                            var index = findChartObject(chartName);
                            if (index != -1) {
                                metricChartsController.charts[index].chartOperator.destroy();
                                metricChartsController.charts.splice(index, 1);
                                metricChartsController.removeQueryMetric(chartName);
                            }
                        }
                    }
                }
            });
            return this;
        }

        function findChartObject(chartName) {
            for (var i = 0; i < metricChartsController.charts.length; i++) {
                if (metricChartsController.charts[i].chartName == chartName) {
                    return i;
                }
            }

            return -1;
        }

        var metricChartsController = {
            charts: [],
            queryParam: {
                instanceId: undefined,
                startTime: undefined,
                endTime: undefined,
                metricNames: ["tps", "cpu", "resptime", "heapMemory", "gc"]
            },
            currentXAxesOriginPoint: undefined,
            loadData: function (endTime) {
                console.log("load chart data: end time: " + endTime);
                var that = metricChartsController;
                that.queryParam.endTime = endTime;
                that.currentXAxesOriginPoint = endTime;
                $.getJSON("/metricInfoWithTimeRange", this.queryParam, function (data) {
                    for (var i = 0; i < that.charts.length; i++) {
                        var dataFetcher = that.charts[i].dataFetcher;
                        console.log(that.charts[i].chartOperator);
                        that.charts[i].chartOperator.fillData(dataFetcher(data, that.charts[i].chartName), that.queryParam.startTime, that.queryParam.endTime);
                    }

                    that.queryParam.startTime = that.queryParam.endTime;
                    console.log("next query start time: " + that.queryParam.startTime + " next query end time: " + that.queryParam.endTime);
                });
            },
            removeQueryMetric: function (metricName) {
                for (var i = 0; i < this.queryParam.metricNames.length; i++) {
                    if (this.queryParam.metricNames[i] == metricName) {
                        this.queryParam.metricNames.splice(i, 1);
                        break;
                    }
                }
            },
            initParams: function (instanceId, startTime) {
                this.queryParam.instanceId = instanceId;
                this.queryParam.startTime = startTime;
            },
            redraw: function(timestamp){
                for (var i = 0; i < this.charts.length; i++) {
                    this.charts[i].chartOperator.redrawChart(timestamp);
                }
            }
        }

        function initPageCharts(instanceId, currentTime) {
            var startTime = moment(currentTime, "YYYYMMDDHHmmss").subtract(5, "minutes").format("YYYYMMDDHHmmss");
            var endTime = currentTime;

            metricChartsController.initParams(instanceId, startTime);

            var tpsChart = initChart("tps", startTime);
            metricChartsController.charts.push(tpsChart);

            var cpuChart = initChart("cpu", startTime);
            metricChartsController.charts.push(cpuChart);

            var heapMemoryChart = initChart("heapMemory", startTime);
            metricChartsController.charts.push(heapMemoryChart);

            var gcChart = initChart("gc", startTime);
            metricChartsController.charts.push(gcChart);

            metricChartsController.loadData(endTime);
        }

        function updateMetricCharts(currentTime) {
            var endTime = currentTime;
            var startTime = moment(currentTime, "YYYYMMDDHHmmss").subtract(5, "minutes").format("YYYYMMDDHHmmss");
            metricChartsController.queryParam.startTime = startTime;
            metricChartsController.currentXAxesOriginPoint = startTime;
            metricChartsController.redraw(startTime);
            metricChartsController.loadData(endTime);
        }

        function autoUpdateMetricCharts(currentTime) {
            metricChartsController.queryParam.startTime = currentTime;
            metricChartsController.loadData(currentTime);
        }

        function initChart(chartName, xAxesStartTime) {
            $("#metricCanvasDiv").append("<div class='row' style='margin-top: 10px;margin-bottom: 20px; ' id='" + chartName + "-div'><div class='col-lg-12 col-sm-12 ' style=' height:135px;'><canvas id='" + chartName + "' style='display: block; '></canvas></div></div>\n");
            var chartContext = document.getElementById(chartName).getContext("2d");
            var chartObject = getChartHandler(chartName);
            chartObject.chartOperator = chartObject.canvasBuilder.createChart(chartContext, xAxesStartTime);
            chartObject.dataFetcher = chartObject.dataFetcher;
            chartObject.chartName = chartName;
            return chartObject;
        }

        function getChartHandler(chartName) {
            console.log("chartName:" + chartName);
            var chartHandlerConfig;
            switch (chartName) {
                case "tps":
                    chartHandlerConfig = {
                        canvasBuilder: tpsChart,
                        dataFetcher: function (data) {
                            return {
                                tps: data.tps,
                                respTime: data.resptime
                            };
                        }
                    }
                    break;
                case "gc":
                    chartHandlerConfig = {
                        canvasBuilder: gcChart,
                        dataFetcher: function (data) {
                            var ygcArray = data.gc.ygc;
                            var ogcArray = data.gc.ogc;

                            var gcArray = [];
                            for (var i = 0; i < ygcArray.length; i++) {
                                gcArray.push({
                                    ygc: ygcArray[i],
                                    ogc: ogcArray[i]
                                });
                            }

                            return gcArray;
                        }
                    }
                    break;
                case "cpu":
                    chartHandlerConfig = {
                        canvasBuilder: cpuChart,
                        dataFetcher: function (data) {
                            return data.cpu;
                        }
                    }
                    break;
                default:
                    chartHandlerConfig = {
                        canvasBuilder: memoryChart,
                        dataFetcher: function (data, chartName) {
                            console.log(data);
                            console.log("default, chartName: " + chartName);
                            var chartData = data[chartName.toLowerCase()];

                            var usedArray = chartData.used;
                            var memoryData = [];
                            $.each(usedArray, function (index) {
                                memoryData.push({
                                    max: chartData.max,
                                    init: chartData.init,
                                    used: usedArray[index]
                                });
                            })

                            return memoryData;
                        }
                    }
            }
            return chartHandlerConfig;
        }

        return {
            drawMetricSelector: drawMetricSelector,
            initPageCharts: initPageCharts,
            updateMetricCharts: updateMetricCharts,
            autoUpdateMetricCharts: autoUpdateMetricCharts
        }
    }
);

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
        var config = {
            startTime: undefined
        };

        var dataChart = {
            CONSTANTS: {
                maxDisplayCount: 5, // min
                interval: 1 //second
            },
            charts: [],
            queryParam: {
                instanceId: instanceId,
                queryStartTime: undefined,
                queryEndTime: undefined,
                timeAxisEndTime: undefined
            },
            initQueryParam: function (instanceId, startTime) {
                return this.newQueryParam(instanceId, startTime, ["tps", "cpu", "heapMemory", "gc"]);
            },
            resetQueryParam: function (startTime) {
                return this.newQueryParam(this.queryParam.instanceId, startTime, this.getCurrentMetricName());
            },
            newQueryParam: function (instanceId, startTime, metricNames) {
                this.queryParam.instanceId = instanceId;
                this.queryParam.queryEndTime = startTime;
                this.queryParam.queryStartTime = startTime;
                this.queryParam.timeAxisEndTime = moment(startTime).add(this.CONSTANTS.maxDisplayCount, "minutes").format("x");
                return {
                    instanceId: instanceId,
                    startTime: startTime,
                    endTime: this.queryParam.timeAxisEndTime,
                    metricNames: metricNames
                }
            },
            queryParamUpdateCallback: function (dataSize) {
                var newQueryEndTime = parseInt(moment(this.queryParam.queryStartTime).add(dataSize - 1, "seconds").format("x"));
                if (newQueryEndTime > this.queryParam.queryEndTime) {
                    this.queryParam.queryEndTime = parseInt(newQueryEndTime);
                }

                if (newQueryEndTime > this.queryParam.timeAxisEndTime) {
                    this.queryParam.timeAxisEndTime = parseInt(newQueryEndTime);
                }
            },
            generateRedrawChartQueryParam: function (chartName) {
                return {
                    instanceId: this.queryParam.instanceId,
                    startTime: moment(this.queryParam.queryEndTime).subtract(this.CONSTANTS.maxDisplayCount, "minutes").format("x"),
                    endTime: this.queryParam.queryEndTime,
                    metricNames: [chartName]
                }
            },
            getNextQueryStartTime: function () {
                return parseInt(moment(this.queryParam.queryEndTime).add(this.CONSTANTS.interval, "second").format("x"));
            },
            getCurrentMetricName: function () {
                var metricNames = [];
                for (var i = 0; i < this.charts.length; i++) {
                    metricNames.push(this.charts[i].name);
                }
                return metricNames;
            },
            generateQueryParam: function (timeSpan) {
                this.queryParam.queryStartTime = this.getNextQueryStartTime();
                return {
                    instanceId: this.queryParam.instanceId,
                    startTime: this.getNextQueryStartTime(),
                    endTime: moment(this.queryParam.queryEndTime).add(timeSpan, "seconds").format("x"),
                    metricNames: this.getCurrentMetricName()
                }
            },
            getTimeAxisEndTime: function () {
                return parseInt(this.queryParam.timeAxisEndTime);
            }
        };

        function generateLabels(time, count) {
            var labels = [];
            for (var i = 0; i < count; i++) {
                labels.push(moment(time).add(i, "seconds").format("HH:mm:ss"));
            }
            return labels;
        }

        function generateRevertLabels(time, count) {
            var labels = [];
            for (var i = 0; i < count; i++) {
                labels.unshift(moment(time).subtract(i + 1, "seconds").format("HH:mm:ss"));
            }
            return labels;
        }

        function loadMetricCharts(instanceId, startTime) {
            var queryParam = dataChart.initQueryParam(instanceId, startTime);
            loadData("/metricInfoWithTimeRange", queryParam, function (data) {
                var tpsChart = buildChart("tps", startTime, data);

                dataChart.charts.push(tpsChart);
                var cpuChart = buildChart("cpu", startTime, data);

                dataChart.charts.push(cpuChart);
                var heapMemoryChart = buildChart("heapMemory", startTime, data);

                dataChart.charts.push(heapMemoryChart);
                var gcChart = buildChart("gc", startTime, data);

                dataChart.charts.push(gcChart);
            });
            return this;
        }

        function loadData(url, queryParams, handler, timestamp) {
            $.getJSON(url, queryParams, function (data) {
                handler(data, timestamp);
            });
        }

        function autoUpdateMetricCharts(instanceId) {
            loadData("/metricInfoWithTimeRange", dataChart.generateQueryParam(dataChart.CONSTANTS.interval), function (data, timestamp) {
                for (var i = 0; i < dataChart.charts.length; i++) {
                    dataChart.charts[i].updateChart(data, timestamp);
                }
            }, dataChart.getNextQueryStartTime());
        }

        function drawMetricSelector() {
            $("#metricSelectorDiv").html(metricSelectorHtml);
            new Vue({
                el: "#metricSelectorDiv",
                data: dataChart,
                methods: {
                    displayChart: function (chartName, event) {
                        // timer task need to stop
                        if ($(event.target).prop("checked")) {
                            loadData("/metricInfoWithTimeRange", dataChart.generateRedrawChartQueryParam(chartName), function (data, timestamp) {
                                var chart = buildChart(chartName, timestamp, data, generateRevertLabels);
                                dataChart.charts.push(chart);
                            }, dataChart.getTimeAxisEndTime());
                        } else {
                            var chartIndex = findChartObject(chartName);
                            if (chartIndex != -1) {
                                dataChart.charts[chartIndex].chart.destroy();
                                $("#" + chartName + "-div").remove();
                                dataChart.charts.splice(chartIndex, 1);
                            }
                        }
                    }
                }
            });
            return this;
        }

        function buildChart(chartName, timestamp, data, labelHandler) {
            if (labelHandler == undefined) {
                labelHandler = generateLabels;
            }

            $("#metricCanvasDiv").append("<div class='row' style='margin-top: 10px;margin-bottom: 20px; ' id='" + chartName + "-div'><div class='col-lg-12 col-sm-12 ' style=' height:135px;'><canvas id='" + chartName + "' style='display: block; '></canvas></div></div>\n");
            var ctx = document.getElementById(chartName).getContext("2d");
            var chartHandlerConfig = buildChartHandlerConfig(chartName);
            var canvasBuilder = chartHandlerConfig.canvasBuilder;
            var dataFetcher = chartHandlerConfig.dataFetcher;
            var chartData = dataFetcher(data, chartName);
            dataChart.queryParamUpdateCallback(chartData.length);
            var config = canvasBuilder.createChartConfig(labelHandler(timestamp, dataChart.CONSTANTS.maxDisplayCount * 60), chartData.data);
            return {
                name: chartName,
                type: config.type,
                chart: new Chart(ctx, config),
                updateChart: function (data, timestamplabel) {
                    var chartData = dataFetcher(data, chartName);
                    dataChart.queryParamUpdateCallback(chartData.length);
                    canvasBuilder.updateChart(this.chart, moment(timestamplabel).format("HH:mm:ss"), chartData.data);
                },
                redrawChart: function (canvasObject, data, timestamp) {
                    this.chart.destroy();
                    this.chart = null;
                    var chartData = dataFetcher(data, chartName);
                    dataChart.queryParamUpdateCallback(chartData.length);
                    var config = chartHandlerConfig.canvasBuilder.createChartConfig(generateLabels(timestamp, dataChart.CONSTANTS.maxDisplayCount * 60), chartData.data);
                    this.chart = new Chart(canvasObject, config);
                }
            };
        }

        function buildChartHandlerConfig(chartName) {
            var chartHandlerConfig;
            switch (chartName) {
                case "tps":
                    chartHandlerConfig = {
                        canvasBuilder: tpsChart,
                        dataFetcher: function (data) {
                            return {
                                length: data.tps.length,
                                data: {
                                    tps: data.tps,
                                    respTime: data.respTime
                                }
                            };
                        }
                    }
                    break;
                case "gc":
                    chartHandlerConfig = {
                        canvasBuilder: gcChart,
                        dataFetcher: function (data) {
                            return {
                                length: data.gc.ogc.length > data.gc.ygc.length ? data.gc.ogc.length : data.gc.ygc.length,
                                data: data.gc
                            };
                        }
                    }
                    break;
                case "cpu":
                    chartHandlerConfig = {
                        canvasBuilder: cpuChart,
                        dataFetcher: function (data) {
                            return {
                                length: data.cpu.length,
                                data: data.cpu
                            }
                        }
                    }
                    break;
                default:
                    chartHandlerConfig = {
                        canvasBuilder: memoryChart,
                        dataFetcher: function (data, chartName) {
                            return {
                                length: data[chartName].used.length,
                                data: data[chartName]
                            };
                        }
                    }
            }
            return chartHandlerConfig;
        }

        function findChartObject(chartName) {
            for (var i = 0; i < dataChart.charts.length; i++) {
                if (dataChart.charts[i].name === chartName) {
                    return i;
                }
            }
            return -1;
        }

        function updateMetricCharts(startTime) {
            var parameters = dataChart.resetQueryParam(startTime);
            loadData("/metricInfoWithTimeRange", parameters, function (data, timestamp) {
                for (var i = 0; i < dataChart.charts.length; i++) {
                    var canvasObject = document.getElementById(dataChart.charts[i].name).getContext("2d");
                    dataChart.charts[i].redrawChart(canvasObject, data, timestamp);
                }
            }, startTime);
        }

        return {
            loadMetricCharts: loadMetricCharts,
            autoUpdateMetricCharts: autoUpdateMetricCharts,
            drawMetricSelector: drawMetricSelector,
            updateMetricCharts: updateMetricCharts
        }
    }
);

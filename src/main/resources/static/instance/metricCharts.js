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
                            metricChartsController.charts.push(initChart(chartName));
                            if (!$("#autoUpdate").prop('checked')) {
                                updateMetricCharts(metricChartsController.currentXAxesOriginPoint);
                            } else {
                                metricChartsController.queryParam.startTime = moment(metricChartsController.queryParam.currentXAxesOriginPoint).format("YYYYMMDDHHmmss");
                            }
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
                metricNames: ["tps", "cpu", "respTime", "heapMemory", "gc"]
            },
            autoUpdateParam: {
                nextQueryEndTime: undefined
            },
            currentXAxesOriginPoint: undefined,
            loadData: function (endTime) {
                console.log("load Date: " + endTime);
                var that = metricChartsController;
                that.queryParam.endTime = moment(parseInt(endTime)).format("YYYYMMDDHHmmss");
                console.log("query end time : " + that.queryParam.endTime);
                $.getJSON("/metricInfoWithTimeRange", this.queryParam, function (data) {
                    var nextQueryStartTime = Number.MAX_VALUE;
                    var maxXAxesTimestamp = 0;
                    for (var i = 0; i < that.charts.length; i++) {
                        var dataFetcher = that.charts[i].dataFetcher;
                        that.charts[i].chartOperator.fillData(dataFetcher(data, that.charts[i].chartName));
                        var chartLastDate = that.charts[i].chartOperator.previousTime.timestamp;
                        if (chartLastDate < nextQueryStartTime) {
                            nextQueryStartTime = chartLastDate;
                        }

                        if (chartLastDate > maxXAxesTimestamp) {
                            maxXAxesTimestamp = chartLastDate;
                        }
                    }

                    // align x-axes,
                    for (var i = 0; i < that.charts.length; i++) {
                        that.charts[i].chartOperator.alignXAxes(maxXAxesTimestamp);
                        that.currentXAxesOriginPoint = that.charts[i].chartOperator.getChartStartTime();
                    }

                    that.queryParam.startTime = moment(nextQueryStartTime).format("YYYYMMDDHHmmss");
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
            redrawChart: function (timestamp) {
                for (var i = 0; i < this.charts.length; i++) {
                    this.charts[i].chartOperator.redrawChart(timestamp);
                }
            }
        }

        function initPageCharts(instanceId, startTime) {
            metricChartsController.initParams(instanceId, startTime);

            var tpsChart = initChart("tps", startTime);
            metricChartsController.charts.push(tpsChart);

            var cpuChart = initChart("cpu", startTime);
            metricChartsController.charts.push(cpuChart);

            var heapMemoryChart = initChart("heapMemory", startTime);
            metricChartsController.charts.push(heapMemoryChart);

            var gcChart = initChart("gc", startTime);
            metricChartsController.charts.push(gcChart);

            metricChartsController.loadData(moment(startTime, "YYYYMMDDHHmmss").add(5, "minutes").format("x"));
        }

        function updateMetricCharts(startTime) {
            metricChartsController.redrawChart(startTime);
            metricChartsController.loadData(moment(startTime).add(5, "minutes").format("x"));
        }

        function redrawChart(startTime) {
            metricChartsController.redrawChart(startTime);
        }

        function autoUpdateMetricCharts(endTime) {
            if (metricChartsController.autoUpdateParam.nextQueryEndTime == undefined) {
                metricChartsController.autoUpdateParam.nextQueryEndTime = parseInt(endTime);
            }

            var nextQueryTime = metricChartsController.autoUpdateParam.nextQueryEndTime;
            if (parseInt(endTime) - nextQueryTime > 3000) {
                metricChartsController.loadData(nextQueryTime);
                metricChartsController.autoUpdateParam.nextQueryEndTime += 3000;
            }
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
            var chartHandlerConfig;
            switch (chartName) {
                case "tps":
                    chartHandlerConfig = {
                        canvasBuilder: tpsChart,
                        dataFetcher: function (data) {
                            return {
                                tps: data.tps,
                                respTime: data.respTime
                            };
                        }
                    }
                    break;
                case "gc":
                    chartHandlerConfig = {
                        canvasBuilder: gcChart,
                        dataFetcher: function (data) {
                            return data.gc
                        }
                    }
                    break;
                case "cpu":
                    chartHandlerConfig = {
                        canvasBuilder: cpuChart,
                        dataFetcher: function (data) {
                            return data.cpu
                        }
                    }
                    break;
                default:
                    chartHandlerConfig = {
                        canvasBuilder: memoryChart,
                        dataFetcher: function (data, chartName) {
                            return data[chartName]
                        }
                    }
            }
            return chartHandlerConfig;
        }

        return {
            drawMetricSelector: drawMetricSelector,
            initPageCharts: initPageCharts,
            updateMetricCharts: updateMetricCharts,
            autoUpdateMetricCharts: autoUpdateMetricCharts,
            redrawChart: redrawChart
        }
    }
);

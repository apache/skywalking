define(['jquery', 'vue', 'text!metricChartsHtml', 'text!metricSelectorHtml', 'cpuChart', 'gcChart', 'memoryChart', 'tpsChart', 'chartJs', 'moment'],
    function ($, Vue, segmentHtml, metricSelectorHtml, cpuChart, gcChart, memoryChart, tpsChart, Chart, moment) {
        window.chartColors = {
            blue: 'rgb(188, 214, 236)',
            blueBorder: 'rg(185,203,218)',
            ogcDataColor: 'rgb(234, 155, 107)',
            ygcDataColor: 'rgb(118, 188, 236)',
            jvmUsedColor: 'rgb(222,232,245)',
            jvmUsedBgColor: 'rgb(194,211,235)',
            jvmMaxColor: 'rgb(253, 149, 77)',
            respTimeColor: 'rgb(188, 214, 236)',
            tps: 'rgb(95, 142, 173)',
            tpsBorder: 'rgb(248,134,59)',
        };
        var startTime;
        var dataChart = {
            chartNumber: 0,
            charts: [],
        }


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

        function loadMetricCharts() {
            loadData("/testData/instance/mertic.json", function (data) {
                $("#metricCanvasDiv").html(segmentHtml);
                var tpsChart = buildChart("tps", data.timestamp, data);
                dataChart.charts.push(tpsChart);
                dataChart.chartNumber++;

                var cpuChart = buildChart("cpu", data.timestamp, data);
                dataChart.charts.push(cpuChart);
                dataChart.chartNumber++;

                var heapMemoryChart = buildChart("heapMemory", data.timestamp, data);
                dataChart.charts.push(heapMemoryChart);
                dataChart.chartNumber++;

                var gcChart = buildChart("gc", data.timestamp, data);
                dataChart.charts.push(gcChart);
                dataChart.chartNumber++;
                startTime = moment(data.timestamp).add(5,"minutes");
            });

            return this;
        }

        function loadData(url, handler, timestamp) {
            $.getJSON(url, function (data) {
                handler(data, timestamp);
            });
        }

        function autoUpdateMetricCharts() {
            var endTime = moment(startTime).add(1, "seconds").format("x");
            loadData("/testData/instance/mertic.json", function (data, timestamp) {
                for (var i = 0; i < dataChart.charts.length; i++) {
                    dataChart.charts[i].updateChart(data, timestamp);
                }
            }, endTime);
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
                                    loadData("/testData/instance/mertic-0.json", function (data, timestamp) {
                                        var chart = buildChart(chartName, startTime, data, generateRevertLabels);
                                        dataChart.charts.push(chart);
                                    }, startTime);
                                    dataChart.chartNumber++;
                                } else {
                                    var chartIndex = findChartObject(chartName);
                                    dataChart.charts[chartIndex].chart.destroy();
                                    for (var i = chartIndex; i < dataChart.charts.length - 1; i++) {
                                        //   get canvas object
                                        var canvasObject = document.getElementById("canvas-" + i).getContext("2d");
                                        // redraw
                                        var swapChartObject = dataChart.charts[i + 1];
                                        swapChartObject.chart.destroy();
                                        var config = getChartConfig(swapChartObject.type, swapChartObject.chart);
                                        swapChartObject.chart = new Chart(canvasObject, config);
                                    }

                                    dataChart.charts.splice(chartIndex, 1);
                                    dataChart.chartNumber--;
                                }
                            },
                            needDisabled: function (inputName) {
                                return !$("#" + inputName).prop("checked");
                            }
                        }
                    });
            return this;
        }

        function buildChart(chartName, timestamp, data, labelHandler) {
            if (labelHandler == undefined){
                labelHandler = generateLabels;
            }
            var ctx = document.getElementById("canvas-" + dataChart.charts.length).getContext("2d");
            var chartHandlerConfig = buildChartHandlerConfig(chartName);
            var canvasBuilder = chartHandlerConfig.canvasBuilder;
            var dataFetcher = chartHandlerConfig.dataFetcher;
            var config = canvasBuilder.createChartConfig(labelHandler(timestamp, 300), dataFetcher(data, chartName));
            return {
                name: chartName,
                type: config.type,
                chart: new Chart(ctx, config),
                updateChart: function (data, timestamplabel) {
                    canvasBuilder.updateChart(this.chart, moment(timestamplabel).format("HH:mm:ss"), dataFetcher(data, chartName));
                },
                redrawChart: function (canvasObject, data, timestamp) {
                    this.chart.destroy();
                    this.chart = null;
                    var config = chartHandlerConfig.canvasBuilder.createChartConfig(generateLabels(timestamp, 300), chartHandlerConfig.dataFetcher(data, this.name));
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
                            return data.gc;
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
                            return data[chartName];
                        }
                    }
            }
            return chartHandlerConfig;
        }

        function getChartConfig(type, chartObject) {
            return {
                type: type,
                data: chartObject.data,
                options: chartObject.options
            }
        }

        function findChartObject(chartName) {
            for (var i = 0; i < dataChart.charts.length; i++) {
                if (dataChart.charts[i].name === chartName) {
                    return i;
                }
            }
            return -1;
        }

        function updateMetricCharts(timestamp) {
            console.log(timestamp);
            loadData("/testData/instance/mertic.json", function (data, timestamp) {
                for (var i = 0; i < dataChart.charts.length; i++) {
                    var canvasObject = document.getElementById("canvas-" + i).getContext("2d");
                    dataChart.charts[i].redrawChart(canvasObject, data, timestamp);
                }
            }, timestamp);
        }

        return {
            loadMetricCharts: loadMetricCharts,
            autoUpdateMetricCharts: autoUpdateMetricCharts,
            drawMetricSelector: drawMetricSelector,
            updateMetricCharts: updateMetricCharts
        }
    }
);

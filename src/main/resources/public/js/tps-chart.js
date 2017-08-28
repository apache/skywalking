define(['chartJs', 'moment', 'metric-chart'], function (Chart, moment, metricChart) {
    function TPSMetricChart(chartContext, startTime) {
        metricChart.MetricChart.apply(this, arguments);
        this.chartConfig = function (labels) {
            return {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: "TPS",
                        borderWidth: 1,
                        borderColor: window.chartColors.tpsBorder,
                        backgroundColor: window.chartColors.tpsBorder,
                        data: [],
                        yAxisID: "y-axis-tps",
                        type: "line",
                        fill: false,
                        radius: 0,
                    },
                        {
                            label: "Response Time (ms)",
                            borderWidth: 0,
                            backgroundColor: window.chartColors.respTimeColor,
                            data: [],
                            pointHoverRadius: 1,
                            radius: 0,
                            yAxisID: "y-axis-responseTime",
                        }
                    ]
                },
                options: {
                    scales: {
                        xAxes: [{
                            gridLines: {
                                display: false
                            },
                            ticks: {
                                callback: function (dataLabel, index) {
                                    return index % 20 === 0 ? dataLabel : null;
                                },
                                maxRotation: 0,
                                padding: 20
                            }
                        }],
                        yAxes: [{
                            display: false,
                            id: 'y-axis-responseTime',
                            stacked: true,
                            position: "right",
                            scaleLabel: {
                                display: true
                            },
                            gridLines: {
                                display: true
                            },
                            ticks: {
                                callback: function (dataLabel, index) {
                                    var label = dataLabel.toString();
                                    for (var index = label.length; index <= 5; index++) {
                                        label = label + " ";
                                    }
                                    return label;
                                }
                            }
                        },
                            {

                                id: 'y-axis-tps',
                                stacked: true,
                                position: "left",
                                scaleLabel: {
                                    display: true
                                },
                                gridLines: {
                                    display: true
                                },
                                ticks: {
                                    callback: function (dataLabel, index) {
                                        var label = dataLabel.toString();
                                        var blank = "";
                                        for (var index = label.length; index <= 5; index++) {
                                            blank = blank + " ";
                                        }

                                        return label + blank;
                                    }
                                }
                            },
                        ]
                    },
                    maintainAspectRatio: false,
                    legend: {
                        display: true,
                        position: 'top'
                    },
                    tooltips: {
                        mode: 'index',
                        intersect: false,
                    }
                }
            };
        };
        this.fillData = function (data) {
            this.fillResponseTimeData(data.respTime, this.updateResponseTimeData);
            this.fillTpsData(data.tps, this.updateTpsData);
        };
        this.fillResponseTimeData = function (data, updateHandler) {
            if (data.length == 0) {
                return;
            }
            var previousTime = metricChart.FormatDate(data[0].timeBucket);
            updateHandler(this, this.calculateDataIndex(previousTime), data[0].data);
            for (var i = 1; i < data.length; i++) {
                var formatTimeBucket = metricChart.FormatDate(data[i].timeBucket);
                var lostDataSize = formatTimeBucket.subtractSeconds(previousTime);
                if (lostDataSize > 1) {
                    this.fillLostData(previousTime, lostDataSize, updateHandler);
                }
                updateHandler(this, this.calculateDataIndex(formatTimeBucket), data[i].data);
                previousTime = formatTimeBucket;
            }
            this.previousTime = previousTime;
            this.chartObject.update();
        };
        this.fillTpsData = function (data, updateHandler) {
            if (data.length == 0) {
                return;
            }
            var previousTime = metricChart.FormatDate(data[0].timeBucket);
            updateHandler(this, this.calculateDataIndex(previousTime), data[0].data);
            for (var i = 1; i < data.length; i++) {
                var formatTimeBucket = metricChart.FormatDate(data[i].timeBucket);
                var lostDataSize = formatTimeBucket.subtractSeconds(previousTime);
                if (lostDataSize > 1) {
                    this.fillLostData(previousTime, lostDataSize, updateHandler);
                }
                updateHandler(this, this.calculateDataIndex(formatTimeBucket), data[i].data);
                previousTime = formatTimeBucket;
            }
            this.previousTime = previousTime;
            this.chartObject.update();
        };

        this.fillLostData = function (baseTime, dataCount, updateHandler) {
            for (var j = 1; j < dataCount; j++) {
                var index = this.calculateDataIndex(baseTime.addSeconds(j));
                updateHandler(this, index, 0);
            }
        };

        this.updateResponseTimeData = function (caller, index, data) {
            caller.chartObject.data.datasets[0].data[index] = data;
        };

        this.updateTpsData = function (caller, index, data) {
            caller.chartObject.data.datasets[1].data[index] = data;
        };
    }

    function createChart(chartContext, startTime) {
        return metricChart.createMetricChart(TPSMetricChart, chartContext, startTime);
    }

    return {
        createChart: createChart
    }
})

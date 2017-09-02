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
        this.fillData = function (data, startTime, endTime) {
            this.fillResponseTimeData(data.respTime, startTime, endTime);
            this.fillTpsData(data.tps);
        };
        this.fillResponseTimeData = function (data) {
            for (var i = 0; i < data.length; i++) {
                this.chartObject.data.datasets[1].data.push(data[i]);
                this.chartObject.data.datasets[1].data.shift();
            }
            this.chartObject.update();
        };
        this.fillTpsData = function (data) {
            for (var i = 0; i < data.length; i++) {
                this.chartObject.data.datasets[0].data.push(data[i]);
                this.chartObject.data.datasets[0].data.shift();
            }
            this.chartObject.update();
        };
    }

    function createChart(chartContext, startTime) {
        var chart = metricChart.createMetricChart(TPSMetricChart, chartContext, startTime);
        for (var i = 0; i < 300; i++) {
            chart.chartObject.data.datasets[0].data.push(0);
            chart.chartObject.data.datasets[1].data.push(0);
        }
        return chart;
    }

    return {
        createChart: createChart
    }
})

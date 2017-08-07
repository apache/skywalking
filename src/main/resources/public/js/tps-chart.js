define([], function () {
    function createChartConfig(labels, tpsData) {
        return {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: "TPS",
                    borderWidth: 1,
                    borderColor: window.chartColors.tpsBorder,
                    backgroundColor: window.chartColors.tpsBorder,
                    data: tpsData.tps,
                    yAxisID: "y-axis-tps",
                    type: "line",
                    fill: false,
                    radius: 0,
                },
                    {
                        label: "Response Time (ms)",
                        borderWidth: 0,
                        backgroundColor: window.chartColors.respTimeColor,
                        data: tpsData.respTime,
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
    }

    function updateChart(chartObject, labels, tpsData) {
        var showTpsData = chartObject.data.datasets[0].data;
        var showRespTimeData = chartObject.data.datasets[1].data;
        if (showTpsData.length > 300) {
            chartObject.data.labels.shift();
            chartObject.data.labels.push(labels);
        }

        dealData(tpsData.tps, function (data) {
            if (showTpsData.length > 300) {
                showTpsData.shift();
            }
            showTpsData.push(data);
        });

        dealData(tpsData.respTime, function (data) {
            if (showRespTimeData.length > 300) {
                showRespTimeData.shift();
            }
            showRespTimeData.push(data);
        });
        chartObject.update();
    }

    function dealData(data, callback) {
        for (var i = 0; i < data.length; i++) {
            callback(data[i]);
        }
    }

    return {
        createChartConfig: createChartConfig,
        updateChart: updateChart
    }
})

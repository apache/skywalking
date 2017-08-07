define([], function () {
    function createChartConfig(labels, gcData) {
        return {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: "Young GC",
                    backgroundColor: window.chartColors.ygcDataColor,
                    borderColor: window.chartColors.ygcDataColor,
                    data: gcData.ygc,
                },
                    {
                        label: "Old GC",
                        backgroundColor: window.chartColors.ogcDataColor,
                        borderColor: window.chartColors.ogcDataColor,
                        data: gcData.ogc,
                    }
                ]
            },
            options: {
                maintainAspectRatio: false,
                tooltips: {
                    mode: 'index',
                    intersect: false,
                },
                scales: {
                    xAxes: [{
                        stacked: true,
                        gridLines: {
                            display: false
                        },
                        ticks: {
                            callback: function (dataLabel, index) {
                                return index % 20 == 0 ? dataLabel : '';
                            },
                            maxRotation: 0,
                            padding: 20
                        }
                    }],
                    yAxes: [{
                        stacked: true,
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
                            },
                        }
                    }]
                },
                legend: {
                    display: true,
                    position: 'top'
                },
            }
        };
    }

    function updateChart(chartObject, label, gcData) {
        var showYgcData = chartObject.data.datasets[0].data;
        var showOgcData = chartObject.data.datasets[1].data;
        if (showYgcData.length > 300) {
            chartObject.data.labels.shift();
            chartObject.data.labels.push(label);
        }

        dealData(gcData.ygc, function (data) {
            if (showYgcData.length > 300) {
                showYgcData.shift();
            }
            showYgcData.push(data);
        });

        dealData(gcData.ogc, function (data) {
            if (showOgcData.length > 300) {
                showOgcData.shift();
            }
            showOgcData.push(data);
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

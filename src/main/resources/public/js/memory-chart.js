define([], function () {

    function generateMaxUsage(number, length) {
        var data = [];
        for (var i = 0; i < length; i++) {
            data.push(number);
        }
        return data;
    }

    function createChartConfig(labels, memoryData) {
        var showMax = memoryData.max + 200;
        var maxData = generateMaxUsage(memoryData.max, labels.length);
        return {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: "Used (MB)",
                    borderWidth: 1,
                    borderColor: window.chartColors.jvmUsedColor,
                    backgroundColor: window.chartColors.jvmUsedBgColor,
                    data: memoryData.used,
                    fill: true,
                    pointHoverRadius: 1,
                    radius: 0,
                },
                    {
                        label: "Max (MB)",
                        borderWidth: 1,
                        borderColor: window.chartColors.jvmMaxColor,
                        backgroundColor: window.chartColors.jvmMaxColor,
                        data: maxData,
                        fill: false,
                        radius: 0,
                    }
                ]
            },
            options: {
                maintainAspectRatio: false,
                legend: {
                    display: true,
                    position: 'top'
                },
                tooltips: {
                    mode: 'index',
                    intersect: false,
                },
                scales: {
                    xAxes: [{
                        gridLines: {
                            display: false
                        },
                        ticks: {
                            maxRotation: 0,
                            padding: 20,
                            callback: function (dataLabel, index) {
                                return index % 20 === 0 ? dataLabel : '';
                            },
                            autoSkip: true
                        }
                    }],
                    yAxes: [{
                        position: 'left',
                        display: true,
                        scaleLabel: {
                            display: true
                        },
                        gridLines: {
                            display: true
                        },
                        ticks: {
                            min: memoryData.min,
                            max: showMax,
                            callback: function (dataLabel, index) {
                                var label = dataLabel.toString();
                                var blank = "";
                                for (var index = label.length; index <= 5; index++) {
                                    blank = blank + " ";
                                }

                                return label + blank;
                            }
                        }
                    }]
                }
            }
        };
    }

    function updateChart(chartObject, label, memoryData) {
        var showData = chartObject.data.datasets[0].data;
        if (showData.length > 300) {
            chartObject.data.labels.shift();
            chartObject.data.labels.push(label);
        }

        dealData(memoryData.used, function (data) {
            if (showData.length > 300) {
                showData.shift();
            }
            showData.push(data);
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

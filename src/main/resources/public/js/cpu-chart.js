define([], function () {
    function createChartConfig(labels, data) {
        return {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: "CPU (%)",
                    borderWidth: 1,
                    borderColor: window.chartColors.blueBorder,
                    backgroundColor: window.chartColors.blue,
                    data: data,
                    radius: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    xAxes: [{
                        display: true,
                        gridLines: {
                            display: false
                        },
                        ticks: {
                            callback: function (dataLabel, index) {
                                return index % 20 === 0 ? dataLabel : '';
                            },
                            maxRotation: 0,
                            padding: 20
                        }
                    }],
                    yAxes: [{
                        display: true,
                        scaleLabel: {
                            display: true,
                        },
                        gridLines: {
                            display: true
                        },
                        ticks: {
                            max: 100,
                            min: 0,
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

    function updateChart(chartObject, label, data) {
        var showData = chartObject.data.datasets[0].data;
        if (showData.length > 300) {
            for (var i = 0; i < data.length; i++) {
                showData.shift();
            }
            chartObject.data.labels.shift();
            chartObject.data.labels.push(label);
        }
        for (var i = 0; i < data.length; i++) {
            showData.push(data[i]);
        }
        chartObject.update();
    }

    return {
        createChartConfig: createChartConfig,
        updateChart: updateChart
    }
});

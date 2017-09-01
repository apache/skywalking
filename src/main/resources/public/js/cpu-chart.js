define(['chartJs', 'moment', 'metric-chart'], function (Chart, moment, metricChart) {

    function CPUMetric(chartContext, startTime) {
        metricChart.MetricChart.apply(this, arguments);
        this.chartConfig = function (labels) {
            return {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: "CPU (%)",
                        borderWidth: 1,
                        borderColor: window.chartColors.blueBorder,
                        backgroundColor: window.chartColors.blue,
                        data: [],
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
                    },
                    tooltips: {
                        mode: 'index',
                        intersect: false,
                    }
                }
            };
        };

        this.updateYAxesData = function (index, data) {
            this.chartObject.data.datasets[0].data[index] = data;
        };

        this.fillLostData = function (baseTime, dataCount) {
            for (var j = 1; j < dataCount; j++) {
                var index = this.calculateDataIndex(baseTime.addSeconds(j));
                this.updateYAxesData(index, 0);
            }
        }
    }

    function createChart(chartContext, startTime) {
        return metricChart.createMetricChart(CPUMetric, chartContext, startTime);
    }

    return {
        createChart: createChart
    }
});

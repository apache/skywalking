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
        this.fillData = function (data, startTime, endTime) {
            console.log(data);
            var beginIndexOfFillData = (this.toUnixTimestamp(startTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
            var endIndexOfFillData = (this.toUnixTimestamp(endTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
            for (var index = beginIndexOfFillData, dataIndex = 0; index <= endIndexOfFillData; index++, dataIndex++) {
                if (index > 299) {
                    console.log("update chart x axes");
                    this.updateXAxes();
                    this.chartObject.data.datasets[0].data.shift();
                }
                this.chartObject.data.datasets[0].data.push(data[dataIndex]);
                this.updateChartStartTime(index);
            }
            this.chartObject.update();
        };
    }

    function createChart(chartContext, startTime) {
        var chart = metricChart.createMetricChart(CPUMetric, chartContext, startTime);
        return chart;
    }

    return {
        createChart: createChart
    }
});

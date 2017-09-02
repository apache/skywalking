define(['chartJs', 'moment', 'metric-chart'], function (Chart, moment, metricChart) {

    function MemoryMetricChart(chartContext, startTime) {
        metricChart.MetricChart.apply(this, arguments);
        this.chartConfig = function (labels) {
            return {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: "Used (MB)",
                        borderWidth: 1,
                        borderColor: window.chartColors.jvmUsedColor,
                        backgroundColor: window.chartColors.jvmUsedBgColor,
                        data: [],
                        fill: true,
                        pointHoverRadius: 1,
                        radius: 0,
                    },
                        {
                            label: "Max (MB)",
                            borderWidth: 1,
                            borderColor: window.chartColors.jvmMaxColor,
                            backgroundColor: window.chartColors.jvmMaxColor,
                            data: [],
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
        };
        this.fillData = function (data, startTime, endTime) {
            console.log(data);
            for (var i = 0; i < data.length; i++) {
                this.chartObject.data.datasets[0].data.push(data[i].used);
                this.chartObject.data.datasets[0].data.shift();

                this.chartObject.data.datasets[1].data.push(data[i].max);
                this.chartObject.data.datasets[1].data.shift();
            }
            this.chartObject.update();
        };
    }

    function createChart(chartContext, startTime) {
        var chart = metricChart.createMetricChart(MemoryMetricChart, chartContext, startTime);
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

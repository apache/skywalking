define(['chartJs', 'moment', 'metric-chart'], function (Chart, moment, metricChart) {

        function GCMetricChart(chartContext, startTime) {
            metricChart.MetricChart.apply(this, arguments);
            this.chartConfig = function (labels) {
                return {
                    type: 'bar',
                    data: {
                        labels: labels,
                        datasets: [{
                            label: "Young GC",
                            backgroundColor: window.chartColors.ygcDataColor,
                            borderColor: window.chartColors.ygcDataColor,
                            data: [],
                        },
                            {
                                label: "Old GC",
                                backgroundColor: window.chartColors.ogcDataColor,
                                borderColor: window.chartColors.ogcDataColor,
                                data: [],
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
            };
            this.fillData = function (data, startTime, endTime) {
                for (var i = 0; i < data.length; i++) {
                    this.chartObject.data.datasets[0].data.push(data[i].ygc);
                    this.chartObject.data.datasets[0].data.shift();

                    this.chartObject.data.datasets[1].data.push(data[i].ogc);
                    this.chartObject.data.datasets[1].data.shift();
                }
                this.chartObject.update();
            };
        }

        function createChart(chartContext, startTime) {
            var chart = metricChart.createMetricChart(GCMetricChart, chartContext, startTime);
            for (var i = 0; i < 300; i++) {
                chart.chartObject.data.datasets[0].data.push(0);
                chart.chartObject.data.datasets[1].data.push(0);
            }
            return chart;
        }

        return {
            createChart: createChart
        }
    }
)

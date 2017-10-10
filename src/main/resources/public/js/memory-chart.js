/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

define(['chartJs', 'moment', 'metric-chart'], function (Chart, moment, metricChart) {

    function MemoryMetricChart(chartContext, startTime) {
        metricChart.MetricChart.apply(this, arguments);
        this.chartConfig = function (labels) {
            return {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: "Used (GB)",
                        borderWidth: 1,
                        borderColor: window.chartColors.jvmUsedColor,
                        backgroundColor: window.chartColors.jvmUsedBgColor,
                        data: [],
                        fill: true,
                        pointHoverRadius: 1,
                        radius: 0,
                    },
                        {
                            label: "Max (GB)",
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
        };
        this.fillData = function (data, startTime, endTime) {
            var beginIndexOfFillData = (this.toUnixTimestamp(startTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
            var endIndexOfFillData = (this.toUnixTimestamp(endTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
            console.log("beginIndexOfFillData : " + beginIndexOfFillData + " endIndexOfFillData: " + endIndexOfFillData);
            var max = undefined;
            var init = undefined;
            for (var index = beginIndexOfFillData, dataIndex = 0; index <= endIndexOfFillData; index++, dataIndex++) {
                if (index > 299) {
                    console.log("update chart x axes");
                    this.updateXAxes();
                    this.chartObject.data.datasets[0].data.shift();
                    this.chartObject.data.datasets[1].data.shift();
                }
                this.chartObject.data.datasets[0].data.push(this.convertToGB(data[dataIndex].used));
                this.chartObject.data.datasets[1].data.push(data[dataIndex].max < 0 ? 0 : this.convertToGB(data[dataIndex].max));
                this.updateChartStartTime(index);

                if (max == undefined && data[dataIndex].max != undefined) {
                    max = data[dataIndex].max;
                }
                if (init == undefined && data[dataIndex].init != undefined) {
                    init = data[dataIndex].init;
                }
            }

            this.chartObject.options.scales.yAxes[0].ticks.min = init == undefined ? 0 : this.convertToGB(init);
            if (max != undefined && max >= 0) {
                this.chartObject.options.scales.yAxes[0].ticks.max = this.convertToGB(max + 1 * 100000000);
            } else {
                this.chartObject.options.scales.yAxes[0].ticks.max = 1;
            }
            this.chartObject.update();
        };
        this.convertToGB = function (data) {
            return Math.floor((data / 1000000000) * 100) / 100;
        }
    }

    function createChart(chartContext, startTime) {
        var chart = metricChart.createMetricChart(MemoryMetricChart, chartContext, startTime);
        return chart;
    }

    return {
        createChart: createChart
    }
})

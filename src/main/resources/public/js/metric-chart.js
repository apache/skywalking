define(['chartJs', 'moment'], function (Chart, moment) {
    function MetricChart(chartContext, startTime) {
        this.chartStartTime = FormatDate(startTime);
        this.chartContext = chartContext;
        this.previousTime = undefined;
        this.chartObject = function (labels) {
        };
        this.updateYAxesData = function (data) {
        };
        this.fillLostData = function (baseTime, dataCount) {

        };
        this.fillData = function(data){
            // data example: {timeBucket: 20170824225619, data: 75}
            if (data.length == 0) {
                return;
            }
            var previousTime = FormatDate(data[0].timeBucket);
            this.updateYAxesData(this.calculateDataIndex(previousTime), data[0].data);
            for (var i = 1; i < data.length; i++) {
                var formatTimeBucket = FormatDate(data[i].timeBucket);
                var lostDataSize = formatTimeBucket.subtractSeconds(previousTime);
                if (lostDataSize > 1) {
                    this.fillLostData(previousTime, lostDataSize);
                }
                this.updateYAxesData(this.calculateDataIndex(formatTimeBucket), data[i].data);
                previousTime = formatTimeBucket;
            }
            this.previousTime = previousTime;
            this.chartObject.update();
        }
    }

    MetricChart.prototype.buildChartObject = function () {
        this.chartObject = new Chart(this.chartContext, this.chartConfig(generateChartLabels(FormatDate(startTime).timestamp, 300)));
    };

    MetricChart.prototype.calculateDataIndex = function (timeBucket) {
        var index = timeBucket.subtractSeconds(this.chartStartTime);
        if (index > 299) {
            this.updateXAxesDisplayTime(index - 299);
            this.updateChartStartTime(index - 299);
            return 299;
        } else {
            return index;
        }
    };

    MetricChart.prototype.alignXAxes = function (timestamp) {
        if (this.previousTime.timestamp == timestamp) {
            return;
        }

        var count = FormatDate(timestamp).subtractSeconds(this.previousTime);
        this.fillLostData(this.previousTime, count);
    };

    MetricChart.prototype.updateChartStartTime = function (updateCount) {
        this.chartStartTime = this.chartStartTime.addSeconds(updateCount);
    };

    MetricChart.prototype.updateXAxesDisplayTime = function (updateCount) {
        var startTime = this.chartStartTime.timestamp;
        for (var i = 0; i < updateCount; i++) {
            this.chartObject.data.labels.shift();
            this.chartObject.data.labels.push(moment(startTime).add(i + 300, "seconds").format("HH:mm:ss"))
        }
    };


    MetricChart.prototype.redrawChart = function (startTime) {
        var formatDate = moment(startTime).format("YYYYMMDDHHmmss");
        this.chartObject.destroy();
        this.chartObject = new Chart(this.chartContext, this.chartConfig(generateChartLabels(startTime, 300)));
        this.chartStartTime = FormatDate(formatDate);
        this.previousTime = undefined;
    };

    MetricChart.prototype.supplyLostData = function (timestamp) {
        if (this.previousTime.timestamp == timestamp) {
            return;
        }
        var count = this.previousTime.subtract(timestamp);
        this.fillLostData(this.chartStartTime, count);

        this.previousTime = FormatDate(timestamp);
    };

    MetricChart.prototype.destroy = function () {
        this.chartObject.destroy();
        this.chartObject = undefined;
    };

    MetricChart.prototype.getChartStartTime = function () {
        return this.chartStartTime.timestamp;
    };

    function FormatDate(timestamp) {
        return {
            timestamp: parseInt(moment(timestamp, "YYYYMMDDHHmmss").format("x")),
            subtract: function (timestamp) {
                return (this.timestamp - timestamp ) / 1000;
            },
            subtractSeconds: function (formatDate) {
                return (this.timestamp - formatDate.timestamp) / 1000;
            },
            addSeconds: function (seconds) {
                return FormatDate(moment(this.timestamp).add(seconds, "seconds").format("YYYYMMDDHHmmss"));
            },
            toDisplayTime: function () {
                return moment(this.timestamp).format("YYYYMMDDHHmmss");
            }
        }
    }

    function generateChartLabels(startTime, maxSize) {
        var labels = [];
        for (var i = 0; i < maxSize; i++) {
            labels.push(moment(startTime).add(i, "seconds").format("HH:mm:ss"));
        }
        return labels;
    }

    function createMetricChart(ChartProtoType, chartContext, startTime) {
        function F() {
        }

        F.prototype = MetricChart.prototype;
        ChartProtoType.prototype = new F();
        ChartProtoType.prototype.constructor = ChartProtoType;

        var chart = new ChartProtoType(chartContext, startTime);
        chart.buildChartObject();

        return chart;
    }

    return {
        createMetricChart: createMetricChart,
        MetricChart: MetricChart,
        FormatDate: FormatDate
    }
});

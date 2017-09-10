define(['chartJs', 'moment'], function (Chart, moment) {
    function MetricChart(chartContext, startTime) {
        this.chartStartTime = startTime;
        this.chartContext = chartContext;
        this.previousTime = undefined;
        this.chartObject = function (labels) {
        };
        this.fillData = function (data) {
        }
    };

    MetricChart.prototype.buildChartObject = function (startTime) {
        this.chartObject = new Chart(this.chartContext, this.chartConfig(generateChartLabels(startTime, 300)));
    };

    MetricChart.prototype.toUnixTimestamp = function (startTime) {
        return parseInt(moment(startTime, "YYYYMMDDHHmmss").format("x"));
    };

    MetricChart.prototype.updateXAxes = function () {
        this.chartObject.data.labels.shift();
        this.chartObject.data.labels.push(moment(this.chartStartTime, "YYYYMMDDHHmmss").add(301, "seconds").format("HH:mm:ss"));
    }

    MetricChart.prototype.updateChartStartTime = function(index){
        if (index > 299){
            this.chartStartTime = moment(this.chartStartTime, "YYYYMMDDHHmmss").add(1, "seconds").format("YYYYMMDDHHmmss");
        }
    }

    MetricChart.prototype.redrawChart = function (startTime) {
        this.chartObject.destroy();
        this.chartObject = new Chart(this.chartContext, this.chartConfig(generateChartLabels(startTime, 300)));
        this.chartStartTime = startTime;
        this.previousTime = undefined;
    };

    MetricChart.prototype.destroy = function () {
        this.chartObject.destroy();
        this.chartObject = undefined;
    };


    function generateChartLabels(startTime, maxSize) {
        var labels = [];
        for (var i = 0; i < maxSize; i++) {
            labels.push(moment(startTime, "YYYYMMDDHHmmss").add(i, "seconds").format("HH:mm:ss"));
        }
        return labels;
    };

    function createMetricChart(ChartProtoType, chartContext, startTime) {
        function F() {
        }

        F.prototype = MetricChart.prototype;
        ChartProtoType.prototype = new F();
        ChartProtoType.prototype.constructor = ChartProtoType;

        var chart = new ChartProtoType(chartContext, startTime);
        chart.buildChartObject(startTime);

        return chart;
    };

    return {
        createMetricChart: createMetricChart,
        MetricChart: MetricChart
    }
});

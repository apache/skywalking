requirejs(['/main.js'], function (main) {
    requirejs(['rangeSlider', 'moment', 'daterangepicker', 'machineInfo', 'metricCharts'],
        function (rangeSlider, moment, dataRangePicker, machineInfo, metricCharts) {

            machineInfo.loadMachineInfo();
            metricCharts.drawMetricSelector().loadMetricCharts();


            $('#startTimeInput').daterangepicker({
                singleDatePicker: true,
                timePicker: true,
                startDate: moment(),
                timePickerIncrement: 1,
                timePicker24Hour: true,
                local: {
                    format: 'YYYY-MM-DD H:mm'
                }
            });

            $("#startTimeInput").val(moment().format('YYYY-MM-DD H:mm'));
            $('#startTimeInput').on('apply.daterangepicker', function (ev, picker) {
                $(this).val(picker.startDate.format('YYYY-MM-DD H:mm'));
                $(this).attr("timestamp", picker.startDate);
                metricCharts.updateMetricCharts(parseInt($(this).attr("timestamp")));
            });

            $("#autoUpdate").change(function () {
                if ($("#autoUpdate").prop('checked')) {
                    $("#startTimeInput").prop("disabled", true);
                    $("#startTimeInput").val("");
                    $("#startTimeInput").attr("timestamp", "")
                    window.autoUpdateMetricCharts();
                } else {
                    window.stopAutoUpdateMetricCharts();
                }
            });


            window.autoUpdateMetricCharts = function () {
                metricCharts.autoUpdateMetricCharts();
                window.updateChartTimeTask = setTimeout("window.autoUpdateMetricCharts()", 1000);
            }

            window.stopAutoUpdateMetricCharts = function () {
                $("#startTimeInput").prop('disabled', false);
                clearTimeout(window.updateChartTimeTask);
                $("#autoUpdate").prop("checked", false);
            }

        });
});

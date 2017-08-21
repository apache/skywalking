requirejs(['/main.js'], function (main) {
    requirejs(['rangeSlider', 'moment', 'daterangepicker', 'machineInfo', 'metricCharts'],
        function (rangeSlider, moment, dataRangePicker, machineInfo, metricCharts) {
            var instanceId = window.instanceId;
            var startTime = window.startTime;

            machineInfo.loadMachineInfo(instanceId);
            metricCharts.drawMetricSelector().loadMetricCharts(1, startTime);


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

            $("#startTimeInput").val(moment(startTime).format('YYYY-MM-DD H:mm'));
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
                    window.updateChartTimeTask = setInterval(function () {
                        metricCharts.autoUpdateMetricCharts();
                    }, 1000);
                } else {
                    window.stopAutoUpdateMetricCharts();
                }
            });


            window.stopAutoUpdateMetricCharts = function () {
                $("#startTimeInput").prop('disabled', false);
                clearInterval(window.updateChartTimeTask);
                $("#autoUpdate").prop("checked", false);
            }

        });
});

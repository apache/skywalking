requirejs(['/main.js'], function (main) {
    requirejs(['jquery', 'applications', 'appInstance', 'healthTimeAxis', 'moment', 'responseTimeCondition'],
        function ($, applications, appInstance, timeAxis, moment, responseTimeCondition) {
            var config = {
                serverTimestamp: undefined,
                differentTime: undefined,
            };
            $.ajax({
                url: "/syncTime",
                async: false,
                timeout: 1000,
                success: function (data) {
                    config.serverTimestamp = data.timestamp;
                    config.differentTime = moment() - data.timestamp;
                }
            });

            timeAxis.draw(config.serverTimestamp, config.differentTime).registryTimerHandle(function (timestamp, queryParameters) {
                appInstance.loadInstancesData(timestamp, queryParameters.applicationIds, queryParameters.responseTime)
            });

            applications.draw().loadApplications(config.serverTimestamp).registryAppIdOperationHandler(function (applicationId, isRemove) {
                if (isRemove) {
                    timeAxis.removeAppId(applicationId);
                } else {
                    timeAxis.addAppId(applicationId);
                }
            }).startTimeTask();

            appInstance.drawCanvas();
            responseTimeCondition.draw().registryResponseTimeHandler(function (responseTime) {
                timeAxis.addResponseTimeQueryParam(responseTime);
            });
        });
});

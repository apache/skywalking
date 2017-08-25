define(['jquery', 'vue', 'text!applicationsHtml'], function ($, Vue, segmentHtml) {
    var apps = {
        applist: [],
        handler: undefined,
        timerTask: undefined
    };
    var vue;

    function draw() {
        $("#applications").html(segmentHtml);
        vue = new Vue({
            el: '#applicationUl',
            data: apps,
            methods: {
                operateQueryApplicationId: function (applicationId, event) {
                    if ($(event.target).is(".operation-selected")) {
                        $(event.target).removeClass("operation-selected");
                        apps.handler(applicationId, true);
                    } else {
                        $(event.target).addClass("operation-selected");
                        apps.handler(applicationId, false);
                    }
                }
            }
        });

        return this;
    }

    function loadApplications(timestamp) {
        $.getJSON("/applications?timestamp=" + timestamp, function (data) {
            apps.applist = data.applicationList;
        });

        return this;
    }

    function registryAppIdOperationHandler(handler) {
        apps.handler = handler;
        return this;
    }

    function startTimeTask() {
        apps.timerTask = setInterval(function () {
            loadApplications($("#timeAxis").val());
        }, 15000);
    }

    return {
        loadApplications: loadApplications,
        draw: draw,
        startTimeTask: startTimeTask,
        registryAppIdOperationHandler: registryAppIdOperationHandler
    }
});

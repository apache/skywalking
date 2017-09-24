requirejs(['/main.js'], function () {
    requirejs(['jquery', 'vue', 'head', 'timeAxis', "applicationList", "entryServiceList", "serviceTreeList"], function ($, Vue, head, timeAxis, applicationList, entryServiceList, serviceTreeList) {
        var vueData = {
            list: [],
            applicationId: 0,
            entryServiceName: "",
            handler: undefined
        };
        var vue;

        timeAxis.load().registryTimeChangedHandler(function (timeBucketType, startTime, endTime) {
            console.log("time changed, start time: " + startTime + ", end time: " + endTime);
            load(timeBucketType, startTime, endTime);
            vue = new Vue({
                el: '#applicationListVueDiv',
                data: vueData,
                methods: {
                    goClick: function () {
                        entryServiceList.load(vueData.applicationId, vueData.entryServiceName, startTime, endTime).registryIdItemClickHandler(function (entryServiceId) {
                            console.log("entryServiceId: " + entryServiceId);
                            serviceTreeList.loadByEntryServiceId(entryServiceId, startTime, endTime).render("serviceTreeListDiv");
                        }).registryNameItemClickHandler(function (entryApplicationId, entryServiceName) {
                            console.log("entryApplicationId: " + entryApplicationId + ", entryServiceName: " + entryServiceName);
                            serviceTreeList.loadByEntryServiceName(entryApplicationId, entryServiceName, startTime, endTime).render("serviceTreeListDiv");
                        }).render("entryServiceListDiv");
                    }
                }
            });
        }).render("timeAxisDiv");

        function load(timeBucketType, startTime, endTime) {
            $.ajaxSettings.async = false;
            $.getJSON("/applications?timeBucketType=minute&startTime=" + startTime + "&endTime=" + endTime, function (data) {
                console.log(data);
                vueData.list.push({applicationId: 0, applicationCode: "All"});
                for (var i = 0; i < data.length; i++) {
                    vueData.list.push({applicationId: data[i].applicationId, applicationCode: data[i].applicationCode});
                }
            });
            return this;
        }
    });
});

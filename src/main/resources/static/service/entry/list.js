define(['jquery', 'vue', 'text!entryServiceListHtml'], function ($, Vue, entryServiceListHtml) {
    var vueData = {
        list: [],
        size: 10,
        pageSize: 0,
        currentPage: 0,
        hasForward: true,
        backward: true,
        applicationId: 0,
        entryServiceName: "",
        startTime: 0,
        endTime: 0,
        handler: undefined
    };
    var vue;

    function render(renderToDivId) {
        $("#" + renderToDivId).html(entryServiceListHtml);
        vue = new Vue({
            el: '#entryServiceListVueDiv',
            data: vueData,
            methods: {
                itemClick: function (entryServiceId, event) {
                    if ($(event.target).is(".operation-selected")) {
                        $(event.target).removeClass("operation-selected");
                        vueData.handler(entryServiceId);
                    } else {
                        $(event.target).addClass("operation-selected");
                        vueData.handler(entryServiceId);
                    }
                },
                gotoPage: function (pageNum) {
                    vueData.currentPage = pageNum;
                    load(vueData.applicationId, vueData.entryServiceName, vueData.startTime, vueData.endTime);
                }
            }
        });

        return this;
    }

    function load(applicationId, entryServiceName, startTime, endTime) {
        var path = "/service/entry/entryServiceList?timeBucketType=minute";
        path = path + "&applicationId=" + applicationId;
        path = path + "&entryServiceName=" + entryServiceName;
        path = path + "&startTime=" + startTime;
        path = path + "&endTime=" + endTime;
        path = path + "&from=" + vueData.currentPage * vueData.size;
        path = path + "&size=" + vueData.size;

        vueData.applicationId = applicationId;
        vueData.entryServiceName = entryServiceName;
        vueData.startTime = startTime;
        vueData.endTime = endTime;

        $.getJSON(path, function (data) {
            console.log(data);
            vueData.list = data.array;
            vueData.pageSize = data.total % vueData.size;
            console.log("page size: " + vueData.pageSize);
        });

        if (vueData.currentPage == 0) {
            vueData.hasForward = false;
        } else {
            vueData.hasForward = true;
        }

        if (vueData.currentPage + 1 == vueData.pageSize) {
            vueData.backward = false;
        } else {
            vueData.backward = true;
        }
        return this;
    }

    function registryItemClickHandler(handler) {
        vueData.handler = handler;
        return this;
    }

    return {
        load: load,
        render: render,
        registryItemClickHandler: registryItemClickHandler
    }
});

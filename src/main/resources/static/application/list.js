define(['jquery', 'vue', 'text!applicationListHtml'], function ($, Vue, applicationListHtml) {
    var vueData = {
        list: [],
        handler: undefined
    };
    var vue;

    function render(renderToDivId) {
        $("#" + renderToDivId).html(applicationListHtml);
        vue = new Vue({
            el: '#applicationListVueDiv',
            data: vueData,
            methods: {
                itemClick: function (applicationId, event) {
                    if ($(event.target).is(".operation-selected")) {
                        $(event.target).removeClass("operation-selected");
                        vueData.handler(applicationId, true);
                    } else {
                        $(event.target).addClass("operation-selected");
                        vueData.handler(applicationId, false);
                    }
                }
            }
        });
        return this;
    }

    function load(startTime, endTime) {
        $.ajaxSettings.async = false;
        $.getJSON("/applications?timeBucketType=minute&startTime=" + startTime + "&endTime=" + endTime, function (data) {
            console.log(data);
            vueData.list = data;
        });
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

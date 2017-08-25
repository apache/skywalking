/**
 * @author pengys5
 */
define(["jquery", "text!alarmHtml", "echarts", "walden"], function ($, alarmHtml, echarts, walden) {
    function create(divId) {
        $("#" + divId).html(alarmHtml);
    }

    return {
        create: create
    }
});
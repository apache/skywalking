/**
 * @author pengys5
 */
requirejs.config({
    paths: {
        "jquery": "/webjars/jquery/2.2.4/jquery.min",
        "timers": "/public/js/timers",
        "text": "/webjars/requirejs-text/2.0.15/text",
        "bootstrap": "/webjars/bootstrap/3.3.6/js/bootstrap.min",
        "inspinia": "/public/js/inspinia",
        "metisMenu": "/webjars/metisMenu/2.5.2/dist/metisMenu.min",
        "slimscroll": "/webjars/jquery-slimscroll/1.3.6/jquery.slimscroll.min",
        "moment": "/webjars/momentjs/2.18.1/min/moment.min",
        "vis": "/webjars/vis/4.19.1/dist/vis.min",
        "dagDraw": "/dag/dagDraw",
        "nodeCanvas": "/public/js/node.canvas",
        "dagHtml": "/dag/dag.html",
        "timeAxisHtml": "/index/timeaxis.html",
        "timeAxis": "/index/timeaxis",
        "rangeSlider": "/webjars/ion.rangeSlider/2.1.4/js/ion.rangeSlider.min",
        "daterangepicker": "/webjars/bootstrap-daterangepicker/2.1.24/js/bootstrap-daterangepicker",
        "alarm": "/index/alarm",
        "echarts": "/webjars/echarts/3.3.1/dist/echarts.min",
        "walden": "/public/js/walden",
        "alarmHtml": "/index/alarm.html",
    }
});

require(["jquery", "dagDraw", "timeAxis", "alarm"], function ($, dagDraw, timeAxis, alarm) {
    dagDraw.startNetwork("dagViewDiv");
    timeAxis.create("timeAxisDiv");
    alarm.create("alarmDiv");
});
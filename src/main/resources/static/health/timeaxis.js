define(['jquery', 'rangeSlider', 'moment', 'text!instanceTimeAxisHtml'], function($, rangeSlider, moment, segmentHtml) {
  var config = {
    timeAxis: {
      timer: undefined,
      rangeSlider: undefined,
      dTime: undefined,
      start: function() {
        var that = this;
        that.dTime = +moment() - $("#timeAxis").val();
        that.timerTask = window.setInterval(function() {
          that.rangeSlider.update({
            max: +moment().subtract(10, "seconds").format("x"),
            from: +moment().subtract(that.dTime).format("x")
          });
        }, 1000);
      },
      stop: function() {
        if (this.timerTask != undefined) {
          window.clearInterval(this.timerTask);
        }
      }
    }
  }

  function draw() {
    $("#timeAxisDiv").html(segmentHtml);
    config.timeAxis.rangeSlider = initRangeSlider();
    bindButtonEvent();
    config.timeAxis.start();
  }

  function initRangeSlider() {
    $("#timeAxis").ionRangeSlider({
      min: +moment().subtract(1, "hours").format("x"),
      max: +moment().subtract(10, "seconds").format("x"),
      from: +moment().subtract(10, "seconds").format("x"),
      grid: true,
      step: 60000,
      prettify: function(num) {
        var m = moment(num, "x").locale("zh-cn");
        return m.format("MM/DD H:mm:ss");
      }
    });

    return $("#timeAxis").data("ionRangeSlider");
  }

  function bindButtonEvent() {
    $("#timeAxisButton").click(function() {
      if ($(this).is(".fa-play")) {
        $(this).removeClass("fa-play").addClass("fa-pause");
        config.timeAxis.start();
      } else {
        $(this).removeClass("fa-pause").addClass("fa-play");
        config.timeAxis.stop();
      }
    });
  }

  return {
    draw: draw
  }
});

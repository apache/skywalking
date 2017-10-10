/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

define(['jquery', 'chartJs'], function($, Chart) {
  Chart.defaults.global.tooltips.enabled = false;
  Chart.pluginService.register({
    beforeDraw: function(chart) {
      if (chart.config.options.elements.center) {
        var ctx = chart.chart.ctx;
        var centerConfig = chart.config.options.elements.center;
        var fontStyle = centerConfig.fontStyle || 'Arial';
        var txt = centerConfig.text;
        var color = centerConfig.color || '#000';
        var sidePadding = centerConfig.sidePadding || 20;
        var sidePaddingCalculated = (sidePadding / 100) * (chart.innerRadius * 2)
        ctx.font = "15px " + fontStyle;
        var ks = txt.split('\n');
        var newFontSize = 7;
        var elementHeight = (chart.innerRadius * 2);
        var fontSizeToUse = 9;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        var centerX = ((chart.chartArea.left + chart.chartArea.right) / 2.0);
        var centerY = ((chart.chartArea.top + chart.chartArea.bottom) / 3.3);
        ctx.font = fontSizeToUse + "px " + fontStyle;
        ctx.fillStyle = color;
        var currentCenterY = centerY;
        $.each(ks, function(index) {
          ctx.fillText(ks[index], centerX, currentCenterY);
          currentCenterY += fontSizeToUse + 5;
        })
        ctx.font = 10 + "px " + fontStyle;
        if (!chart.config.options.elements.left.hide) {
          ctx.fillText(chart.config.options.elements.left.text, 10, 6);
        }
        if (!chart.config.options.elements.right.hide) {
          ctx.fillText(chart.config.options.elements.right.text, 74, 6);
        }
      }
    }
  });

  var chartColors = [{
    // red
    color: '#EF5352'
  }, {
    // orange
    color: '#F8AC59'

  }, {
    // yellow
    color: '#1C84C6'
  }, {
    // blue
    color: '#23C6C8'
  }, {
    // gray
    color: '#C9CBCF'
  }];

  function createCanvas(contextText, hideLeft, leftText, hideRight, rightText, color) {
    return {
      type: 'doughnut',
      data: {
        datasets: [{
          data: [100],
          backgroundColor: [color]
        }],
      },
      options: {
        responsive: false,
        maintainAspectRatio: false,
        legend: {
          display: false,
        },
        elements: {
          center: {
            text: contextText,
            color: '#1c84c6',
          },
          left: {
            hide: hideLeft,
            text: leftText,
          },
          right: {
            hide: hideRight,
            text: rightText,
          },

        },
        cutoutPercentage: 90,
        rotation: 3.30
      }
    };
  }

  function createCanvasConfig(instance) {
    if (instance.status == 0) {
      return createCanvas("#" + instance.id + "\n " + instance.tps + " t/s \navg " + instance.avg + "ms",
        false, instance.ygc, false, instance.ogc, chartColors[instance.healthLevel].color);
    } else {
      return createCanvas("#" + instance.id + "\n Unknown ",
        true, undefined, true, undefined, chartColors[4].color);
    }

  }

  function updateCanvas(chartObject, instance) {
    if (instance.status == 0) {
      var centerText = "#" + instance.id + "\n " + instance.tps + " t/s \navg " + instance.avg + "ms";
      updateCanvasChart(chartObject, centerText, chartColors[instance.healthLevel].color,
        false, instance.ygc, false, instance.ogc);
    } else {
      updateCanvasChart(chartObject, "#" + instance.id + "\n Unknown ", chartColors[4].color,
        true, undefined, true, undefined);
    }
  }

  function updateCanvasChart(chartObject, centerText, color, hideLeft, leftText, hideRight, rightText) {
    chartObject.options.elements.center.text = centerText;
    chartObject.data.datasets[0].backgroundColor = color;
    chartObject.options.elements.left.hide = hideLeft;
    chartObject.options.elements.left.text = leftText;
    chartObject.options.elements.right.hide = hideRight;
    chartObject.options.elements.right.text = rightText;
    chartObject.update();
  }

  return {
    createCanvasConfig: createCanvasConfig,
    updateCanvas: updateCanvas,
  }
});

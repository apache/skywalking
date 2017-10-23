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

/**
 * @author peng-yongsheng
 */
define(["jquery", "vis", "text!dagHtml", "moment", "nodeCanvas"], function ($, vis, dagHtml, moment, nodeCanvas) {
    var _containerDiv = "traceDagDiv";
    var _network = null;
    var _data = {
        nodes: new vis.DataSet(),
        edges: new vis.DataSet()
    };

    var _images = {};
    var _options = {
        nodes: {
            borderWidth: 0,
            size: 30,
            color: {
                background: '#ffffff'
            },
            shapeProperties: {
                useImageSize: false
            }
        },
        edges: {
            color: 'grey',
            arrows: {
                to: {enabled: true, scaleFactor: 0.5, type: 'arrow'},
                from: {enabled: true, scaleFactor: 0, type: 'circle'}
            },
            smooth: false,
            arrowStrikethrough: false
        },
        physics: {
            enabled: true,
            barnesHut: {gravitationalConstant: -30000},
            stabilization: {iterations: 2500}
        }
    };

    function _clear() {
        _images = {};
        _data.nodes.clear();
        _data.edges.clear();
    }

    function startNetwork(divId) {
        $("#" + divId).html(dagHtml);
        var container = document.getElementById(_containerDiv);
        _network = new vis.Network(container, _data, _options);
        _resize();
        return this;
    }

    function _resize() {
        var width = $("#dagViewDiv").width();
        height = $(window).height() - 110;
        console.log("width: " + width);
        console.log("height: " + height);
        $("#" + _containerDiv).width(width - 10).height(height);
        _network.redraw();
    }

    function _addEdge(nodeRef) {
        console.log(nodeRef.from + " - " + nodeRef.to + " : " + nodeRef.resSum);
        _data.edges.add({from: nodeRef.from, to: nodeRef.to, label: nodeRef.resSum});
    }

    function _addNode(node) {
        console.log(node.id + " - " + node.label + " - " + node.title + " - " + node.instNum);
        _data.nodes.add({
            id: node.id,
            label: node.label,
            title: node.title,
            image: nodeCanvas.createNode(node.imageObj, node.real, node.instNum),
            shape: 'image',
            borderWidth: 4
        })
    }

    function load(timeBucketType, startTimeStr, endTimeStr) {
        console.log("timeBucketType: " + timeBucketType + ", startTimeStr: " + startTimeStr + ", endTimeStr: " + endTimeStr);
        $.getJSON("dagNodesLoad", {startTime: startTimeStr, endTime: endTimeStr}, function (data) {
            _clear();
            _preLoadImages(data);
            _resize();
        });
    }

    function _preLoadImages(data) {
        for (var i = 0; i < data.nodes.length; i++) {
            var nodeImage = new Image();
            nodeImage.src = data.nodes[i].image;
            nodeImage.id = i;

            nodeImage.onload = function () {
                _images[this.id] = this;

                if (_isFinish(data.nodes.length)) {
                    _pushDagData(data);
                    _network.stabilize();
                }
            }
        }
    }

    function _isFinish(nodeLength) {
        var isFinish = true;
        for (var i = 0; i < nodeLength; i++) {
            if (_images[i]) {
                isFinish = isFinish && true;
            } else {
                isFinish = isFinish && false;
            }
        }
        return isFinish;
    }

    function _pushDagData(data) {
        for (var i in data.nodes) {
            data.nodes[i].imageObj = _images[i];
            _addNode(data.nodes[i]);
        }

        for (var i in data.nodeRefs) {
            _addEdge(data.nodeRefs[i]);
        }
    }

    $(window).resize(function () {
        _resize();
    });

    return {
        startNetwork: startNetwork,
        load: load
    }
});
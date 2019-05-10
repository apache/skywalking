/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* eslint-disable */
import * as d3 from 'd3';
import d3tip from 'd3-tip';
export default class TraceMap {
  constructor(el,row, showSpanModal,smax,smin,cmax,cmin) {
    this.type = {
      MQ: '#bf99f8',
      Http: '#72a5fd',
      Database: '#ff6732',
      Unknown: '#ffc107',
      Cache: '#00bcd4',
      RPCFramework: '#ee4395',
    };
    this.smax = smax;
    this.smin = smin;
    this.cmax = cmax;
    this.cmin = cmin;
    this.showSpanModal = showSpanModal;
    this.el = el;
    this.i = 0;
    this.j = 0;
    this.width = el.clientWidth;
    this.height = row.length * 80 + 20;
    this.body = d3
      .select(this.el)
      .style('height', this.height + 'px')
      .append('svg')
      .attr('width', this.width)
      .attr('height', this.height);
    this.tip = d3tip()
      .attr('class', 'd3-tip')
      .offset([10, 0])
      .html(d => d.data.label);
    this.timeTip = d3tip()
      .attr('class', 'd3-tip')
      .offset([-8, 0])
      .html(d => d.data.label);
    this.body.call(this.timeTip);
    this.body.call(this.tip);
    this.treemap = d3.tree().size([this.height * 0.8, this.width]);
  }
  init(data, row) {
    this.row = row;
    this.data = data;
    this.min = d3.min(this.row.map(i => i.startTime));
    this.max = d3.max(this.row.map(i => i.endTime - this.min));
    this.list = Array.from(new Set(this.row.map(i => i.serviceCode)));
    this.sequentialScale = d3
      .scaleSequential()
      .domain([0, this.list.length])
      .interpolator(d3.interpolateCool);
    this.svg = this.body.append('g').attr('transform', d => `translate(0, ${this.row.length * 14 + 20})`).append('g');
    this.timeGroup = this.body.append('g').attr('class','timeGroup').attr('transform', d => 'translate(5,30)');
    this.body.call(this.getZoomBehavior(this.svg));
    this.root = d3.hierarchy(this.data, d => d.children);
    this.root.x0 = this.height / 2;
    this.root.y0 = 0;
  }
  resize() {
    this.body
    .select('.xAxis')
    .remove();
    this.body
      .select('.timeGroup')
      .remove();
    this.width = this.el.clientWidth;
    this.body.attr('width', this.width);
    this.xScale = d3
    .scaleLinear()
    .domain([0, this.max])
    .range([0, this.width - 10]);
    this.xAxis = d3.axisTop(this.xScale).tickFormat(d => {
      if (d === 0) return 0;
      if (d >= 1000) return d / 1000 + 's';
      return d + ' ms';
    });
    this.body
    .append('g')
    .attr('class', 'xAxis')
    .attr('transform', `translate(5,20)`)
    .call(this.xAxis);
    this.timeGroup = this.body.append('g').attr('class','timeGroup').attr('transform', d => 'translate(5,30)');
    this.updatexAxis(this.root);
  }
  draw() {
    this.xScale = d3
      .scaleLinear()
      .domain([0, this.max])
      .range([0, this.width - 10]);

    this.xAxis = d3.axisTop(this.xScale).tickFormat(d => {
      if (d === 0) return 0;
      if (d >= 1000) return d / 1000 + 's';
      return d + ' ms';
    });
    this.body
    .append('g')
    .attr('class', 'xAxis')
    .attr('transform', `translate(5,20)`)
    .call(this.xAxis);
    this.updatexAxis(this.root);
    this.update(this.root);
  }
  update(source) {
    const that = this;
    const links = this.nodes.slice(1);
    const node = this.svg.selectAll('g.node').data(this.nodes, d => {
      return d.id|| (d.id = ++this.i);
    });
    // node
    const nodeEnter = node
      .enter()
      .append('g')
      .attr('class', 'node')
      .attr('transform', `translate(${source.y0},${source.x0})`)
      .on('mouseover', function(d, i) {
        that.tip.show(d, this);
        const _node = that.timeUpdate._groups[0].filter(group => group.__data__.id === (i+1));
        if(_node.length){
          that.timeTip.show(d, _node[0].children[1]);
        }
      })
      .on('mouseout', function(d, i) {
        that.tip.hide(d, this);
        const _node = that.timeUpdate._groups[0].filter(group => group.__data__.id === (i+1));
        if(_node.length){
          that.timeTip.hide(d, _node[0].children[1]);
        }
      })
      .on('click', (d, i) => {
        this.showSpanModal(
          d.data,
          { width: '100%', top: -10, left: '0' },
          d3.select(nodeEnter._groups[0][i]).append('rect')
        );
        d3.event.stopPropagation();
      });
    const nodeSelfDur = nodeEnter
      .append('g')
      .style('opacity', 0)
      .attr('class','trace-tree-node-selfdur')
      .attr('transform', 'translate(0,-39)')
    nodeSelfDur
      .append('rect')
      .attr('width', 65)
      .attr('height', 16)
      .attr('rx', 3)
      .attr('ry', 3)
      .attr('fill', '#333')
    nodeSelfDur
      .append('text')
      .attr('dx', 5)
      .attr('dy', 11)
      .text(d=> {
        return d.data.dur + ' ms'
      })
      .attr('fill', '#fff');
    const nodeSelfChild = nodeEnter
      .append('g')
      .style('opacity', 0)
      .attr('class','trace-tree-node-selfchild')
      .attr('transform', 'translate(0,-39)')
      nodeSelfChild
      .append('rect')
      .attr('width', 110)
      .attr('height', 16)
      .attr('rx', 3)
      .attr('ry', 3)
      .attr('fill', '#333')
      nodeSelfChild
      .append('text')
      .attr('dx', 5)
      .attr('dy', 11)
      .text(d=> `children: ${d.data.childrenLength}`)
      .attr('fill', '#fff')
    nodeEnter
      .append('rect')
      .attr('class', 'block')
      .attr('x', '0')
      .attr('y', '-20')
      .attr('fill', d => (d.data.isError ? '#ff57221a' : '#f7f7f7'))
      .attr('stroke', d => (d.data.isError ? '#ff5722aa' : '#e4e4e4'));
    nodeEnter
      .append('rect')
      .attr('class', 'content')
      .attr('stroke', d => d.data.isError ? '#ff5722aa' : '#e4e4e4');
    nodeEnter
      .append('rect')
      .attr('class', 'service')
      .attr('x', '-0.5')
      .attr('y', '-20.5')
      .style('fill', d => `${this.sequentialScale(this.list.indexOf(d.data.serviceCode))}`);
    nodeEnter
      .append('text')
      .attr('dy', 13)
      .attr('dx', 10)
      .attr('stroke', '#333')
      .attr('text-anchor', 'start')
      .text(d => d.data.label.length > 19 ? d.data.label.slice(0, 19) : d.data.label);

    nodeEnter
      .append('text')
      .attr('dy', -7)
      .attr('dx', 12)
      .attr('text-anchor', 'start')
      .attr('fill', d => this.type[d.data.layer])
      .attr('stroke', d => this.type[d.data.layer])
      .text(d => {
        if(d.data.type == 'Local' && d.data.layer == 'Unknown'){
          return 'Local';
        }
        return d.data.layer;
      });

    nodeEnter
      .append('text')
      .attr('dy', -7)
      .attr('x', 95)
      .attr('stroke', '#333')
      .attr('text-anchor', 'start')
      .text(d => d.data.endTime ? d.data.endTime - d.data.startTime + ' ms' : d.data.traceId);

    nodeEnter
      .append('circle')
      .attr('class', 'node')
      .attr('r', 4)
      .attr('cx', '158')
      .style('fill', d => d._children ? '#8543e0aa' : '#fff')
      .on('click', click);

    this.nodeUpdate = nodeEnter.merge(node);

    this.nodeUpdate
      .transition()
      .duration(600)
      .attr('transform', function(d) {
        return 'translate(' + d.y + ',' + d.x + ')';
      });

    this.nodeUpdate
      .select('circle.node')
      .attr('r', 4)
      .attr('cx', '158')
      .style('fill', d => d._children ? '#8543e0aa' : '#fff')
      .attr('cursor', 'pointer');

    const nodeExit = node
      .exit()
      .transition()
      .duration(600)
      .attr('transform', function(d) {
        return 'translate(' + source.y + ',' + source.x + ')';
      })
      .remove();

    // link
    const link = this.svg.selectAll('path.link').data(links, d => d.id);

    const linkEnter = link
      .enter()
      .insert('path', 'g')
      .attr('class', 'link')
      .attr('d', function(d) {
        const o = { x: source.x0, y: source.y0 };
        return diagonal(o, o);
      });

    const linkUpdate = linkEnter.merge(link);

    linkUpdate
      .transition()
      .duration(600)
      .attr('d', function(d) {
        return diagonal(d, d.parent);
      });

    link
      .exit()
      .transition()
      .duration(600)
      .attr('d', function(d) {
        var o = { x: source.x, y: source.y };
        return diagonal(o, o);
      })
      .remove();

    function diagonal(s, d) {
      return `M ${s.y} ${s.x}
      C ${s.y - 30} ${s.x}, ${d.y + 188} ${d.x},
      ${d.y + 158} ${d.x}`;
    }
    function click(d, i) {
      // that.tip.hide(d, this);
      // that.timeTip.hide(d, that.timeUpdate._groups[0][i]);
      if (d.children) {
        d._children = d.children;
        d.children = null;
      } else {
        d.children = d._children;
        d._children = null;
      }
      that.updatexAxis(d);
      that.update(d);
      d3.event.stopPropagation();
    }
  }
  updatexAxis(source) {
    // time
    const that = this;
    this.nodes = this.treemap(this.root).descendants();
    let index = -1;
    this.nodes.forEach(function(d) {
      d.y = d.depth * 200;
      d.timeX = ++index * 12;
      d.x0 = d.x;
      d.y0 = d.y;
    });
    const timeNode = this.timeGroup.selectAll('g.time').data(this.nodes, d => {
      return d.id|| (d.id = ++this.j);
    });
    this.timeNode = timeNode;
    const timeEnter = timeNode
      .enter()
      .append('g')
      .attr('class', 'time')
      .attr('transform', d => `translate(0,${d.timeX})`)
      .on('mouseover', function(d, i) {
        that.timeTip.show(d, this);
        const _node = that.nodeUpdate._groups[0].filter(group => group.__data__.id === (i+1));
        if(_node.length){
          that.tip.show(d, _node[0]);
        }
      })
      .on('mouseout', function(d, i) {
        that.timeTip.hide(d, this);
        const _node = that.nodeUpdate._groups[0].filter(group => group.__data__.id === (i+1));
        if(_node.length){
          that.tip.hide(d, _node[0]);
        }
      })
      .on('click', (d, i) => {
        this.showSpanModal(
          d.data,
          { width: '100%', top: -10, left: '0' },
          d3.select(that.timeUpdate._groups[0][i]).append('rect')
        );
        d3.event.stopPropagation();
      });
    timeEnter
      .append('rect')
      .attr('height', 10)
      .attr('width', this.width)
      .attr('y', -4)
      .attr('class', 'time-bg');
    timeEnter
      .append('rect')
      .attr('class', 'time-inner')
      .attr('height', 8)
      .attr('width', d => {
        if (!d.data.endTime || !d.data.startTime) return 0;
        return this.xScale(d.data.endTime - d.data.startTime) + 1;
      })
      .attr('rx', 2)
      .attr('ry', 2)
      .attr(
        'x',
        d => (!d.data.endTime || !d.data.startTime ? 0 : this.xScale(d.data.startTime - this.min))
      )
      .attr('y', -3)
      .style('fill', d => `${this.sequentialScale(this.list.indexOf(d.data.serviceCode))}`);
    timeEnter
      .append('rect')
      .style('opacity',0)
      .attr('class', 'time-inner-duration')
      .attr('height', 8)
      .attr('width', d => {
        if (!d.data.dur) return 1;
        return this.xScale(d.data.dur) + 1;
      })
      .attr('rx', 2)
      .attr('ry', 2)
      .attr(
        'x',
        d => (!d.data.endTime || !d.data.startTime ? 0 : this.xScale(d.data.startTime - this.min))
      )
      .attr('y', -3)
      .style('fill', d => `${this.sequentialScale(this.list.indexOf(d.data.serviceCode))}`);

    this.timeUpdate = timeEnter.merge(timeNode);
    this.timeUpdate
      .transition()
      .duration(600)
      .attr('transform', d => `translate(0,${d.timeX})`);

    const timeExit = timeNode
      .exit()
      .transition()
      .duration(600)
      .attr('transform', 'translate(0 ,10)')
      .remove();
  }
  setDefault() {
    d3.selectAll('.time-inner').style('opacity', 1);
    d3.selectAll('.time-inner-duration').style('opacity', 0);
    d3.selectAll('.trace-tree-node-selfdur').style('opacity', 0);
    d3.selectAll('.trace-tree-node-selfchild').style('opacity', 0);
    this.nodeUpdate._groups[0].forEach(i => {
      d3.select(i).style('opacity', 1);
    })
  }
  topChild() {
    d3.selectAll('.time-inner').style('opacity', 1);
    d3.selectAll('.time-inner-duration').style('opacity', 0);
    d3.selectAll('.trace-tree-node-selfdur').style('opacity', 0);
    d3.selectAll('.trace-tree-node-selfchild').style('opacity', 1);
    this.nodeUpdate._groups[0].forEach(i => {
      d3.select(i).style('opacity', .2);
      if(i.__data__.data.childrenLength >= this.cmin && i.__data__.data.childrenLength <= this.cmax){
        d3.select(i).style('opacity', 1);
      }
    })
  }
  topSlow() {
    d3.selectAll('.time-inner').style('opacity', 0);
    d3.selectAll('.time-inner-duration').style('opacity', 1);
    d3.selectAll('.trace-tree-node-selfchild').style('opacity', 0);
    d3.selectAll('.trace-tree-node-selfdur').style('opacity', 1);
    this.nodeUpdate._groups[0].forEach(i => {
      d3.select(i).style('opacity', .2);
      if(i.__data__.data.dur >= this.smin && i.__data__.data.dur <= this.smax){
        d3.select(i).style('opacity', 1);
      }
    })
  }
  getZoomBehavior(g) {
    return d3
      .zoom()
      .scaleExtent([0.3, 10])
      .on('zoom', () => {
        g.attr(
          'transform',
          `translate(${d3.event.transform.x},${d3.event.transform.y})scale(${d3.event.transform.k})`
        );
      });
  }
}

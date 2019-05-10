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

import React, { Component } from 'react';
import { Chart, Axis, Tooltip, Geom } from 'bizcharts';
import Debounce from 'lodash-decorators/debounce';
import Bind from 'lodash-decorators/bind';
import autoHeight from '../autoHeight';
import styles from '../index.less';

@autoHeight()
class Area extends Component {
  static defaultProps = {
    limitColor: 'rgb(255, 144, 24)',
    color: 'rgb(24, 144, 255)',
  };

  state = {
    autoHideXLabels: false,
  };

  componentDidMount() {
    window.addEventListener('resize', this.resize);
    this.resize();
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.resize);
  }

  handleRoot = n => {
    this.root = n;
  };

  handleRef = n => {
    this.node = n;
  };

  @Bind()
  @Debounce(200)
  resize() {
    if (!this.node) {
      return;
    }
    const canvasWidth = this.node.parentNode.clientWidth;
    const { data = [], autoLabel = true } = this.props;
    if (!autoLabel) {
      return;
    }
    const minWidth = data.length * 30;
    const { autoHideXLabels } = this.state;
    if (canvasWidth <= minWidth) {
      if (!autoHideXLabels) {
        this.setState({
          autoHideXLabels: true,
        });
      }
    } else if (autoHideXLabels) {
      this.setState({
        autoHideXLabels: false,
      });
    }
  }
  
  render() {
    const { height, title, forceFit = true, data, color, limitColor } = this.props;

    if (!data || data.length < 1) {
      return <span style={{ display: 'none' }} />;
    }

    const { autoHideXLabels } = this.state;

    const scale = {
      x: {
        type: 'cat',
        tickCount: 3,
        range: [0, 1],
      },
      y: {
        min: 0,
      },
    };
    const offset = Math.floor(data.length / 2);
    const xData = data.slice(0, offset);
    const yData = data.slice(offset).map((v, i) => ({
      ...v,
      y: v.y - xData[i].y > 0 ? parseFloat((v.y - xData[i].y).toFixed(2)) : 0,
    }));
    return (
      <div className={styles.chart} style={{ height }} ref={this.handleRoot}>
        <div ref={this.handleRef}>
          {title && <h4 style={{ marginBottom: 20 }}>{title}</h4>}
          <Chart
            scale={scale}
            height={title ? height - 41 : height}
            forceFit={forceFit}
            data={yData.concat(xData)}
            padding="auto"
          >
            <Axis
              name="x"
              title={false}
              label={autoHideXLabels ? false : {}}
              tickLine={autoHideXLabels ? false : {}}
            />
            <Axis name="y" min={0} />
            <Tooltip crosshairs={{ type: 'line' }} />
            <Geom type="areaStack" position="x*y" color={['type', [color, limitColor]]} />
            <Geom type="lineStack" position="x*y" size={2} color={['type', [color, limitColor]]} />
          </Chart>
        </div>
      </div>
    );
  }
}

export default Area;

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


import React from 'react';
import { Chart, Axis, Tooltip, Geom } from 'bizcharts';
import autoHeight from '../autoHeight';
import styles from '../index.less';

@autoHeight()
export default class MiniArea extends React.Component {
  render() {
    const {
      height,
      data = [],
      forceFit = true,
      color = 'rgba(24, 144, 255, 0.2)',
      borderColor = '#1089ff',
      scale = {},
      borderWidth = 2,
      line,
      xAxis,
      yAxis,
      animate = true,
    } = this.props;

    const padding = [36, 5, 30, 5];

    const scaleProps = {
      x: {
        type: 'cat',
        range: [0, 1],
        ...scale.x,
      },
      y: {
        min: 0,
        ...scale.y,
      },
    };

    const tooltip = [
      'x*y',
      (x, y) => ({
        name: x,
        value: y,
      }),
    ];

    const chartHeight = height + 54;

    return (
      <div className={styles.miniChart} style={{ height }}>
        <div className={styles.chartContent}>
          {height > 0 && (
            <Chart
              animate={animate}
              scale={scaleProps}
              height={chartHeight}
              forceFit={forceFit}
              data={data}
              padding={padding}
            >
              <Axis
                key="axis-x"
                name="x"
                label={false}
                line={false}
                tickLine={false}
                grid={false}
                {...xAxis}
              />
              <Axis
                key="axis-y"
                name="y"
                label={false}
                line={false}
                tickLine={false}
                grid={false}
                {...yAxis}
              />
              <Tooltip showTitle={false} crosshairs={false} />
              <Geom
                type="area"
                position="x*y"
                color={color}
                tooltip={tooltip}
                shape="smooth"
                style={{
                  fillOpacity: 1,
                }}
              />
              {line ? (
                <Geom
                  type="line"
                  position="x*y"
                  shape="smooth"
                  color={borderColor}
                  size={borderWidth}
                  tooltip={false}
                />
              ) : (
                <span style={{ display: 'none' }} />
              )}
            </Chart>
          )}
        </div>
      </div>
    );
  }
}

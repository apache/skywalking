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

import numeral from 'numeral';
import './g2';
import ChartCard from './ChartCard';
import Area from './Area';
import Bar from './Bar';
import Pie from './Pie';
import Line from './Line';
import MiniArea from './MiniArea';
import MiniBar from './MiniBar';
import Field from './Field';
import StackBar from './StackBar';
import Sankey from './Sankey';
import HeatMap from './HeatMap';
import EndpointDeps from './EndpointDeps';

const yuan = val => `&yen; ${numeral(val).format('0,0')}`;

const Charts = {
  yuan,
  Bar,
  Pie,
  Field,
  MiniBar,
  MiniArea,
  ChartCard,
  Line,
  Area,
  StackBar,
  Sankey,
  HeatMap,
  EndpointDeps,
};

export {
  Charts as default,
  yuan,
  Bar,
  Pie,
  Field,
  MiniBar,
  MiniArea,
  ChartCard,
  Line,
  Area,
  StackBar,
  Sankey,
  HeatMap,
  EndpointDeps,
};

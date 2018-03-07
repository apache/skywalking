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


export function axis({ display }, data, tranformFunc) {
  return display.range.map((v, i) =>
    (tranformFunc ? tranformFunc({ x: v, y: data[i] }) : { x: v, y: data[i] }));
}

export function generateDuration({ from, to }) {
  const start = from();
  const end = to();
  const mlist = getMeasureList();
  const lenght = mlist.length;
  const { measureType, step, format, displayFormat } = mlist
    .find((_, index) => ((index + 1 >= lenght) || end.diff(start, _.measureType) > 1));
  return {
    input: {
      start: start.format(format),
      end: end.format(format),
      step,
    },
    display: {
      range: Array.from({ length: end.diff(start, measureType) + 1 },
        (v, i) => start.clone().add(i, measureType).format(displayFormat)),
    },
  };
}

function createTimeMeasure(measureType, step, format, displayFormat = format) {
  return {
    measureType, step, format, displayFormat,
  };
}

function getMeasureList() {
  return [createTimeMeasure('months', 'MONTH', 'YYYY-MM'), createTimeMeasure('days', 'DAY', 'YYYY-MM-DD'),
    createTimeMeasure('hours', 'HOUR', 'YYYY-MM-DD HH', 'YYYY-MM-DD HH:00:00'), createTimeMeasure('minutes', 'MINUTE', 'YYYY-MM-DD HHmm', 'HH:mm:00')];
}

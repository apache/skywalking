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

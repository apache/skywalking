import mockjs from 'mockjs';

export default {
  getAlarm(req, res) {
    const { variables: { alarmType } } = req.body;
    switch (alarmType) {
      case 'APPLICATION':
        return res.json(mockjs.mock(
          {
            data: {
              loadAlarmList: {
                'items|10': [{
                  'key|+1': 1,
                  title: '@name',
                  content: '@paragraph(1)',
                  startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
                  'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
                  alarmType: 'APPLICATION',
                }],
                total: '@natural(5, 50)',
              },
            },
          }
        ));
      case 'SERVER':
        return res.json(mockjs.mock(
          {
            data: {
              loadAlarmList: {
                'items|10': [{
                  'key|+1': 1,
                  title: '@name',
                  content: '@paragraph(1)',
                  startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
                  'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
                  alarmType: 'SERVER',
                }],
                total: '@natural(5, 50)',
              },
            },
          }
        ));
      case 'SERVICE':
        return res.json(mockjs.mock(
          {
            data: {
              loadAlarmList: {
                'items|10': [{
                  'key|+1': 1,
                  title: '@name',
                  content: '@paragraph(1)',
                  startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
                  'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
                  alarmType: 'SERVICE',
                }],
                total: '@natural(5, 50)',
              },
            },
          }
        ));
      default:
        return null;
    }
  },
};

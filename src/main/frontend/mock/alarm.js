import mockjs from 'mockjs';

export default {
  getAlarm(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'loadAlertList|100': [
            {
              'key|+1': 1,
              title: '@name',
              content: '@paragraph(1)',
              startTime: '@datetime("yyyy-MM-dd HH:mm:ss")',
              'causeType|1': ['LOW_SUCCESS_RATE', 'SLOW_RESPONSE'],
              'alertType|1': ['APPLICATION', 'SERVER', 'SERVICE'],
            },
          ],
        },
      }
    ));
  },
};

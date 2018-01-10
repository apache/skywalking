import mockjs from 'mockjs';

export default {
  getAlarm(req, res) {
    res.json(mockjs.mock(
      {
        data: {
          'loadAlertList|100': [{ 'key|+1': 1, content: '@string(20)', startTime: '@datetime("yyyy-MM-dd HH:mm:ss")', 'alertType|1': ['APPLICATION', 'SERVER', 'SERVICE'] }],
        },
      }
    ));
  },
};

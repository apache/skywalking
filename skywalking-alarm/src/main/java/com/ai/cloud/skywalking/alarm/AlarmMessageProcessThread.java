package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadStatus;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadValue;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.procesor.AlarmMessageProcessor;
import com.ai.cloud.skywalking.alarm.util.ProcessUtil;
import com.ai.cloud.skywalking.alarm.util.ZKUtil;
import com.google.gson.Gson;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AlarmMessageProcessThread extends Thread {

    private Logger logger = LogManager.getLogger(AlarmMessageProcessThread.class);

    private String threadId;
    private ProcessThreadStatus status;
    private List<String> processUserIds;
    private CoordinatorStatusWatcher watcher = new CoordinatorStatusWatcher();
    private Map<UserInfo, List<AlarmRule>> cacheRules = new HashMap<UserInfo, List<AlarmRule>>();
    private static AlarmMessageProcessor processor = new AlarmMessageProcessor();

    public AlarmMessageProcessThread() {
        // 初始化生成ThreadId
        threadId = UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        //注册服务(默认为空闲状态)
        registerProcessThread(threadId, ProcessThreadStatus.FREE);
        while (true) {
            try {
                //检查是否为忙碌状态
                if (status == ProcessThreadStatus.BUSY) {
                    //处理告警信息
                    for (Map.Entry<UserInfo, List<AlarmRule>> entry : cacheRules.entrySet()) {
                        for (AlarmRule rule : entry.getValue()) {
                            processor.process(entry.getKey(), rule);
                        }
                    }
                }

                //检查是否分配线程的状态(重新分配状态)
                if (status == ProcessThreadStatus.REDISTRIBUTING) {
                    // 修改自身状态：(空闲状态)
                    status = ProcessThreadStatus.FREE;
                    ProcessUtil.changeProcessThreadStatus(threadId, ProcessThreadStatus.FREE);

                    //清空缓存数据
                    clearCacheProcessUser(cacheRules);
                }

                //检查分配线程的状态(分配完成状态)
                if (status == ProcessThreadStatus.REDISTRIBUTE_SUCCESS) {
                    // 获取待处理的用户
                    processUserIds = acquireProcessedUsers();

                    // 缓存数据
                    cacheProcessUser(processUserIds);

                    // 修改自身状态 ：(忙碌状态)
                    status = ProcessThreadStatus.BUSY;
                    ProcessUtil.changeProcessThreadStatus(threadId, ProcessThreadStatus.BUSY);
                }

                try {
                    Thread.sleep(Config.ProcessThread.THREAD_WAIT_INTERVAL);
                } catch (InterruptedException e) {
                    logger.error("Sleep failed.", e);
                }
            } catch (Exception e) {
                logger.error("Failed to process data.", e);
            }
        }
    }

    private void clearCacheProcessUser(Map<UserInfo, List<AlarmRule>> cacheRules) {
        cacheRules.clear();
    }

    private void cacheProcessUser(List<String> processUserIds) {
        // TODO 需要重新获取
        UserInfo tmpUserInfo;
        List<AlarmRule> alarmRules;
        for (String userId : processUserIds) {
            tmpUserInfo = AlarmMessageDao.selectUser(userId);
            if (tmpUserInfo == null) {
                continue;
            }
            alarmRules = AlarmMessageDao.selectAlarmRulesByUserId(userId);
            cacheRules.put(tmpUserInfo, alarmRules);
        }
    }

    private List<String> acquireProcessedUsers() {
        String path = Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId;
        String value = ZKUtil.getPathData(path);
        ProcessThreadValue processThreadValue = new Gson().fromJson(value, ProcessThreadValue.class);
        return processThreadValue.getDealUserIds();
    }

    private void registerProcessThread(String threadId, ProcessThreadStatus status) {
        try {
            String registerPath = Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId;
            ProcessThreadValue initValue = new ProcessThreadValue();
            initValue.setStatus(status.getValue());
            ZKUtil.getZkClient().create().creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL).forPath
                    (registerPath, new Gson().toJson(initValue).getBytes());

            this.status = status;


            ZKUtil.getPathDataWithWatch(Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId, watcher);
        } catch (Exception e) {
            logger.error("Failed to register process thread.", e);
        }
    }

    private class CoordinatorStatusWatcher implements CuratorWatcher {

        @Override
        public void process(WatchedEvent watchedEvent) {
            if (watchedEvent.getType() == Watcher.Event.EventType.NodeDataChanged) {
                String value = ZKUtil.getPathData(Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId);
                ProcessThreadValue processThreadValue = new Gson().fromJson(value, ProcessThreadValue.class);
                status = ProcessThreadStatus.convert(processThreadValue.getStatus());
            }

            try {
                ZKUtil.getPathDataWithWatch(Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId, watcher);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}

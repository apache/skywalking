package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadStatus;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadValue;
import com.ai.cloud.skywalking.alarm.util.ProcessUtil;
import com.ai.cloud.skywalking.alarm.util.ZKUtil;
import com.google.gson.Gson;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UserInfoCoordinator extends Thread {

	private Logger logger = LogManager.getLogger(UserInfoCoordinator.class);

	private boolean redistributing;
	private RegisterServerWatcher watcher = new RegisterServerWatcher();
	private InterProcessMutex lock = new InterProcessMutex(
			ZKUtil.getZkClient(), Config.ZKPath.COORDINATOR_PATH);
	private boolean isCoordinator = false;

	public UserInfoCoordinator() {
	}

	@Override
	public void run() {
		while (true) {
			try {
				if (!isCoordinator) {
					while (!retryBecomeCoordinator()) {
						try {
							Thread.sleep(Config.Coordinator.RETRY_BECOME_COORDINATOR_WAIT_TIME);
						} catch (Exception e) {
							logger.error("Sleep Failed.", e);
						}
					}
					
					logger.info("becoming cluster coordinator.");
					isCoordinator = true;
					watcherRegisterServerPath();
					redistributing = true;
				}

				// 检查是否有新服务注册或者在重分配过程做有新处理线程启动了
				if (!redistributing) {
					try {
						Thread.sleep(Config.Coordinator.CHECK_REDISTRIBUTE_INTERVAL);
					} catch (InterruptedException e) {
						logger.error("Sleep error", e);
					}

					continue;
				}

				redistributing = false;

				// 获取当前所有的注册的处理线程
				List<String> registeredThreads = acquireAllRegisteredThread();
				// 修改状态 (开始重新分配状态）
				changeStatus(registeredThreads,
						ProcessThreadStatus.REDISTRIBUTING);
				// 检查所有的服务是否都处于空闲状态
				int retryTimes = 0;
				while (!checkAllProcessStatus(registeredThreads,
						ProcessThreadStatus.FREE)) {
					try {
						Thread.sleep(Config.Coordinator.CHECK_ALL_PROCESS_THREAD_INTERVAL);
						retryTimes++;
					} catch (InterruptedException e) {
						logger.error("Sleep failed", e);
					}
					
					if(retryTimes > 1000){
						logger.warn("checking all processors are free, waiting {}ms", Config.Coordinator.CHECK_ALL_PROCESS_THREAD_INTERVAL * retryTimes);
						retryTimes = 0;
					}
				}

				// 查询当前有多少用户
				List<String> users = AlarmMessageDao.selectAllUserIds();

				// 将用户重新分配给服务
				List<String> realRedistributeThread = allocationUser(
						registeredThreads, users);

				// 修改状态(分配完成)
				changeStatus(realRedistributeThread,
						ProcessThreadStatus.REDISTRIBUTE_SUCCESS);

				// 检查所有的服务是否都处于忙碌状态
				while (!checkAllProcessStatus(realRedistributeThread,
						ProcessThreadStatus.BUSY)) {
					try {
						Thread.sleep(Config.Coordinator.CHECK_ALL_PROCESS_THREAD_INTERVAL);
					} catch (InterruptedException e) {
						logger.error("Sleep failed", e);
					}
					
					if(retryTimes > 1000){
						logger.warn("checking all processors are busy, waiting {}ms", Config.Coordinator.CHECK_ALL_PROCESS_THREAD_INTERVAL * retryTimes);
						retryTimes = 0;
					}
				}

			} catch (Exception e) {
				logger.error("Failed to coordinate, release lock. ", e);
				releaseCoordinator();
				isCoordinator = false;
			}
		}
	}

	private boolean retryBecomeCoordinator() {
		try {
			return lock.acquire(
					Config.Coordinator.RETRY_GET_COORDINATOR_LOCK_INTERVAL,
					TimeUnit.SECONDS);
		} catch (Exception e) {
			logger.error("Failed to acquire lock .", e);
			return false;
		}
	}

	private void releaseCoordinator() {
		if (lock != null && lock.isAcquiredInThisProcess()) {
			try {
				lock.release();
			} catch (Exception e1) {
				logger.error("Failed to release lock.", e1);
			}
		}
	}

	private List<String> allocationUser(List<String> registeredThreads,
			List<String> userIds) {
		List<String> realRedistributeThread = new ArrayList<String>();
		Set<String> sortThreadIds = new HashSet<String>(registeredThreads);
		int step = (int) Math.ceil(userIds.size() * 1.0 / sortThreadIds.size());
		int start = 0;
		int end = step;

		if (end > userIds.size()) {
			end = userIds.size();
		}

		for (String thread : sortThreadIds) {
			if (!ZKUtil.exists(Config.ZKPath.REGISTER_SERVER_PATH + "/"
					+ thread))
				continue;
			String value = ZKUtil
					.getPathData(Config.ZKPath.REGISTER_SERVER_PATH + "/"
							+ thread);
			ProcessThreadValue value1 = new Gson().fromJson(value,
					ProcessThreadValue.class);
			value1.setDealUserIds(userIds.subList(start, end));
			ZKUtil.setPathData(Config.ZKPath.REGISTER_SERVER_PATH + "/"
					+ thread, new Gson().toJson(value1));
			// 实际重新分配的线程Id
			realRedistributeThread.add(thread);

			start = end;
			end += step;
			if (start >= userIds.size()) {
				break;
			}
			if (end > userIds.size()) {
				end = userIds.size();
			}

		}
		return realRedistributeThread;
	}

	private List<String> acquireAllRegisteredThread() {
		return ZKUtil.getChildren(Config.ZKPath.REGISTER_SERVER_PATH);
	}

	private boolean checkAllProcessStatus(List<String> registeredThreadIds,
			ProcessThreadStatus status) {
		String registerPathPrefix = Config.ZKPath.REGISTER_SERVER_PATH + "/";
		for (String threadId : registeredThreadIds) {

			if (!ZKUtil.exists(Config.ZKPath.REGISTER_SERVER_PATH + "/"
					+ threadId))
				continue;

			if (getProcessThreadStatus(registerPathPrefix, threadId) != status) {
				return false;
			}
		}
		return true;
	}

	private ProcessThreadStatus getProcessThreadStatus(
			String registerPathPrefix, String threadId) {
		if (!ZKUtil.exists(Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId))
			return ProcessThreadStatus.FREE;
		String value = ZKUtil.getPathData(registerPathPrefix + threadId);
		if (value == null || value.length() == 0)
			return ProcessThreadStatus.FREE;
		ProcessThreadValue value1 = new Gson().fromJson(value,
				ProcessThreadValue.class);
		return ProcessThreadStatus.convert(value1.getStatus());
	}

	private void changeStatus(List<String> registeredThreadIds,
			ProcessThreadStatus status) {
		for (String threadId : registeredThreadIds) {
			ProcessUtil.changeProcessThreadStatus(threadId, status);
		}
	}

	public class RegisterServerWatcher implements CuratorWatcher {

		@Override
		public void process(WatchedEvent watchedEvent) {
			if (watchedEvent.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
				redistributing = true;
			}

			watcherRegisterServerPath();
		}
	}

	private void watcherRegisterServerPath() {
		try {
			ZKUtil.getChildrenWithWatcher(Config.ZKPath.REGISTER_SERVER_PATH,
					watcher);
		} catch (Exception e) {
			logger.error("Failed to set watcher for get children", e);
		}
	}
}

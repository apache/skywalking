package org.skywalking.apm.collector.cluster.zookeeper;

import java.io.IOException;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Test;

/**
 * @author pengys5
 */
public class ZookeeperTestCase {

    @Test
    public void test() throws IOException, KeeperException, InterruptedException {
        String hostPort = "localhost:2181";
        String znode = "/collector/module";
        String filename = "";
        String exec[] = new String[5 - 3];
//        new ZookeeperExecutor(hostPort, znode, filename, exec).run();

        ZooKeeper zk = new ZooKeeper(hostPort, 1000, new Watcher() {
            @Override public void process(WatchedEvent event) {
                String path = event.getPath();
                System.out.println("已经触发了" + event.getType() + "事件！");
                System.out.println("path: " + path);
            }
        });

        zk.create("/testRootPath", "testRootData".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.PERSISTENT);
// 创建一个子目录节点
        zk.create("/testRootPath/testChildPathOne", "testChildDataOne".getBytes(),
            ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.PERSISTENT);
        System.out.println(new String(zk.getData("/testRootPath",false,null)));
        // 取出子目录节点列表
        System.out.println(zk.getChildren("/testRootPath",true));
        // 修改子目录节点数据
        zk.setData("/testRootPath/testChildPathOne","modifyChildDataOne".getBytes(),-1);
        System.out.println("目录节点状态：["+zk.exists("/testRootPath",true)+"]");
        // 创建另外一个子目录节点
        zk.create("/testRootPath/testChildPathTwo", "testChildDataTwo".getBytes(),
            ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.PERSISTENT);
//        System.out.println(new String(zk.getData("/testRootPath/testChildPathTwo",true,null)));
        // 删除子目录节点
        zk.delete("/testRootPath/testChildPathTwo",-1);
        zk.delete("/testRootPath/testChildPathOne",-1);
        // 删除父目录节点
        zk.delete("/testRootPath",-1);
        zk.close();
    }
}

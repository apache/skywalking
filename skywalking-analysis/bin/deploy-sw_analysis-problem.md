#FAQ

#常见命令  Common Commands：
```
#上传所有jar包. Upload all jar file to hdfs
./hdfs dfs -put /aifs01/users/devhdp01/hadoop-2.6.0/share/hadoop/common/lib/*.jar /aifs01/users/devhdp01/hadoop-2.6.0/share/hadoop/common/lib/

#创建目录. Make directory into hdfs
./hdfs dfs -mkdir -p /aifs01/users/devhdp01/hbase-1.1.2/lib/
```

## Jar包找不到. Jar File cannot be find.
###日志. Detail
```
Exception in thread "main" java.io.FileNotFoundException: File does not exist: hdfs://host-10-1-241-18:9000/aifs01/users/devhdp01/hbase-1.1.2/lib/hbase-hadoop-compat-1.1.2.jar
        at org.apache.hadoop.hdfs.DistributedFileSystem$18.doCall(DistributedFileSystem.java:1122)
        at org.apache.hadoop.hdfs.DistributedFileSystem$18.doCall(DistributedFileSystem.java:1114)
        at org.apache.hadoop.fs.FileSystemLinkResolver.resolve(FileSystemLinkResolver.java:81)
        at org.apache.hadoop.hdfs.DistributedFileSystem.getFileStatus(DistributedFileSystem.java:1114)
        at org.apache.hadoop.mapreduce.filecache.ClientDistributedCacheManager.getFileStatus(ClientDistributedCacheManager.java:288)
        at org.apache.hadoop.mapreduce.filecache.ClientDistributedCacheManager.getFileStatus(ClientDistributedCacheManager.java:224)
        at org.apache.hadoop.mapreduce.filecache.ClientDistributedCacheManager.determineTimestamps(ClientDistributedCacheManager.java:93)
        at org.apache.hadoop.mapreduce.filecache.ClientDistributedCacheManager.determineTimestampsAndCacheVisibilities(ClientDistributedCacheManager.java:57)
```
###解决.Resolve
1. 创建目录. Make directory into hdfs
```
${HADOOP_HOME}/bin>./hdfs dfs -mkdir -p /aifs01/users/devhdp01/hbase-1.1.2/lib/
```
2. 上传文件. Upload jar file to hdfs
```
${HADOOP_HOME}/bin>./hdfs dfs -put /aifs01/users/devhdp01/hbase-1.1.2/lib/hbase-hadoop-compat-1.1.2.jar /aifs01/users/devhdp01/hbase-1.1.2/lib/
```

## 分析失败, 如何分析之前的数据. After analyis failed. Cannot analysis the data before now.
1. 删除start-analysis.sh脚本中${SW_ANALYSIS_HOME}对应的目录，默认分析三个月之前的数据
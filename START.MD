# 通过 IntelliJ IDEA 本地调试 RocketMQ
**注意：推荐使用 jdk1.8**
## 运行 nameserver
直接执行：org.apache.rocketmq.namesrv.NamesrvStartup#main
## 运行 broker
在 ~/store/config（RocketMQ 默认的存储目录）下创建 broker.properties 文件
```
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
deleteWhen=04
fileReservedTime=48
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
namesrvAddr=localhost:9876
```
直接执行：org.apache.rocketmq.broker.BrokerStartup#main，启动 broker
## 运行 dashboard（可选）
下载 dashboard 项目：git clone git@github.com:apache/rocketmq-dashboard.git
待 maven 导入结束后，直接运行：org.apache.rocketmq.dashboard.App#main
## 测试 Producer
执行：org.apache.rocketmq.example.simple.Producer#main
日志打印成功即可


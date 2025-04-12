# 通过 IntelliJ IDEA 本地调试 RocketMQ
```shell
  export ROCKETMQ_HOME=$(pwd)
  echo $ROCKETMQ_HOME
  java -cp "target/classes:target/dependency/*" org.apache.rocketmq.namesrv.NamesrvStartup
```
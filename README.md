# SimpleRaft

## 设计
refer to ：https://raft.github.io/raft.pdf

## 功能
- leader election
- log replication


## 示例

下面以在同一台机器上，启动4个server为例说明：
复制的日志文件xx.log和系统数据xx.meta会保存在当前目录下。

xx.log的格式
> 5:hello

5 表示term号
hello 表示日志内容

xx.meta的格式
>48,127.0.0.1:3002

48 表示最新的term
127.0.0.1:3002 表示voteFor

- server

`
  mvn compile exec:java -Dexec.mainClass="org.simpleRaft.SimpleRaftServer" -Dexec.args="-i 127.0.0.1 -p 3001 -m 127.0.0.1:3001,127.0.0.1:3002,127.0.0.1:3003,127.0.0.1:3004"
`

`
  mvn compile exec:java -Dexec.mainClass="org.simpleRaft.SimpleRaftServer" -Dexec.args="-i 127.0.0.1 -p 3002 -m 127.0.0.1:3001,127.0.0.1:3002,127.0.0.1:3003,127.0.0.1:3004"
`

`
  mvn compile exec:java -Dexec.mainClass="org.simpleRaft.SimpleRaftServer" -Dexec.args="-i 127.0.0.1 -p 3003 -m 127.0.0.1:3001,127.0.0.1:3002,127.0.0.1:3003,127.0.0.1:3004"
`

`
  mvn compile exec:java -Dexec.mainClass="org.simpleRaft.SimpleRaftServer" -Dexec.args="-i 127.0.0.1 -p 3004 -m 127.0.0.1:3001,127.0.0.1:3002,127.0.0.1:3003,127.0.0.1:3004"
`

- client （以增加一个log记录为例，这里的IP可以是集群中任意一个服务器）

`
 mvn compile exec:java -Dexec.mainClass="org.simpleRaft.client.SimpleRaftClient" -Dexec.args="-i 127.0.0.1 -p 3002 -c \"add world3\""
 `
 

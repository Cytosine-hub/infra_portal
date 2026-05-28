-- 常用命令模块 DDL

CREATE TABLE IF NOT EXISTS middleware_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL COMMENT '类型名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中间件类型';

CREATE TABLE IF NOT EXISTS middleware_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    middleware_type_id BIGINT NOT NULL COMMENT '所属中间件类型ID',
    command_format TEXT NOT NULL COMMENT '命令格式',
    brief_description VARCHAR(500) COMMENT '简要说明',
    detailed_description TEXT COMMENT '详细说明',
    categories TEXT COMMENT '分类标签JSON数组',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    INDEX idx_type (middleware_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用命令';

-- 种子数据：中间件类型
INSERT INTO middleware_types (id, name, sort_order) VALUES
(1, 'Redis', 0),
(2, 'Kafka', 2),
(3, 'Zookeeper', 1),
(4, 'RabbitMQ', 3),
(5, 'RocketMQ', 4),
(6, 'Java容器', 5),
(7, 'Nacos', 6);

-- 种子数据：常用命令
INSERT INTO middleware_commands (id, middleware_type_id, command_format, brief_description, detailed_description, categories, sort_order) VALUES
(7, 2, 'kafka-topics.sh --list --bootstrap-server localhost:9092', '查看所有主题', '列出Kafka集群中的所有topic', '["查询"]', 0),
(8, 2, 'kafka-topics.sh --create --topic test --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1', '创建主题', '创建一个名为test的topic，包含3个分区', '["操作","创建"]', 1),
(9, 2, 'kafka-console-producer.sh --bootstrap-server localhost:9092 --topic test', '生产消息', '启动生产者，向test topic发送消息', '["生产"]', 2),
(10, 2, 'kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test --from-beginning', '消费消息', '启动消费者，从test topic消费消息', '["消费"]', 3),
(11, 3, 'echo stat | nc localhost 2181', '查看Zookeeper状态', '通过netcat连接Zookeeper并查看状态', '["基础","常用","监控"]', 0),
(12, 3, 'zkCli.sh -server localhost:2181', '连接Zookeeper客户端', '启动Zookeeper命令行客户端', '["基础","常用"]', 1),
(13, 3, 'ls /', '查看根节点', '列出根目录下的所有子节点', '["查询"]', 2),
(14, 3, 'get /path', '获取节点数据', '获取指定路径节点的数据和状态', '["查询"]', 3),
(15, 4, 'rabbitmqctl list_queues', '查看所有队列', '列出RabbitMQ中所有的队列及其消息数量', '["查询"]', 0),
(16, 4, 'rabbitmqctl list_exchanges', '查看所有交换机', '列出RabbitMQ中所有的交换机', '["查询"]', 1),
(17, 4, 'rabbitmqctl list_connections', '查看所有连接', '列出当前所有的客户端连接', '["查询"]', 2),
(18, 5, 'mqadmin clusterList -n localhost:9876', '查看集群信息', '查看RocketMQ集群的详细信息', '["查询"]', 0),
(19, 5, 'mqadmin topicList -n localhost:9876', '查看所有主题', '列出RocketMQ中的所有topic', '["查询"]', 1),
(20, 5, 'sh mqnamesrv', '启动NameServer', '启动RocketMQ的NameServer服务', '["启动"]', 2),
(21, 6, 'docker ps', '查看运行中的容器', '列出当前正在运行的Docker容器', '["基础","常用","查询"]', 0),
(22, 6, 'docker ps -a', '查看所有容器', '列出所有的Docker容器，包括停止的', '["查询"]', 1),
(23, 6, 'docker start container_id', '启动容器', '启动指定ID的容器', '["基础","常用","操作"]', 2),
(24, 6, 'docker stop container_id', '停止容器', '停止指定ID的容器', '["基础","常用","操作"]', 3),
(25, 6, 'docker logs container_id', '查看容器日志', '查看指定容器的日志输出', '["基础","常用","日志"]', 4),
(26, 6, 'docker exec -it container_id bash', '进入容器', '以交互模式进入容器的bash shell', '["基础","常用","操作"]', 5),
(27, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT info', '【查看基础信息】redis-cli -a abc@123 -h 127.0.0.1  -p 6379 info', 'Redis Info 命令以一种易于理解和阅读的格式，返回关于 Redis 服务器的各种信息和统计数值。', '["基础","常用","查询"]', 0),
(28, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT  client list', '【查看有哪些客户端连接】redis-cli -a abc@123 -h 127.0.0.1 -p 6379 client list', 'CLIENT LIST 命令的输出是每行一个客户端，包含多个字段。', '["基础","常用","查询"]', 0),
(29, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT   config get  [参数名称]', '【查看配置参数】redis-cli -a abc@123 -h 127.0.0.1  -p 6379 config get  appendonly', '在命令最后部分可以指定要获取的参数。', '["常用"]', 1),
(30, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT   config set  [参数名字]  [参数值]', '【修改配置参数】redis-cli -a abc@123 -h 127.0.0.1  -p 6379 config set  appendonly yes', '常用配置参数说明。', '["常用"]', 1),
(31, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT   rewrite rewrite', '【配置参数持久化写入到配置文件】redis-cli -a abc@123 -h 127.0.0.1  -p 6379 config rewrite', '', '["常用"]', 1),
(32, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT    shutdown save', '【停止进程前强制执行RDB持久化】redis-cli -a abc@123 -h 127.0.0.1  -p 6379 shutdown save', 'SHUTDOWN 所有参数详解。', '["基础","常用"]', 1),
(33, 1, 'redis-cli  -h $HOST -p $SENTINEL_PORT   SENTINEL FAILOVER mymaster', '【手动故障转移】redis-cli -h 127.0.0.1  -p 26379  SENTINEL FAILOVER mymaster', '强制对指定主节点执行故障转移。', '["操作"]', 2),
(34, 1, 'redis-cli  -h $HOST -p $SENTINEL_PORT   SENTINEL RESET mymaster', '【刷新哨兵状态】redis-cli -h 127.0.0.1  -p 26379  SENTINEL RESET mymaster', '执行后哨兵会重新发现、重新监控主节点及其从节点。', '["操作"]', 1),
(35, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT   cluster info', '【查看集群状态】 redis-cli -a abc@123 -h 127.0.0.1  -p 6379  cluster info', 'CLUSTER INFO 命令输出详解。', '["查询","集群"]', 1),
(36, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT    cluster nodes', '【查看集群节点信息】 redis-cli -a abc@123 -h 127.0.0.1  -p 6379   cluster nodes', '查看集群所有节点的角色、地址、状态等信息。', '["查询","集群"]', 0),
(37, 1, 'redis-cli -a $PASSWORD -h $HOST -p $PORT   slowlog get [N]', '【慢日志查询】 redis-cli -a abc@123 -h 127.0.0.1  -p 6379   slowlog get 5', '返回最新的N条慢查询记录。', '["查询"]', 1),
(38, 1, 'redis-cli -a $PASSWORD --cluster call    $HOST:$PORT   【命令】', '【集群批量执行命令】 redis-cli -a abc@123 --cluster call  127.0.0.1:6379 info ', '集群模式下在所有节点执行命令。', '["集群"]', 1),
(39, 4, 'rabbitmq-server  -detached', '【启动服务】', '', '["重启"]', 0),
(40, 4, 'rabbitmqctl stop', '【停止服务】', '', '["重启"]', 0),
(41, 4, 'rabbitmqctl start_app', '【启动应用】', '', '["重启"]', 0),
(42, 4, 'rabbitmqctl stop_app', '【停止应用】', '', '["重启"]', 0),
(43, 4, 'rabbitmqctl cluster_status', '查看集群状态', '', '["查询"]', 0),
(44, 4, 'rabbitmqctl list_users', '【查看用户列表】', '', '["查询"]', 0),
(45, 4, 'rabbitmqctl change_password $userName $newPassword', '【修改密码】', '', '["操作"]', 0),
(46, 4, 'rabbitmqctl delete_user userName', '【删除用户】', '', '["操作"]', 0),
(47, 2, '/app/kafka/kafka_home/bin/kafka-server-start.sh -daemon /app/kafka/kafka_home/config/kafkaserver.properties', '【启动服务】', '', '["基础","操作"]', 0),
(48, 2, '/app/kafka/kafka_home/bin/kafka-server-stop.sh', '【停止服务】', '', '["基础","操作"]', 0),
(49, 2, '/app/kafka/kafka_home/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092     --describe ', '【查看top详情】', '', '["查询"]', 0),
(50, 2, '/app/kafka/kafka_home/bin/kafka-leader-election.sh --bootstrap-server 127.0.0.1:9092    --election-type preferred --all-topic-partitions', '【Leader重平衡】', '', '["操作"]', 0),
(51, 2, '/app/kafka/kafka_home/bin/kafka-reassign-partitions.sh --bootstrap-server  127.0.0.1:9092  --execute --reassignment-json-file  middletest2.json', '【topic 加副本】', '', '["操作"]', 0),
(52, 2, 'kafka-consumer-groups.sh   --bootstrap-server 127.0.0.1:9092   --describe   --group my_group', '【查看消费者组my_group消费情况】', '', '["查询"]', 0);

package org.apache.rocketmq.example.simple;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;

public class DelayedProducer {

    public static final String DEFAULT_NAMESRVADDR = "127.0.0.1:9876";

    public static void main(String[] args) throws Exception {

        String topic = "DelayedTopic";
        DefaultMQProducer producer = new DefaultMQProducer("ProducerGroupName");

        // Uncomment the following line while debugging, namesrvAddr should be set to your local address
        producer.setNamesrvAddr(DEFAULT_NAMESRVADDR);

        producer.start();

        for (int i = 0; i < 10; i++) {

            Message message = new Message(topic, ("Hello scheduled message " + i).getBytes(StandardCharsets.UTF_8));
            // 延迟 10s 后投递
            message.setDelayTimeSec(10);
            // 发送消息
            SendResult result = producer.send(message);
            System.out.println(result);
        }


    }

}

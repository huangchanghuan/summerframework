
package com.bkjk.platform.rabbit.test;

import com.bkjk.platform.rabbit.delay.Delay;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Program: summerframework2
 * @Description:
 * @Author: shaoze.wang
 * @Create: 2019/2/22 13:37
 **/
@SpringBootApplication
@EnableScheduling
public class AmqpSimpleApplication {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        System.err.println(rabbitTemplate);
    }

    public static void main(String[] args) {
        SpringApplication.run(AmqpSimpleApplication.class, args);
    }

    /**
     * 消费者
     * @param foo
     */
    @RabbitListener(queues = "architect.queue")
    public void process(@Payload Integer foo) {
        System.out.println(new Date() + "数值: " + foo);
        throw new RuntimeException();
    }

//    @Bean
    public Sender mySender() {
        return new Sender();
    }

    class Sender {

        @Autowired
        private RabbitTemplate rabbitTemplate;
        private AtomicInteger atomicInteger = new AtomicInteger();
        /**
         * 生产者定时发送
         */
        @Scheduled(fixedDelay = 100L)
//        @Delay(interval = 1000L, queue = "architect.queue")
        public void send() {

            rabbitTemplate.convertAndSend("architect.exchange", "architect.route", atomicInteger.getAndIncrement());
        }
    }
}


package com.bkjk.platform.rabbit.extend;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import com.bkjk.platform.rabbit.DeadLetterConstant;
import com.bkjk.platform.rabbit.DeadLetterQueueCreator;

/**
 * todo 重试逻辑？
 * 消费者抛出异常的时候，会到这里处理
 */
public class RepublishMessageRecovererExtend implements MessageRecoverer {

    public static final String X_EXCEPTION_STACKTRACE = "x-exception-stacktrace";

    public static final String X_EXCEPTION_MESSAGE = "x-exception-message";

    public static final String X_ORIGINAL_EXCHANGE = "x-original-exchange";

    public static final String X_ORIGINAL_ROUTING_KEY = "x-original-routingKey";

    public static final String X_REPUBLISH_TIMES = "x-republish-times";

    private final Log logger = LogFactory.getLog(getClass());

    private final AmqpTemplate errorTemplate;

    private final DeadLetterQueueCreator deadLetterQueueCreator;
    //todo 自定义messagerecover不应该和本地重试共用参数,需要修改
    private int recoverTimes = 3;
    private Long interval;

    public RepublishMessageRecovererExtend(AmqpTemplate errorTemplate, AmqpAdmin amqpAdmin) {
        this.errorTemplate = errorTemplate;
        this.deadLetterQueueCreator = new DeadLetterQueueCreator(amqpAdmin);
    }

    protected Map<? extends String, ? extends Object> additionalHeaders(Message message, Throwable cause) {
        return null;
    }

    private String createRetryQueueAndGetRetryRourtingKey(Message message) {
        MessageProperties messageProperties = message.getMessageProperties();
        String exchange = messageProperties.getReceivedExchange();
        String routeKey = messageProperties.getReceivedRoutingKey();
        String queueName = messageProperties.getConsumerQueue();
        String retryQueueName = queueName + DeadLetterConstant.DEFAULT_RETRY_QUEUENAME_PREFIX;
        String retryRouteKey = routeKey + DeadLetterConstant.DEFAULT_RETRY_QUEUENAME_PREFIX;
        deadLetterQueueCreator.createDeadLetterQueue(exchange, routeKey, retryRouteKey, queueName, retryQueueName,
            interval);
        return retryRouteKey;
    }

    private String getStackTraceAsString(Throwable cause) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter, true);
        cause.printStackTrace(printWriter);
        return stringWriter.getBuffer().toString();
    }

    /**
     *  Auto
     * 1. 如果消息成功被消费（成功的意思是在消费的过程中没有抛出异常），则自动确认
     * 2. 当抛出 AmqpRejectAndDontRequeueException 异常的时候，则消息会被拒绝，且 requeue = false（不重新入队列）
     * 3. 当抛出 ImmediateAcknowledgeAmqpException 异常，则消费者会被确认
     * 4. 其他的异常，则消息会被拒绝，且 requeue = true，此时会发生死循环，可以通过 setDefaultRequeueRejected（默认是true）去设置抛弃消息
     * @param message
     * @param cause
     */
    @Override
    public void recover(Message message, Throwable cause) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        headers.put(X_EXCEPTION_STACKTRACE, getStackTraceAsString(cause));
        headers.put(X_EXCEPTION_MESSAGE, cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage());
        headers.put(X_ORIGINAL_EXCHANGE, message.getMessageProperties().getReceivedExchange());
        headers.put(X_ORIGINAL_ROUTING_KEY, message.getMessageProperties().getReceivedRoutingKey());
        Map<? extends String, ? extends Object> additionalHeaders = additionalHeaders(message, cause);
        if (additionalHeaders != null) {
            headers.putAll(additionalHeaders);
        }

        Integer republishTimes = (Integer)headers.get(X_REPUBLISH_TIMES);
        if (republishTimes != null) {
            if (republishTimes >= recoverTimes) {
                logger.warn(String.format("this message [ %s] republish times >= %d times, and will discard",
                    message.toString(), recoverTimes));
                return;
            } else {
                republishTimes = republishTimes + 1;
            }
        } else {
            republishTimes = 1;
        }
        headers.put(X_REPUBLISH_TIMES, republishTimes);
        message.getMessageProperties().setRedelivered(true);
        String retryRoutingKey = this.createRetryQueueAndGetRetryRourtingKey(message);
        //发送失败，还是会ack,导致消息丢失!! 不会抛出异常，比如没有exchange，queue，
        //处理方案：发送之前，打印日志（可以写入数据库），人工介入补偿，属于代码问题了,防止丢失消息
        //处理方案： [写入数据库][send][ack return异步确认]，更加智能
        this.errorTemplate.send(DeadLetterConstant.DEFAULT_DEADLETTEREXCHANGE_NAME, retryRoutingKey, message);
    }

    public void setInterval(Long interval) {
        this.interval = interval;
    }

    public void setRecoverTimes(int recoverTimes) {
        this.recoverTimes = recoverTimes;
    }

}

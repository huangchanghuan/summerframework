
package com.bkjk.platform.eureka;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.cloud.netflix.eureka.CloudEurekaClient;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.cloud.netflix.eureka.serviceregistry.EurekaServiceRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;

import com.bkjk.platform.eureka.wrapper.EurekaClientWrapper;
import com.bkjk.platform.eureka.wrapper.EurekaServiceRegistryWrapper;

/**
 * addBeanPostProcessor 修改容器中实例初始化对象
 */
public class EurekaApplicationContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        applicationContext.getBeanFactory().addBeanPostProcessor(new InstantiationAwareBeanPostProcessorAdapter() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof CloudEurekaClient) {
                    CloudEurekaClient eurekaClient = (CloudEurekaClient)bean;
                    try {
                        Field filedPublisher = ReflectionUtils.findField(CloudEurekaClient.class, "publisher",
                            ApplicationEventPublisher.class);
                        ReflectionUtils.makeAccessible(filedPublisher);
                        ApplicationEventPublisher publisher =
                            (ApplicationEventPublisher)ReflectionUtils.getField(filedPublisher, eurekaClient);
                        //eureka client增强，过滤不符合条件的服务提供者
                        return new EurekaClientWrapper(eurekaClient, environment, publisher);
                    } finally {
                        //todo 这里的作用
                        Method method = ReflectionUtils.findMethod(CloudEurekaClient.class, "cancelScheduledTasks");
                        ReflectionUtils.makeAccessible(method);
                        ReflectionUtils.invokeMethod(method, eurekaClient);
                        eurekaClient = null;
                    }
                } else if (bean instanceof EurekaServiceRegistry) {
                    //设置上报METADATA
                    EurekaServiceRegistry eurekaServiceRegistry = (EurekaServiceRegistry)bean;
                    return new EurekaServiceRegistryWrapper(eurekaServiceRegistry, environment);
                } else if (bean instanceof EurekaInstanceConfigBean) {
                    EurekaInstanceConfigBean instanceConfig = (EurekaInstanceConfigBean)bean;
                    instanceConfig.setPreferIpAddress(true);
                    return bean;
                } else {
                    return bean;
                }
            }
        });
    }

}

package com.bkjk.platform.monitor.metric.micrometer;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * todo 为什么定时任务凌晨2点删除meter
 */
@Slf4j
public class MeterMapCleanerTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeterMapCleanerTask.class);
    public static void main(String[] args) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Metrics.globalRegistry.add(meterRegistry);
        MeterMapCleanerTask task = new MeterMapCleanerTask(Metrics.globalRegistry);
        task.start("0/2 * * * * ?");

        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor();
        s.scheduleAtFixedRate(() -> {
            meterRegistry.counter(UUID.randomUUID().toString()).increment();
            System.out.println(meterRegistry.getMeters().size());
        }, 0, 100, TimeUnit.MILLISECONDS);

        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        s.shutdown();
        task.stop();
    }

    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    private MeterRegistry meterRegistry;

    public MeterMapCleanerTask(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 清除meterRegistry中所有meter
     */
    public void cleanMeterMap() {

        LOGGER.info("Clean meterMap of {}", this.meterRegistry);
        if (this.meterRegistry instanceof CompositeMeterRegistry) {
            CompositeMeterRegistry compositeMeterRegistry = (CompositeMeterRegistry)meterRegistry;
            this.meterRegistry.getMeters().forEach(this.meterRegistry::remove);
            compositeMeterRegistry.getRegistries().forEach(meterRegistry1 -> {
                meterRegistry1.getMeters().forEach(meterRegistry1::remove);
            });
        } else {
            this.meterRegistry.getMeters().forEach(this.meterRegistry::remove);
        }
    }

    public void start(String trigger) {
        if (meterRegistry == null) {
            LOGGER.error("meterRegistry SHOULD NOT BE NULL");
            return;
        }
        synchronized (this) {
            if (threadPoolTaskScheduler != null) {
                LOGGER.info("MeterMapCleanerTask was already scheduled by {}", threadPoolTaskScheduler);
                return;
            }
            threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
            threadPoolTaskScheduler.setDaemon(true);
            threadPoolTaskScheduler.setThreadNamePrefix("MeterMapCleanerTask");
            threadPoolTaskScheduler.initialize();
            LOGGER.info("MeterMapCleanerTask will be scheduled by {}. trigger = {}", threadPoolTaskScheduler, trigger);
            threadPoolTaskScheduler.schedule(this::cleanMeterMap, new CronTrigger(trigger));
        }
    }

    public void stop() {
        synchronized (this) {
            if (threadPoolTaskScheduler != null) {
                threadPoolTaskScheduler.shutdown();
                threadPoolTaskScheduler = null;
            }
        }
    }
}

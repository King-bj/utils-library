// java
package com.example.redis;

import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreDemo {

    private static final String SEMAPHORE_KEY = "host:192.168.1.1:script-100";
    /**
     * 最大并发数
     */
    private static final int MAX_PERMITS = 2;
    /**
     *  permit 最长等待时间
     */
    private static final int PERMIT_WAIT_SECONDS = 60;

    /**
     *  permit 最长等待时间 110*1.5
     */
    private static final int PERMIT_TTL_SECONDS = 165;


    /**
     *  心跳间隔
     */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 20;
    private static final int TASK_COUNT = 5;

    /**
     *  共享心跳线程池（守护线程，生产中可调整线程数）
     */
    private static final ScheduledExecutorService HEARTBEAT_POOL =
            Executors.newScheduledThreadPool(4, new ThreadFactory() {
                private final AtomicInteger idx = new AtomicInteger(0);
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "SemaphoreHeartbeat-" + idx.getAndIncrement());
                    t.setDaemon(true); // 守护线程，便于 JVM 退出（仍需正确 cancel & shutdown）
                    return t;
                }
            });

    // 任务线程池（用于运行业务任务）
    private static final ExecutorService TASK_POOL  =
            Executors.newFixedThreadPool(TASK_COUNT, new ThreadFactory() {
                private final AtomicInteger idx = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "TaskWorker-" + idx.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            });

    public static void main(String[] args) {
        // 配置 Redisson
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")
                .setPassword("xxx")
                .setDatabase(3)
                ;

        RedissonClient redisson = Redisson.create(config);

        CountDownLatch latch = new CountDownLatch(TASK_COUNT);

        try {
            RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore(SEMAPHORE_KEY);
            semaphore.trySetPermits(MAX_PERMITS);
            for (int i = 1; i <= TASK_COUNT; i++) {
                final String taskId = "task-" + i;
                // 提交任务到线程池，任务完成后 countDown
                TASK_POOL.submit(() -> {
                    try {
                        executeTask(redisson, taskId);
                    } finally {
                        latch.countDown();
                    }
                });
                // 模拟用户陆续提交
                Thread.sleep(1000);
            }

            // 等待所有任务完成（替代 System.in.read()，更适合自动化/生产）
            latch.await();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 优雅关闭：先停止任务池，再关闭心跳池，最后关闭 Redisson
            TASK_POOL.shutdownNow();
            try {
                TASK_POOL.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            HEARTBEAT_POOL.shutdownNow();
            try {
                HEARTBEAT_POOL.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            redisson.shutdown();
            System.out.println("程序已优雅退出");
        }
    }

    private static void executeTask(RedissonClient redisson, String taskId) {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore(SEMAPHORE_KEY);

        String permitId = null;
        ScheduledFuture<?> heartbeatFuture = null;

        try {
            System.out.println("【" + taskId + "】尝试获取信号量...");

            // 获取 permit 并设置 TTL（自动过期兜底）
            permitId = semaphore.tryAcquire(PERMIT_WAIT_SECONDS, PERMIT_TTL_SECONDS +1,TimeUnit.SECONDS);

            if (permitId == null) {
                System.out.println("【" + taskId + "】获取 permit 失败或超时，放弃执行");
                return;
            }

            System.out.println("【" + taskId + "】成功获取 permit: " + permitId);

            // 启动心跳（使用共享 ScheduledExecutorService）
            String permitKey = SEMAPHORE_KEY + ":permits:" + permitId;
            String finalPermitId = permitId;
            Runnable heartbeatTask = () -> {
                try {
                    boolean renewed = semaphore.updateLeaseTime(finalPermitId, PERMIT_TTL_SECONDS, TimeUnit.SECONDS);
                    System.out.println("【HEARTBEAT】续期 permit: " + finalPermitId + " -> " + (renewed ? "成功" : "失败"));
                } catch (Exception e) {
                    System.err.println("【HEARTBEAT】续期失败: " + e.getMessage());
                }
            };
            heartbeatFuture = HEARTBEAT_POOL.scheduleAtFixedRate(
                    heartbeatTask,
                    HEARTBEAT_INTERVAL_SECONDS,
                    HEARTBEAT_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);

            // 模拟远程脚本执行（耗时操作）
            int workTime = 10 + (int) (Math.random() * 100);
            System.out.println("【" + taskId + "】开始执行任务，预计耗时 " + workTime + " 秒...");
            Thread.sleep(workTime * 1000);

            System.out.println("【" + taskId + "】任务执行完成！");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("【" + taskId + "】被中断");
        } catch (Exception e) {
            System.err.println("【" + taskId + "】任务异常: " + e.getMessage());
        } finally {
            // 取消心跳（使用 ScheduledFuture.cancel）
            if (heartbeatFuture != null && !heartbeatFuture.isDone()) {
                // 尝试中断正在执行的心跳任务
                heartbeatFuture.cancel(true);
            }

            // 释放 permit
            if (permitId != null) {
                try {
                    RPermitExpirableSemaphore sem = redisson.getPermitExpirableSemaphore(SEMAPHORE_KEY);
                    sem.release(permitId);
                    System.out.println("【" + taskId + "】已释放 permit: " + permitId);
                } catch (Exception e) {
                    System.err.println("【" + taskId + "】释放 permit 失败: " + e.getMessage());
                }
            }
        }
    }
}
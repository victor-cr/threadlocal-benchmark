package com.oracle.jdk.benchmark;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JavaDoc here
 *
 * @author Victor Polischuk
 * @since 02.11.13 19:13
 */
public class MyThreadExecutorService extends ThreadPoolExecutor implements ExecutorService {
    public MyThreadExecutorService(int poolSize, String prefix) {
        super(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new MyThreadFactory(prefix));
    }

    private static class MyThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        public MyThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            MyThread thread = new MyThread(r);
            thread.setName(prefix + "-worker" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

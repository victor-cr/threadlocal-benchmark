package com.oracle.jdk.benchmark;

/**
 * JavaDoc here
 *
 * @author Victor Polischuk
 * @since 27.10.13 6:31
 */
public class MyThread extends Thread {
    MyThreadLocal.ThreadLocalMap threadLocals;

    public MyThread() {
    }

    public MyThread(Runnable target) {
        super(target);
    }

    public MyThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public MyThread(String name) {
        super(name);
    }

    public MyThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public MyThread(Runnable target, String name) {
        super(target, name);
    }

    public MyThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public MyThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    public static MyThread currentThread() {
        return (MyThread) Thread.currentThread();
    }

    @Override
    public synchronized void start() {
        try {
            super.start();
        } finally {
            if (threadLocals != null) {
                threadLocals.clean();
            }
        }
    }
}

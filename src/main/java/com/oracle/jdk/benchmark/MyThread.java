package com.oracle.jdk.benchmark;

/**
 * JavaDoc here
 *
 * @author Victor Polischuk
 * @since 27.10.13 6:31
 */
public class MyThread extends Thread {
    private static final Numerator NUMERATOR = new Numerator();
    MyThreadLocal.ThreadLocalMap myThreadLocals;
    int index;

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

    synchronized
    private static void setIndex(MyThread thread) {
        thread.index = NUMERATOR.pop();

        System.out.println("# Start thread: " + thread + ", index: " + thread.index);
    }

    synchronized
    private static void retireIndex(MyThread thread) {
        NUMERATOR.push(thread.index);
        System.out.println("# Stop thread: " + thread + ", index: " + thread.index);
        thread.index = 0;
    }

    @Override
    public void start() {
        setIndex(this);

        super.start();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (myThreadLocals != null) {
            myThreadLocals.cleanup(index);
        }

        retireIndex(this);
    }

    private final static class Numerator {
        private final Node root = new Node();
        private int counter = 0;

        private void push(int index) {
            Node r = root;
            Node n = r.next;
            r.next = new Node(index, n);
        }

        private int pop() {
            Node r = root;
            Node n = r.next;

            if (r == n) {
                return ++counter;
            } else {
                r.next = n.next;
                return n.index;
            }
        }
    }

    private final static class Node {
        final int index;
        Node next;

        private Node() {
            this.index = 0;
            this.next = this;
        }

        private Node(int index, Node next) {
            this.index = index;
            this.next = next;
        }
    }
}

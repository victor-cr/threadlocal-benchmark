package com.oracle.jdk.benchmark;

import java.lang.ref.WeakReference;

/**
 * JavaDoc here
 *
 * @author Victor Polischuk
 * @since 27.10.13 6:30
 */
public class MyThreadLocal<T> {
    private static final int BLOCK_BITS = 5;
    private static final int BLOCK_SIZE = 1 << (BLOCK_BITS - 1);
    private static final int BLOCK_MASK = BLOCK_SIZE - 1;

    private final Object lock = new Object();
    private volatile Holder[][] storage = new Holder[0][];

    protected T initialValue() {
        return null;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        MyThread t = MyThread.currentThread();

        Holder holder = find(t);

        if (holder.ref != null && holder.ref.get() == t) {
            return (T) holder.value;
        }

        t.myThreadLocals.put(this, null);
        holder.ref = new WeakReference<>(t);
        return (T) (holder.value = initialValue());
    }

    public void set(T value) {
        MyThread t = MyThread.currentThread();

        Holder holder = find(t);

        if (holder.ref == null || holder.ref.get() != t) {
            holder.ref = new WeakReference<>(t);
            t.myThreadLocals.put(this, null);
        }

        holder.value = value;
    }

    public void remove() {
        remove(MyThread.currentThread());
    }

    @SuppressWarnings("unused")
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    void remove(MyThread t) {
        int i = t.index;
        int page = i >>> BLOCK_BITS;
        Holder[][] tab = storage;
        Holder holder;

        if (tab.length > page && tab[page] != null && (holder = tab[page][i & BLOCK_MASK]) != null && holder.ref != null) {
            t.myThreadLocals.remove(this);
            holder.ref = null;
            holder.value = null;
        }
    }

    private Holder find(MyThread t) {
        Holder[][] tab = storage;
        int i = t.index;
        int len = tab.length;
        int page = i >>> BLOCK_BITS;
        int offset = i & BLOCK_MASK;

        Holder[] holders;

        if (len <= page || (holders = tab[page]) == null) {
            holders = extend(page);
        }

        Holder holder = holders[offset];

        if (holder == null) {
            holder = holders[offset] = new Holder();
        }

        return holder;
    }

    private Holder[] extend(int page) {
        synchronized (lock) {
            Holder[][] tab = storage;

            if (tab.length <= page) {
                int len = page + 1;

                Holder[][] newTab = new Holder[len][];

                System.arraycopy(tab, 0, newTab, 0, tab.length);

                newTab[page] = new Holder[BLOCK_SIZE];

                tab = storage = newTab;
            } else if (tab[page] == null) {
                tab[page] = new Holder[BLOCK_SIZE];
            }

            return tab[page];
        }
    }

    private static class Holder {
        private WeakReference<MyThread> ref;
        private Object value;
    }
}
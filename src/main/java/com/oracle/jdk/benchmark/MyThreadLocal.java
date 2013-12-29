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
    @SuppressWarnings("unchecked")
    volatile Holder<T>[][] storage = new Holder[0][];

    protected T initialValue() {
        return null;
    }

    public T get() {
        Holder<T>[][] tab = storage;
        MyThread t = MyThread.currentThread();
        int i = t.index;
        int len = tab.length;
        int page = i >> BLOCK_BITS;
        int offset = i & BLOCK_MASK;

        Holder<T>[] holders;

        if (len <= page || (holders = tab[page]) == null) {
            holders = extend(page);
        }

        Holder<T> holder = holders[offset];

        if (holder == null) {
            holder = holders[offset] = new Holder<>(t, initialValue());
        } else if (holder.ref.get() != t) {
            holder.ref = new WeakReference<>(t);
            holder.value = initialValue();
        }

        return holder.value;
    }

    public void set(final T value) {
        if (value == null) {
            remove();
        }

        Holder<T>[][] tab = storage;
        MyThread t = MyThread.currentThread();
        int i = t.index;
        int len = tab.length;
        int page = i >> BLOCK_BITS;
        int offset = i & BLOCK_MASK;

        Holder<T>[] holders;

        if (len <= page || (holders = tab[page]) == null) {
            holders = extend(page);
        }

        Holder<T> holder = holders[offset];

        if (holder == null) {
            holders[offset] = new Holder<>(t, value);
        } else {
            if (holder.ref.get() != t) {
                holder.ref = new WeakReference<>(t);
            }

            holder.value = value;
        }
    }

    public void remove() {
        int i = MyThread.currentThread().index;
        int page = i >>> BLOCK_BITS;
        Holder<T>[][] tab = storage;
        Holder holder;

        if (tab.length <= page || tab[page] == null || (holder = tab[page][i & BLOCK_MASK]) == null) {
            return;
        }

        holder.value = null;
    }


    @SuppressWarnings("unused")
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    synchronized
    private Holder<T>[] extend(int page) {
        Holder<T>[][] tab = storage;

        if (tab.length <= page) {
            System.out.print("#Extends#");
            int len = page + 1;
            Holder<T>[][] newTab = new Holder[len][];

            System.arraycopy(tab, 0, newTab, 0, tab.length);

            newTab[page] = new Holder[BLOCK_SIZE];

            tab = storage = newTab;
        } else if (tab[page] == null) {
            System.out.print("#Populates#");
            tab[page] = new Holder[BLOCK_SIZE];
        }

        return tab[page];
    }

    private static class Holder<V> {
        private WeakReference<MyThread> ref;
        private V value;

        private Holder(MyThread t, V v) {
            ref = new WeakReference<>(t);
            value = v;
        }
    }
}
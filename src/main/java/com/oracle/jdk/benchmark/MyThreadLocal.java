package com.oracle.jdk.benchmark;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JavaDoc here
 *
 * @author Victor Polischuk
 * @since 27.10.13 6:30
 */
public class MyThreadLocal<T> {
    private static final int DEFAULT_CAPACITY = 1 << 1;
//    private static final int DEFAULT_CAPACITY = 1 << 5;
    private static final Holder MOVED = new Holder();
//    private static final int BLOCK_BITS = 5;
//    private static final int BLOCK_SIZE = 1 << (BLOCK_BITS - 1);
//    private static final int BLOCK_MASK = BLOCK_SIZE - 1;

    private static final long ADDR_ABASE;
    private static final int ASHIFT;
    private static final sun.misc.Unsafe UNSAFE;

    private volatile Holder[] table;
    private volatile Holder[] nextTable;
    private AtomicInteger sizeCtl = new AtomicInteger(-1);

    static {
        try {
//            /* Security Error */
//            UNSAFE = sun.misc.Unsafe.getUnsafe();
//
            Field singleoneInstanceField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);

            UNSAFE = (sun.misc.Unsafe) singleoneInstanceField.get(null);


            Class<?> arrayClass = Holder[].class;

            ADDR_ABASE = UNSAFE.arrayBaseOffset(arrayClass);
            int scale = UNSAFE.arrayIndexScale(arrayClass);

            if ((scale & (scale - 1)) != 0) {
                System.out.println("AAAAAAAAA!!!! Data");
                throw new Error("data type scale not a power of two");
            }

            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            System.out.println("AAAAAAAAA!!!!");
            throw new Error(e);
        }
    }

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
        Holder[] tab = table;
        int i = t.index();
        Holder holder;

        if (tab != null && tab.length > i && (holder = atomicGet(tab, i)) != null && holder.ref != null) {
            t.myThreadLocals.remove(this);
            holder.ref = null;
            holder.value = null;
        }
    }

    private Holder find(MyThread t) {
        int i = t.index();

        for (Holder[] tab = table; ;) {
            Holder holder;

            if (tab == null) {
                tab = initTable(i);
            } else if (i >= tab.length) {
                tab = extend(i);
            } else if ((holder = atomicGet(tab, i)) == null) {
                if (atomicSet(tab, i, holder = new Holder())) {
                    return holder;
                }
            } else if (holder == MOVED) {
                Holder[] nextTab = nextTable;

                if (nextTab != null) {
                    holder = atomicGet(nextTab, i);

                    if (holder != MOVED && (holder != null || atomicSet(nextTab, i, holder = new Holder()))) {
                        return holder;
                    }
                }

                tab = table;
            } else {
                return holder;
            }
        }
    }

    private Holder[] extend(int index) {
        Holder[] tab = table;
        Holder[] nextTab;

        while (tab.length <= index) {
            while ((nextTab = nextTable) == null) {
                if (sizeCtl.get() >= 0) {
                    Thread.yield(); // lost initialization race; just spin
                } else if (sizeCtl.compareAndSet(-1, 0)) {
                    if ((nextTab = nextTable) == null) {
                        int n = 1 << (32 - Integer.numberOfLeadingZeros(index));

                        if (n < 0) {
                            n = Integer.MAX_VALUE;
                        }

                        nextTable = nextTab = new Holder[n];
                    }
                    break;
                }
            }

            try {
                transfer(tab, nextTab, 0);
            } finally {
                sizeCtl.set(-1);
            }

            table = tab = nextTab;
            nextTable = null;
        }

        return null;
    }

    private void transfer(Holder[] tab, Holder[] nextTab, int index) {
        int len = tab.length;

        while (index < len) {
            Holder holder = atomicGet(tab, index);

            if (holder == MOVED) {
                index++;
            } else if (atomicMark(tab, index, holder)) {
                sizeCtl.incrementAndGet();

                if (holder != null) {
                    atomicSet(nextTab, index, holder); // ignore write error
                }

                index++;
            }
        }
    }

    private Holder[] initTable(int len) {
        Holder[] tab;

        while ((tab = table) == null) {
            if (sizeCtl.get() >= 0) {
                Thread.yield(); // lost initialization race; just spin
            } else if (sizeCtl.compareAndSet(-1, 0)) {
                try {
                    if ((tab = table) == null) {
                        int n = len < DEFAULT_CAPACITY ? 1 << (32 - Integer.numberOfLeadingZeros(len)) : DEFAULT_CAPACITY;
                        table = tab = new Holder[n];
                    }
                } finally {
                    sizeCtl.set(-1);
                }
                break;
            }
        }

        return tab;
    }

    //    private boolean uCheck(int oldValue, int newValue) {
//        return UNSAFE.compareAndSwapInt(this, ADDR_SIZECTL, oldValue, newValue);
//    }
//
    private static Holder atomicGet(Holder[] tab, int index) {
        return (Holder) UNSAFE.getObjectVolatile(tab, addr(index));
    }

    private static boolean atomicSet(Holder[] tab, int index, Holder value) {
        return UNSAFE.compareAndSwapObject(tab, addr(index), null, value);
    }

    private static boolean atomicMark(Holder[] tab, int index, Holder expected) {
        return UNSAFE.compareAndSwapObject(tab, addr(index), expected, MOVED);
    }

//    private static void uSwap(Holder[] tab, int index, Holder value) {
//        UNSAFE.putObjectVolatile(tab, addr(index), value);
//    }

    private static long addr(int index) {
        return ((long) index << ASHIFT) + ADDR_ABASE;
    }

    private static class Holder {
        private WeakReference<MyThread> ref;
        private Object value;
    }
}
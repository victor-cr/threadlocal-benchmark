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
    private static final int INITIAL_CAPACITY = 1 << 1; //TODO: increase it after tests
    private static final int BLOCK_BITS = 5;
    private static final int BLOCK_SIZE = 1 << (BLOCK_BITS - 1);
    private static final int BLOCK_MASK = BLOCK_SIZE - 1;


    private static final Holder MOVED = new Holder();

    private volatile Column[] table;
    private volatile Column[] nextTable;
    private AtomicInteger sizeCtl = new AtomicInteger(-1);


    protected T initialValue() {
        return null;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        MyThread t = MyThread.currentThread();
        Column[] tab = table;

        int i = index(t);
        int c = column(i);

        Column column = atomicGetOrCreate(tab, c);


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

    private Holder[] getSegment(int index) {

    }

    private Holder find(MyThread t) {
        int i = t.index();

        for (Holder[][] tab = table; ; ) {
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
                        int n = newLength(index + 1);

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
                        int n = len < INITIAL_CAPACITY ? newLength(len) : INITIAL_CAPACITY;
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

    private static int index(MyThread t) {
        return t.index();
    }

    private static int column(int index) {
        return index >>> BLOCK_BITS;
    }

    private static int newLength(int requiredLength) {
        return 1 << (32 - Integer.numberOfLeadingZeros(requiredLength));
    }

    private static class Column {
        private final Holder[] holders;

        private Column(Holder[] holders) {
            this.holders = holders;
        }
    }

    private static class Holder {
        private WeakReference<MyThread> ref;
        private Object value;
    }

    private static class ConcurrentArrayList {
        private static final long ADDR_ABASE;
        private static final int ASHIFT;
        private static final sun.misc.Unsafe UNSAFE;

        static {
            try {
//            /* Security Error */
//            UNSAFE = sun.misc.Unsafe.getUnsafe();
//
/* Replace it { */
                Field singleoneInstanceField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                singleoneInstanceField.setAccessible(true);

                UNSAFE = (sun.misc.Unsafe) singleoneInstanceField.get(null);
/* } Replace it */

                Class<?> arrayClass = Column[].class;

                ADDR_ABASE = UNSAFE.arrayBaseOffset(arrayClass);
                int scale = UNSAFE.arrayIndexScale(arrayClass);

                if ((scale & (scale - 1)) != 0) {
                    throw new Error("data type scale not a power of two");
                }

                ASHIFT = newLength(scale) - 1;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                System.out.println("AAAAAAAAA!!!!");
                throw new Error(e);
            }
        }

        private int initialSize;
        private Column[] list;
        private Column[] prototype;
        private AtomicInteger sizeCtl;

        private ConcurrentArrayList(int initialSize) {
            this.initialSize = initialSize;
            this.list = null;
            this.prototype = null;
            this.sizeCtl = new AtomicInteger(-1);
        }

        private Column get(int i) {
            for (Column[] tab = list; ; ) {
                Holder holder;

                if (tab == null) {
                    tab = init(i);
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

        private Column ensure(int i) {

        }

        private Column[] extend(int index) {
            Column[] tab = list;
            Column[] nextTab;

            while (tab.length <= index) {
                while ((nextTab = prototype) == null) {
                    if (sizeCtl.get() >= 0) {
                        Thread.yield(); // lost initialization race; just spin
                    } else if (sizeCtl.compareAndSet(-1, 0)) {
                        if ((nextTab = prototype) == null) {
                            int n = newLength(index + 1);

                            if (n < 0) {
                                n = Integer.MAX_VALUE;
                            }

                            prototype = nextTab = new Column[n];
                        }
                        break;
                    }
                }

                try {
                    transfer(tab, nextTab, 0);
                } finally {
                    sizeCtl.set(-1);
                }

                list = tab = nextTab;
                prototype = null;
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

        private Column[] init(int len) {
            Column[] tab;

            while ((tab = list) == null) {
                if (sizeCtl.get() >= 0) {
                    Thread.yield(); // lost initialization race; just spin
                } else if (sizeCtl.compareAndSet(-1, 0)) {
                    try {
                        if ((tab = list) == null) {
                            int n = len < initialSize ? newLength(len) : initialSize;
                            list = tab = new Column[n];
                        }
                    } finally {
                        sizeCtl.set(-1);
                    }
                    break;
                }
            }

            return tab;
        }

        private static Column atomicGet(Column[] tab, int index) {
            return (Column) UNSAFE.getObjectVolatile(tab, addr(index));
        }

        private static Column atomicGetOrCreate(Column[] tab, int index) {
            Column column = atomicGet(tab, index);

            if (column == null) {
                column = new Column(new Holder[BLOCK_SIZE]);

                while (!atomicSet(tab, index, column)) ;

                column = atomicGet(tab, index);
            }

            return column;
        }

        private static boolean atomicSet(Column[] tab, int index, Column value) {
            return UNSAFE.compareAndSwapObject(tab, addr(index), null, value);
        }

        private static long addr(int index) {
            return ((long) index << ASHIFT) + ADDR_ABASE;
        }
    }
}
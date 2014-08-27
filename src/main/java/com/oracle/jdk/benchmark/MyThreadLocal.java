package com.oracle.jdk.benchmark;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Operational cases:
 * <dl compact>
 * <dt>INIT</dt>
 * <dd>The table is not yet initialized</dd>
 * <dt>EMPTY</dt>
 * <dd>The thread cell in the table is empty. It happened either after {@link #remove()} invocation or
 * no value has yet been set</dd>
 * <dt>SET</dt>
 * <dd>The thread cell is in use by current thread</dd>
 * <dt>NEED_COLUMN</dt>
 * <dd>A row in which the thread cell should be allocated are not yet initialized in the table</dd>
 * <dt>NEED_EXTENSION</dt>
 * <dd>The table is needed to be extended</dd>
 * </dl>
 *
 * @author Victor Polischuk
 * @since 27.10.13 6:30
 */
public class MyThreadLocal<T> {
    private static final int INITIAL_CAPACITY = 1 << 4; //TODO: increase it after tests
    private static final int BLOCK_BITS = 8;
    private static final int BLOCK_SIZE = 1 << (BLOCK_BITS - 1);
    private static final int BLOCK_MASK = BLOCK_SIZE - 1;

    private static final long ADDR_BASE;
    private static final int ADDR_SHIFT;
    private static final sun.misc.Unsafe UNSAFE;

    static {
        try {
//            /* Security Error */
//            UNSAFE = sun.misc.Unsafe.getUnsafe();  //TODO: replace when/if integrated
//
/* Replace it { */
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);

            UNSAFE = (sun.misc.Unsafe) unsafeField.get(null);
/* } Replace it */

            Class<?> arrayClass = Holder[][].class;

            ADDR_BASE = UNSAFE.arrayBaseOffset(arrayClass);
            int scale = UNSAFE.arrayIndexScale(arrayClass);

            if ((scale & (scale - 1)) != 0) {
                throw new Error("data type scale not a power of two");
            }

            ADDR_SHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            System.out.println("AAAAAAAAA!!!!");
            throw new Error(e);
        }
    }

    private final ConcurrentArrayList list = new ConcurrentArrayList(INITIAL_CAPACITY);

    protected T initialValue() {
        return null;
    }

    @SuppressWarnings("unchecked unused")
    public T get() {
        MyThread t = MyThread.currentThread();

        int i = t.index();

        int r = row(i);
        int c = cell(i);

        Holder[] row = list.ensure(r);

        Holder holder = row[c];

        if (holder != null && holder.ref != null && holder.ref.get() == t) {
            return (T) holder.value;
        }

        //t.myThreadLocals.put(this, null);
        T v = initialValue();

        row[c] = holder = new Holder(t);

        if (v != null) {
            holder.value = v;
        }

        return v;
    }

    @SuppressWarnings("unused")
    public void set(T value) {
        MyThread t = MyThread.currentThread();

        int i = t.index();

        int r = row(i);
        int c = cell(i);

        Holder[] row = list.ensure(r);

        Holder holder = row[c];

        if (holder == null) {

        } else if (holder.ref != null && holder.ref.get() == t) {
            if (holder.value != value) {
                holder.value = value;
            }
            return;
        }

        //t.myThreadLocals.put(this, null);

        row[c] = holder = new Holder(t);

        if (value != null) {
            holder.value = value;
        }
    }

    @SuppressWarnings("unused")
    public void remove() {
        MyThread t = MyThread.currentThread();

        int i = t.index();

        int r = row(i);
        int c = cell(i);

        Holder[] row = list.get(r);

        if (row != null && row[c] != null) {
//            t.myThreadLocals.remove(this);
            row[c] = null;
        }
    }

    @SuppressWarnings("unused")
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    private static int row(int index) {
        return index >>> (BLOCK_BITS - 1);
    }

    private static int cell(int index) {
        return index & BLOCK_MASK;
    }

    private static class Table {
        private final int length;
        private final Holder[][] rows;

        private Table(int length) {
            this.length = length;
            this.rows = new Holder[length][];
        }

        private Holder[] get(int i) {
//            return rows[i];
            return atomicGet(rows, i);
        }

        private Holder[] create(int i) {
//            Row[] tab = rows;
//            Row row;
//
//            if (tab[i] == null) {
//                row = tab[i] = new Row(length);
//            } else {
//                row = tab[i];
//            }
//            return row;

            Holder[] r = new Holder[BLOCK_SIZE];

            if (atomicSet(rows, i, r)) {
                return r;
            }

            return atomicGet(rows, i);
        }

        private void copy(Holder[] r, int i) {
            if (rows[i] == null) {
                atomicSet(rows, i, r);
            }
        }

        private static Holder[] atomicGet(Holder[][] a, int i) {
            return (Holder[]) UNSAFE.getObjectVolatile(a, address(i));
        }

        private static boolean atomicSet(Holder[][] a, int i, Holder[] value) {
            return UNSAFE.compareAndSwapObject(a, address(i), null, value);
        }

        private static long address(int i) {
            return ((long) i << ADDR_SHIFT) + ADDR_BASE;
        }
    }

    private static class Holder {
        private final WeakReference<MyThread> ref;
        private Object value;

        private Holder(MyThread t) {
            this.ref = new WeakReference<MyThread>(t);
        }
    }

    private static class ConcurrentArrayList {
        private final int initialSize;
        private final AtomicInteger control;
        private final Lock lock = new ReentrantLock();
        private volatile Table table;
        private volatile Table prototype;

        private ConcurrentArrayList(int initialSize) {
            this.table = null;
            this.prototype = null;
            this.initialSize = initialSize;
            this.control = new AtomicInteger();
        }

        private Holder[] get(int i) {
            Table tab = table;

            if (tab != null && i < tab.length) {
                return tab.get(i);
            }

            return null;
        }

        private Holder[] ensure(int i) {
            Table tab = table;
            Holder[] row;

            int reqLen = i + 1;

            if (tab == null) {
                tab = init(reqLen);
                row = tab.create(i);
            } else if (tab.length < reqLen) {
                tab = extendTo(reqLen);
                row = tab.create(i);
            } else if ((row = tab.get(i)) == null) {
                row = tab.create(i);
            }

            Table newTab = prototype;

            if (newTab != tab) {
                newTab.copy(row, i);
            }

            return row;
        }

        private Table init(int reqLen) {
            /*
            Table tab = table;

            for (; tab == null; tab = table) {
                if (control.get() != 0) {
                    Thread.yield(); // lost initialization race; yield
                } else if (control.compareAndSet(0, 1)) {
                    try {
                        if (table == null) {
                            int size = reqLen < initialSize ? newLength(reqLen) : initialSize;

                            prototype = table = new Table(size);
                        }
                    } finally {
                        control.set(0);
                    }
                }
            }
            */
            Table tab;

            while ((tab = table) == null) {
                if (lock.tryLock()) {
                    try {
                        if (table == null) {
                            int len = reqLen > initialSize ? newLength(reqLen) : initialSize;

                            System.out.println("# !!!!!!!!!!!!!!!!!!!!!! init " + len + " !!!!!!!!!!!!!!!!!!!!!!!!!!!!!#");

                            prototype = table = new Table(len);
                        }
                    } finally {
                        lock.unlock();
                    }
                } else {
                    Thread.yield();
                }
            }

            return tab;
        }

        private Table extendTo(int reqLen) {
            while (prototype.length < reqLen) {
                if (lock.tryLock()) {
                    try {
                        if (prototype.length < reqLen) {
                            int len = newLength(reqLen);

                            System.out.println("# !!!!!!!!!!!!!!!!!!!!!! extendTo " + len + " !!!!!!!!!!!!!!!!!!!!!!!!!!!!!#");

                            prototype = new Table(len);
                        }
                    } finally {
                        lock.unlock();
                    }
                } else {
                    Thread.yield();
                }
            }

            /*
            while (prototype.length < reqLen) {
                if (control.get() != 0) {
                    Thread.yield(); // lost initialization race; yield
                } else if (control.compareAndSet(0, 1)) {
                    try {
                        if (prototype.length < reqLen) {
                            int size = newLength(reqLen);

                            prototype = new Table(size);
                        }
                    } finally {
                        control.set(0);
                    }
                }
            }
              */
            Table tab;

            do {
                tab = prototype;
                int len = tab.length;

                for (int j = 0; j < len && tab == prototype && tab != table; j++) {
                    Holder[] r = table.get(j);

                    tab.copy(r, j);
                }
            } while (tab != prototype);

            table = tab;

            return tab;
        }

        private static int newLength(int reqLen) {
            return 1 << (32 - Integer.numberOfLeadingZeros(reqLen - 1));
        }
    }
}
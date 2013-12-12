package com.oracle.jdk.benchmark;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JavaDoc here
 *
 * @author Victor Polischuk
 * @since 27.10.13 6:30
 */
public class MyThreadLocal<T> {
    private static AtomicInteger nextHashCode = new AtomicInteger();
    private static final int HASH_INCREMENT = 0x61c88647;
    private static final int BLOCK_BITS = 5;
    private static final int BLOCK_SIZE = 1 << (BLOCK_BITS - 1);
    private static final int BLOCK_MASK = BLOCK_SIZE - 1;
    private final int threadLocalHashCode = nextHashCode();
    private volatile int blocks = 0;
    volatile Holder[] storage = null; //new Holder[BLOCK_SIZE + 1];

    /**
     * Returns the next hash code.
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    protected T initialValue() {
        return null;
    }

    public T get() {
        MyThread t = MyThread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            Entry e = map.getEntry(this);
            if (e != null)
                return (T) e.value;
        }
        return setInitialValue();
    }

    private T setInitialValue() {
        T value = initialValue();
        MyThread t = MyThread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }

    public void set(T value) {
        MyThread t = MyThread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }

    public void remove() {
        MyThread t = MyThread.currentThread();
        ThreadLocalMap m = getMap(t);
        if (m != null)
            m.remove(this);
    }

    ThreadLocalMap getMap(MyThread t) {
        return t.myThreadLocals;
    }

    void createMap(MyThread t, T firstValue) {
        t.myThreadLocals = new ThreadLocalMap(this, firstValue);
    }

    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    synchronized
    private void extend(int page) {
        int len = blocks;

        if (len < page) {
            Holder[] list = storage;

            for (; page != 0; page--) {
                list = list[BLOCK_SIZE].next;

                if (list[BLOCK_SIZE] == null) {
                    list[BLOCK_SIZE] = new Holder(BLOCK_SIZE + 1);
                    blocks++;
                }
            }
        }
    }

    WeakEntry remember(Object v) {
        Holder[] list = storage;
        MyThread t = MyThread.currentThread();
        int i = t.index;
        int len = blocks;
        int page = i >>> BLOCK_BITS;
        int offset = i & BLOCK_MASK;

        if (len < page) {
            extend(page);
        }

        for (; page != 0; page--) {
            list = list[BLOCK_SIZE].next;
        }

        Holder holder = list[offset];
        WeakReference<MyThread> ref = holder.ref;

        if (ref == null || ref.get() != t) {
            holder.ref = new WeakReference<>(t);
            holder.entry = new Entry(this, v);
        }

        return new WeakEntry(holder.entry);
    }

    private void forget(Entry e) {
        Holder[] list = storage;
        int i = MyThread.currentThread().index;
        int page = i >>> BLOCK_BITS;
        int offset = i & BLOCK_MASK;

        for (; page != 0; page--) {
            list = list[BLOCK_SIZE].next;
        }

        list[offset].ref = null;
        list[offset].entry = null;
    }

    private int index(int len) {
        return threadLocalHashCode & (len - 1);
    }

    static class Entry {
        final MyThreadLocal key;
        /**
         * The value associated with this ThreadLocal.
         */
        Object value;

        Entry(MyThreadLocal k, Object v) {
            this.key = k;
            this.value = v;
        }
    }

    private static class Holder {
        private final Holder[] next;
        private WeakReference<MyThread> ref;
        private Entry entry;

        private Holder(int size) {
            int fillTo = size - 1;
            next = new Holder[size];

            for (int i = 0; i < fillTo; i++) {
                next[i] = new Holder(null, null);
            }
        }

        private Holder(MyThread thread, Entry e) {
            entry = e;
            next = null;
            ref = new WeakReference<>(thread);
        }
    }

    private static class WeakEntry extends WeakReference<Entry> {
        private WeakEntry(Object referent) {
            super((Entry) referent);
        }
    }

    static class ThreadLocalMap {
        private static final int INITIAL_CAPACITY = 16;
        private WeakEntry[] table;
        private int size = 0;
        private int threshold; // Default to 0

        private static int nextIndex(int i, int len) {
            return (i + 1) & (len - 1);
            //            return ((i + 1 < len) ? i + 1 : 0);
            //            return ((++i < len) ? i : 0);
        }

        private static int prevIndex(int i, int len) {
            return (i - 1) & (len - 1);
            //            return ((i - 1 >= 0) ? i - 1 : len - 1);
            //            return ((--i >= 0) ? i : len - 1);
        }

        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        ThreadLocalMap(MyThreadLocal firstKey, Object firstValue) {
            table = new WeakEntry[INITIAL_CAPACITY];
            int i = firstKey.index(INITIAL_CAPACITY);
            table[i] = firstKey.remember(firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            WeakEntry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new WeakEntry[len];

            for (int j = 0; j < len; j++) {
                Entry e;
                WeakEntry ref = parentTable[j];
                if (ref != null && (e = ref.get()) != null) {
                    MyThreadLocal key = e.key;
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        int h = key.index(len);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = key.remember(value);
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         *
         * @param key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(MyThreadLocal key) {
            WeakEntry[] tab = table;
            int len = tab.length;
            int i = key.index(len);

            for (WeakEntry ref = tab[i];
                 ref != null;
                 ref = tab[i = nextIndex(i, len)]) {
                Entry e = ref.get();

                if (e != null && e.key == key) {
                    return e;
                }

                if (e == null) {
                    expungeStaleEntry(i);
                }
            }

            return null;
        }

        /**
         * Set the value associated with key.
         *
         * @param key   the thread local object
         * @param value the value to be set
         */
        private void set(MyThreadLocal key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            WeakEntry[] tab = table;
            int len = tab.length;
            int i = key.index(len);

            for (WeakEntry ref = tab[i];
                 ref != null;
                 ref = tab[i = nextIndex(i, len)]) {
                Entry e = ref.get();

                if (e != null && e.key == key) {
                    e.value = value;
                    return;
                }

                if (e == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            tab[i] = key.remember(value);
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         */
        private void remove(MyThreadLocal key) {
            WeakEntry[] tab = table;
            int len = tab.length;
            int i = key.index(len);

            for (WeakEntry ref = tab[i];
                 ref != null;
                 ref = tab[i = nextIndex(i, len)]) {
                Entry e = ref.get();
                if (e != null && e.key == key) {
                    ref.clear();
                    key.forget(e);
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         * <p/>
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         *
         * @param key       the key
         * @param value     the value to be associated with key
         * @param staleSlot index of the first stale entry encountered while
         *                  searching for key.
         */
        private void replaceStaleEntry(MyThreadLocal key, Object value,
                                       int staleSlot) {
            WeakEntry[] tab = table;
            int len = tab.length;
            Entry e;
            WeakEntry ref;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).
            int slotToExpunge = staleSlot;
            for (int i = prevIndex(staleSlot, len);
                 (ref = tab[i]) != null;
                 i = prevIndex(i, len)) {
                if (ref.get() == null) {
                    slotToExpunge = i;
                }
            }

            // Find either the key or trailing null slot of run, whichever
            // occurs first
            for (int i = nextIndex(staleSlot, len);
                 (ref = tab[i]) != null;
                 i = nextIndex(i, len)) {
                e = ref.get();

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                if (e == null && slotToExpunge == staleSlot) {
                    slotToExpunge = i;
                }
                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.
                else if (e != null && e.key == key) {
                    e.value = value;

                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = ref;

                    // Start expunge at preceding stale entry if it exists
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }
            }

            // If key not found, put new entry in stale slot
            tab[staleSlot] = key.remember(value);

            // If there are any other stale entries in run, expunge them
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot
         *         (all between staleSlot and this slot will have been checked
         *         for expunging).
         */
        private int expungeStaleEntry(int staleSlot) {
            WeakEntry[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            Entry e;
            WeakEntry ref;
            int i;
            for (i = nextIndex(staleSlot, len);
                 (ref = tab[i]) != null;
                 i = nextIndex(i, len)) {
                e = ref.get();
                if (e == null) {
                    tab[i] = null;
                    size--;
                } else {
                    int h = e.key.index(len);
                    if (h != i) {
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = ref;
                    }
                }
            }
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         *
         * @param i a position known NOT to hold a stale entry. The
         *          scan starts at the element after i.
         * @param n scan control: <tt>log2(n)</tt> cells are scanned,
         *          unless a stale entry is found, in which case
         *          <tt>log2(table.length)-1</tt> additional cells are scanned.
         *          When called from insertions, this parameter is the number
         *          of elements, but when from replaceStaleEntry, it is the
         *          table length. (Note: all this could be changed to be either
         *          more or less aggressive by weighting n instead of just
         *          using straight log n. But this version is simple, fast, and
         *          seems to work well.)
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            WeakEntry[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                WeakEntry ref = tab[i];
                if (ref != null && ref.get() == null) {
                    n = len;
                    removed = true;
                    i = expungeStaleEntry(i);
                }
            } while ((n >>>= 1) != 0);
            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        private void resize() {
            WeakEntry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            WeakEntry[] newTab = new WeakEntry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                WeakEntry ref = oldTab[j];
                if (ref != null) {
                    Entry e = ref.get();
                    if (e != null) {
                        int h = e.key.index(newLen);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = ref;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         */
        private void expungeStaleEntries() {
            WeakEntry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                WeakEntry ref = tab[j];
                if (ref != null && ref.get() == null)
                    expungeStaleEntry(j);
            }
        }

        void cleanup(int index) {
            WeakEntry[] tab = table;
            int len = tab.length;

            for (int j = 0; j < len; j++) {
                Entry e;
                WeakEntry ref = tab[j];

                if (ref != null && (e = ref.get()) != null) {
                    e.key.forget(e);
                    ref.clear();
                }

                tab[j] = null;
            }

            tab = null;
        }
    }
}

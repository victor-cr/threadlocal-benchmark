package com.oracle.jdk.benchmark;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JavaDoc here
 *
 * @author Victor Polischuk
 * @since 27.10.13 6:30
 */
public class MyThreadLocal<T> extends ThreadLocal<T> {
    private static AtomicInteger nextHashCode = new AtomicInteger();
    private static final int HASH_INCREMENT = 0x61c88647;
    private static final int LOCKS_COUNT = 15; // !!!! power 2 - 1
    private final int threadLocalHashCode = nextHashCode();
    private final Lock[] locks = new Lock[LOCKS_COUNT];
    private final Node fallback = new Node();

    public MyThreadLocal() {
        for (int i = 0; i < LOCKS_COUNT; i++) {
            locks[i] = new Lock();
        }
    }

    /**
     * Returns the next hash code.
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
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
        return t.threadLocals;
    }

    void createMap(MyThread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    private Entry link(Node r, Object v) {
        Entry e = new Entry(this, v, r, r.next);
        r.next.prev = e;
        r.next = e;
        return e;
    }

    private void unlink(Node n) {
        n.next.prev = n.prev;
        n.prev.next = n.next;
        n.next = null;
        n.prev = null;
    }

    private Entry remember(Object v) {
        MyThread t = MyThread.currentThread();
        int start = t.hashCode() & LOCKS_COUNT;
//        int end = (start + LOCKS_COUNT - 1) & LOCKS_COUNT;
//
//        for (int i = start; i != end; i = (i + 1) & LOCKS_COUNT) {
//            Lock lock = locks[i];
//
//            if (lock.tryLock()) {
//                try {
//                    return link(lock.root, v);
//                } finally {
//                    lock.unlock();
//                }
//            }
//        }
//
//        synchronized (fallback) {
//            return link(fallback, v);
//        }
        Lock lock = locks[start];

        lock.lock();

        try {
            return link(lock.root, v);
        } finally {
            lock.unlock();
        }
    }

    private void forget(Entry e) {
        MyThread t = MyThread.currentThread();
        int start = t.hashCode() & LOCKS_COUNT;

        Lock lock = locks[start];

        lock.lock();

        try {
            unlink(e);
        } finally {
            lock.unlock();
        }
    }

    private static class Lock extends ReentrantLock {
        private final Node root = new Node();
    }

    private static class Node {
        volatile Node prev = this;
        volatile Node next = this;
    }

    static class Entry extends Node {
        final MyThreadLocal key;
        /**
         * The value associated with this ThreadLocal.
         */
        Object value;

        Entry(MyThreadLocal k, Object v, Node p, Node n) {
            this.key = k;
            value = v;
            prev = p;
            next = n;
        }
    }

    static class ThreadLocalMap {
        private static final int INITIAL_CAPACITY = 16;
        private WeakReference<Entry>[] table;
        private int size = 0;
        private int threshold; // Default to 0

        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        private static int nextIndex(int i, int len) {
            return ((++i < len) ? i : 0);
        }

        private static int prevIndex(int i, int len) {
            return ((--i >= 0) ? i : len - 1);
        }

        ThreadLocalMap(MyThreadLocal firstKey, Object firstValue) {
            table = new WeakReference[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new WeakReference<>(firstKey.remember(firstValue));
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
            WeakReference<Entry>[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new WeakReference[len];

            for (int j = 0; j < len; j++) {
                Entry e;
                WeakReference<Entry> ref = parentTable[j];
                if (ref != null && (e = ref.get()) != null) {
                    MyThreadLocal key = e.key;
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        e = key.remember(value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = new WeakReference<>(e);
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
            WeakReference<Entry>[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len - 1);
            WeakReference<Entry> ref = tab[i];

            while (ref != null) {
                Entry e = ref.get();

                if (e == null) {
                    expungeStaleEntry(i);
                } else if (e.key == key) {
                    return e;
                } else {
                    i = nextIndex(i, len);
                }
                ref = tab[i];
            }
            return null;
        }

//        private Entry getEntry(MyThreadLocal key) {
//            int i = key.threadLocalHashCode & (table.length - 1);
//            Entry e;
//            WeakReference<Entry> ref = table[i];
//            if (ref != null && (e = ref.get()) != null && e.key == key)
//                return e;
//            else
//                return getEntryAfterMiss(key, i, ref);
//        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         *
         * @param key the thread local object
         * @param i   the table index for key's hash code
         * @param ref the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(MyThreadLocal key, int i, WeakReference<Entry> ref) {
            WeakReference<Entry>[] tab = table;
            int len = tab.length;

            while (ref != null) {
                Entry e = ref.get();

                if (e == null) {
                    expungeStaleEntry(i);
                } else if (e.key == key) {
                    return e;
                } else {
                    i = nextIndex(i, len);
                }
                ref = tab[i];
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

            WeakReference<Entry>[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len - 1);

            for (WeakReference<Entry> ref = tab[i];
                 ref != null;
                 ref = tab[i = nextIndex(i, len)]) {
                Entry k = ref.get();

                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }

                if (k.key == key) {
                    k.value = value;
                    return;
                }
            }

            tab[i] = new WeakReference<>(key.remember(value));
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         */
        private void remove(MyThreadLocal key) {
            WeakReference<Entry>[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len - 1);

            for (WeakReference<Entry> ref = tab[i];
                 ref != null;
                 ref = tab[i = nextIndex(i, len)]) {
                Entry e = ref.get();
                if (e != null && e.key == key) {
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
            WeakReference<Entry>[] tab = table;
            int len = tab.length;
            Entry e;
            WeakReference<Entry> ref;

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
            tab[staleSlot] = new WeakReference<>(key.remember(value));

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
            WeakReference<Entry>[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            Entry e;
            WeakReference<Entry> ref;
            int i;
            for (i = nextIndex(staleSlot, len);
                 (ref = tab[i]) != null;
                 i = nextIndex(i, len)) {
                e = ref.get();
                if (e == null) {
                    tab[i] = null;
                    size--;
                } else {
                    int h = e.key.threadLocalHashCode & (len - 1);
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
            WeakReference<Entry>[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                WeakReference<Entry> ref = tab[i];
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
            WeakReference<Entry>[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            WeakReference<Entry>[] newTab = new WeakReference[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                WeakReference<Entry> ref = oldTab[j];
                if (ref != null) {
                    Entry e = ref.get();
                    if (e != null) {
                        int h = e.key.threadLocalHashCode & (newLen - 1);
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
            WeakReference<Entry>[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                WeakReference<Entry> ref = tab[j];
                if (ref != null && ref.get() == null)
                    expungeStaleEntry(j);
            }
        }

        void clean() { // TODO: Optimize as bulk removal
            WeakReference<Entry>[] tab = table;
            int len = tab.length;

            for (int i = 0; i < len; ++i) {
                WeakReference<Entry> ref = tab[i];
                if (ref != null) {
                    Entry e = ref.get();
                    if (e != null) {
                        e.key.remove();
                    }
                }
            }
        }
    }
}

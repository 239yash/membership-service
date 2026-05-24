package com.work.membership_service.concurrency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// per-key reentrant locks with reference-counted eviction
// pattern:
//   try (LockHandle h = registry.acquire(userId)) {
//       // critical section serialized for this user
//   }
//
// why ref-counting: a plain ConcurrentHashMap<K, Lock> would grow forever
// with one entry per user we ever saw. we remove the entry when refCount hits zero.
@Component
@Slf4j
public class StripedLockRegistry {

    // map of key -> lock entry; thread-safe by construction
    private final ConcurrentHashMap<Long, Entry> locks = new ConcurrentHashMap<>();

    public LockHandle acquire(Long key) {
        Entry entry = locks.compute(key, (k, existing) -> {
            Entry e = existing == null ? new Entry() : existing;
            e.refCount++;
            return e;
        });
        entry.lock.lock();
        log.debug("[striped_lock] acquired key: {}, refCount: {}", key, entry.refCount);
        return new LockHandle(key, entry);
    }

    // exposed for tests / debugging
    public int size() {
        return locks.size();
    }

    // one lock entry per key
    private static final class Entry {
        // fair lock prevents starvation when many threads contend on the same key
        final ReentrantLock lock = new ReentrantLock(true);
        int refCount;
    }

    // returned to the caller, released via try-with-resources
    public final class LockHandle implements AutoCloseable {

        private final Long key;
        private final Entry entry;
        private boolean released;

        private LockHandle(Long key, Entry entry) {
            this.key = key;
            this.entry = entry;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            entry.lock.unlock();
            // decrement refcount; remove map entry when no one holds or waits on it
            locks.compute(key, (k, v) -> {
                if (v == null) {
                    return null;
                }
                v.refCount--;
                return v.refCount > 0 ? v : null;
            });
            log.debug("[striped_lock] released key: {}", key);
        }
    }
}

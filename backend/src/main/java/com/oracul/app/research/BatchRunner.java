package com.oracul.app.research;

import com.oracul.app.chatgpt.CallAbandonedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the batches of one pass on a bounded executor: tasks start in index order, at most {@code concurrency} in flight,
 * results are kept per batch index. A failing task sets the abort flag before it frees its slot, so no task that has not
 * started yet starts and no running task sends another request ({@code gate}); in-flight tasks are awaited. The failure
 * of the lowest batch index is rethrown.
 */
final class BatchRunner {

    private static final AtomicInteger POOLS = new AtomicInteger();

    /** A task stopped because another task failed; never reported itself. */
    private static final class PeerAborted extends RuntimeException {
        PeerAborted() {
            super("aborted", null, false, false);
        }
    }

    interface Task<T> {
        /** {@code gate} must be run before every request of the task. */
        T run(int index, Runnable gate);
    }

    private BatchRunner() {
    }

    static <T> List<T> run(int concurrency, int count, Runnable guard, Task<T> task) {
        AtomicBoolean abort = new AtomicBoolean();
        Runnable gate = () -> {
            if (abort.get()) {
                throw new PeerAborted();
            }
            guard.run();
        };
        List<T> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(null);
        }
        RuntimeException[] failures = new RuntimeException[count];
        CountDownLatch done = new CountDownLatch(count);
        int pool = POOLS.incrementAndGet();
        AtomicInteger threads = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "events-" + pool + "-" + threads.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(concurrency, count)), factory);
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                executor.execute(() -> {
                    try {
                        if (!abort.get()) {
                            T value = task.run(index, gate);
                            synchronized (results) {
                                results.set(index, value);
                            }
                        }
                    } catch (PeerAborted e) {
                        // another task failed first
                    } catch (RuntimeException e) {
                        failures[index] = e;
                        abort.set(true);
                    } catch (Throwable e) {
                        failures[index] = new IllegalStateException(e);
                        abort.set(true);
                    } finally {
                        done.countDown();
                    }
                });
            }
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                abort.set(true);
                executor.shutdownNow();
                throw new CallAbandonedException();
            }
        } finally {
            executor.shutdown();
        }
        for (RuntimeException failure : failures) {
            if (failure != null) {
                throw failure;
            }
        }
        synchronized (results) {
            return new ArrayList<>(results);
        }
    }
}

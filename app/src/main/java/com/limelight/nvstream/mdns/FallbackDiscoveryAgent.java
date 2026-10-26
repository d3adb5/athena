package com.limelight.nvstream.mdns;

import com.limelight.LimeLog;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keep native discovery, adding direct mDNS when it fails or returns no hosts. */
public final class FallbackDiscoveryAgent extends MdnsDiscoveryAgent {
    public interface Factory {
        MdnsDiscoveryAgent create(MdnsDiscoveryListener listener);
    }

    private final Factory primaryFactory;
    private final Factory fallbackFactory;
    private final long fallbackDelayMs;
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    // Service callbacks perform blocking host probes. Keep them off the UI and
    // serialize lifecycle changes with callbacks without nesting agent locks.
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
    private MdnsDiscoveryAgent primary;
    private MdnsDiscoveryAgent fallback;
    private ScheduledFuture<?> fallbackTask;
    private int intervalMs;
    private boolean foundComputer;

    public FallbackDiscoveryAgent(MdnsDiscoveryListener listener, Factory primaryFactory,
                                  Factory fallbackFactory) {
        this(listener, primaryFactory, fallbackFactory, 10000);
    }

    FallbackDiscoveryAgent(MdnsDiscoveryListener listener, Factory primaryFactory,
                           Factory fallbackFactory, long fallbackDelayMs) {
        super(listener);
        this.primaryFactory = primaryFactory;
        this.fallbackFactory = fallbackFactory;
        this.fallbackDelayMs = fallbackDelayMs;
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    private void submit(Runnable task) {
        try {
            worker.execute(task);
        } catch (RejectedExecutionException ignored) {
            // A callback may already be in flight when the service is destroyed.
        }
    }

    private MdnsDiscoveryListener callbacks(int token, boolean fromPrimary) {
        return new MdnsDiscoveryListener() {
            @Override
            public void notifyComputerAdded(MdnsComputer computer) {
                submit(() -> {
                    if (generation.get() != token) {
                        return;
                    }
                    foundComputer = true;
                    if (fallbackTask != null) {
                        fallbackTask.cancel(false);
                        fallbackTask = null;
                    }
                    synchronized (computers) {
                        if (!computers.add(computer)) {
                            return;
                        }
                    }
                    listener.notifyComputerAdded(computer);
                });
            }

            @Override
            public void notifyDiscoveryFailure(Exception e) {
                submit(() -> {
                    if (generation.get() != token) {
                        return;
                    }
                    LimeLog.warning("mDNS: " + (fromPrimary ? "native" : "direct") +
                            " discovery failed: " + e);
                    if (fromPrimary) {
                        startFallback(token);
                    } else {
                        listener.notifyDiscoveryFailure(e);
                    }
                });
            }
        };
    }

    private void startFallback(int token) {
        if (generation.get() != token || fallback != null) {
            return;
        }
        LimeLog.info("mDNS: Trying direct discovery alongside native discovery");
        try {
            fallback = fallbackFactory.create(callbacks(token, false));
            fallback.startDiscovery(intervalMs);
        } catch (RuntimeException e) {
            closeAgent(fallback);
            fallback = null;
            listener.notifyDiscoveryFailure(e);
        }
    }

    @Override
    public void startDiscovery(int discoveryIntervalMs) {
        if (closed.get()) {
            return;
        }
        final int token = generation.incrementAndGet();
        submit(() -> {
            stopAgents();
            if (closed.get() || generation.get() != token) {
                return;
            }
            intervalMs = discoveryIntervalMs;
            foundComputer = false;
            synchronized (computers) {
                computers.clear();
            }
            fallbackTask = worker.schedule(() -> {
                if (!foundComputer) {
                    startFallback(token);
                }
            }, fallbackDelayMs, TimeUnit.MILLISECONDS);
            try {
                primary = primaryFactory.create(callbacks(token, true));
                primary.startDiscovery(intervalMs);
            } catch (RuntimeException e) {
                LimeLog.warning("mDNS: Unable to start native discovery: " + e);
                startFallback(token);
            }
        });
    }

    private static void closeAgent(MdnsDiscoveryAgent agent) {
        if (agent != null) {
            try {
                agent.close();
            } catch (RuntimeException e) {
                LimeLog.warning("mDNS: Discovery cleanup failed: " + e);
            }
        }
    }

    private void stopAgents() {
        if (fallbackTask != null) {
            fallbackTask.cancel(false);
            fallbackTask = null;
        }
        closeAgent(primary);
        closeAgent(fallback);
        primary = null;
        fallback = null;
    }

    @Override
    public void stopDiscovery() {
        generation.incrementAndGet();
        submit(this::stopAgents);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        generation.incrementAndGet();
        // Shut down only after cleanup has executed, so executor shutdown
        // policies cannot cancel queued resolver cleanup on Android runtimes.
        submit(() -> {
            try {
                stopAgents();
            } finally {
                worker.shutdown();
            }
        });
    }
}

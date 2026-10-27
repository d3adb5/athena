package com.limelight.nvstream.mdns;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.net.InetAddress;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 36})
public class NsdManagerDiscoveryAgentTest {
    private Context context;
    private NsdManager nsd;
    private MdnsDiscoveryListener observer;
    private NsdManagerDiscoveryAgent agent;
    private NsdServiceInfo service;
    private final BlockingQueue<NsdManager.DiscoveryListener> discoveries = new LinkedBlockingQueue<>();
    private final BlockingQueue<Registration> registrations = new LinkedBlockingQueue<>();
    private final AtomicBoolean failNextStart = new AtomicBoolean();

    private static final class Registration {
        final Executor executor;
        final NsdManager.ServiceInfoCallback callback;

        Registration(Executor executor, NsdManager.ServiceInfoCallback callback) {
            this.executor = executor;
            this.callback = callback;
        }

        void update(NsdServiceInfo service) {
            executor.execute(() -> callback.onServiceUpdated(service));
        }
    }

    @Before
    public void setup() throws Exception {
        context = mock(Context.class);
        nsd = mock(NsdManager.class);
        observer = mock(MdnsDiscoveryListener.class);
        when(context.getSystemService(NsdManager.class)).thenReturn(nsd);
        doAnswer(call -> {
            if (failNextStart.getAndSet(false)) throw new IllegalStateException("NSD start failed");
            discoveries.add(call.getArgument(2));
            return null;
        }).when(nsd).discoverServices(anyString(), anyInt(), any(NsdManager.DiscoveryListener.class));
        doAnswer(call -> {
            registrations.add(new Registration(call.getArgument(1), call.getArgument(2)));
            return null;
        }).when(nsd).registerServiceInfoCallback(any(NsdServiceInfo.class), any(Executor.class),
                any(NsdManager.ServiceInfoCallback.class));
        service = mock(NsdServiceInfo.class);
        when(service.getServiceName()).thenReturn("Hermes");
        when(service.getPort()).thenReturn(47989);
        when(service.getHostAddresses()).thenReturn(Collections.singletonList(InetAddress.getByName("192.168.1.10")));
        agent = new NsdManagerDiscoveryAgent(context, observer);
    }

    @After
    public void cleanup() throws Exception {
        agent.close();
        terminated(agent);
    }

    private NsdManager.DiscoveryListener start(NsdManagerDiscoveryAgent target) throws Exception {
        target.startDiscovery(1000);
        NsdManager.DiscoveryListener discovery = take(discoveries);
        discovery.onDiscoveryStarted("_nvstream._tcp");
        return discovery;
    }

    private Registration found(NsdManager.DiscoveryListener discovery) throws Exception {
        discovery.onServiceFound(service);
        return take(registrations);
    }

    @Test
    public void frameworkCanDispatchUnregistrationAndUpdatesAfterWorkerHasTerminated() throws Exception {
        Registration registration = found(start(agent));
        agent.close();
        terminated(agent);

        // This is the reported crash: NsdManager's ServiceHandler invokes the
        // Executor it saved earlier AFTER asynchronous unregister and shutdown.
        registration.executor.execute(registration.callback::onServiceInfoCallbackUnregistered);
        registration.update(service);
        registration.executor.execute(() -> registration.callback.onServiceInfoCallbackRegistrationFailed(1));
        verifyNoInteractions(observer);
        verify(nsd).unregisterServiceInfoCallback(registration.callback);
    }

    @Test
    public void activeServiceUpdatesStillReachTheHostList() throws Exception {
        Registration registration = found(start(agent));
        registration.update(service);
        drain(agent);
        verify(observer).notifyComputerAdded(argThat(pc -> pc.getName().equals("Hermes") && pc.getPort() == 47989));
        verify(observer, never()).notifyDiscoveryFailure(any());
    }

    @Test
    public void oldUpdatesAndFailuresCannotAffectRestartedDiscovery() throws Exception {
        NsdManager.DiscoveryListener oldDiscovery = start(agent);
        Registration old = found(oldDiscovery);
        agent.stopDiscovery();
        NsdManager.DiscoveryListener current = start(agent);
        Registration fresh = found(current);

        old.update(service);
        old.executor.execute(() -> old.callback.onServiceInfoCallbackRegistrationFailed(1));
        oldDiscovery.onDiscoveryStopped("_nvstream._tcp");
        drain(agent);
        verifyNoInteractions(observer);

        fresh.update(service);
        drain(agent);
        verify(observer).notifyComputerAdded(any(MdnsComputer.class));
        // A stale failure must not remove the replacement registration.
        current.onServiceFound(service);
        assertTrue(registrations.isEmpty());
    }

    @Test
    public void callbackQueuedBeforeCloseCannotPublishAfterClose() throws Exception {
        Registration registration = found(start(agent));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker(agent).execute(() -> {
            entered.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Test worker timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        try {
            registration.update(service);
            agent.close();
        } finally {
            release.countDown();
        }
        terminated(agent);
        verifyNoInteractions(observer);
    }

    @Test
    public void delayedDiscoveryStartedAfterCloseCannotCrashOrRestartAgent() throws Exception {
        agent.startDiscovery(1000);
        NsdManager.DiscoveryListener pending = take(discoveries);
        agent.close();
        doThrow(new IllegalArgumentException("Already stopped")).when(nsd).stopServiceDiscovery(pending);
        pending.onDiscoveryStarted("_nvstream._tcp");
        pending.onServiceFound(service);
        pending.onStartDiscoveryFailed("_nvstream._tcp", 1);
        agent.startDiscovery(1000);
        agent.close();
        assertTrue(discoveries.isEmpty());
        assertTrue(registrations.isEmpty());
        verifyNoInteractions(observer);
    }

    @Test
    public void realRegistrationFailureIsReportedAndAllowsAnotherRegistration() throws Exception {
        NsdManager.DiscoveryListener discovery = start(agent);
        Registration registration = found(discovery);
        registration.executor.execute(() -> registration.callback.onServiceInfoCallbackRegistrationFailed(1));
        drain(agent);
        verify(observer).notifyDiscoveryFailure(any(RuntimeException.class));
        assertNotNull(found(discovery));
    }

    @Test
    public void failedDiscoveryStartDoesNotBlockFutureAttempt() throws Exception {
        failNextStart.set(true);
        assertThrows(IllegalStateException.class, () -> agent.startDiscovery(1000));
        found(start(agent)).update(service);
        drain(agent);
        verify(observer).notifyComputerAdded(any(MdnsComputer.class));
    }

    @Test
    public void simultaneousFrameworkDispatchAndCloseCannotRejectTasks() throws Exception {
        Registration registration = found(start(agent));
        CountDownLatch begin = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread dispatch = new Thread(() -> {
            try {
                assertTrue(begin.await(3, TimeUnit.SECONDS));
                for (int i = 0; i < 1000; i++) {
                    registration.executor.execute(registration.callback::onServiceInfoCallbackUnregistered);
                }
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        dispatch.start();
        begin.countDown();
        agent.close();
        dispatch.join(3000);
        assertFalse("Framework dispatch deadlocked", dispatch.isAlive());
        assertNull("Framework thread received an exception", failure.get());
        terminated(agent);
    }

    @Test
    public void pairingStyleFallbackPauseAndResumeToleratesOldFrameworkEvents() throws Exception {
        AtomicReference<NsdManagerDiscoveryAgent> nativeAgent = new AtomicReference<>();
        FallbackDiscoveryAgent combined = new FallbackDiscoveryAgent(observer, listener -> {
            NsdManagerDiscoveryAgent created = new NsdManagerDiscoveryAgent(context, listener);
            nativeAgent.set(created);
            return created;
        }, listener -> new MdnsDiscoveryAgent(listener) {
            @Override public void startDiscovery(int interval) {}
            @Override public void stopDiscovery() {}
        }, 60000);
        try {
            combined.startDiscovery(1000);
            NsdManager.DiscoveryListener first = take(discoveries);
            first.onDiscoveryStarted("_nvstream._tcp");
            Registration old = found(first);
            NsdManagerDiscoveryAgent oldAgent = nativeAgent.get();
            combined.stopDiscovery(); // PcView pauses discovery before PIN pairing.
            verify(nsd, timeout(3000)).unregisterServiceInfoCallback(old.callback);
            terminated(oldAgent);
            old.executor.execute(old.callback::onServiceInfoCallbackUnregistered);
            old.update(service);

            combined.startDiscovery(1000); // Return to host list / reconnect.
            NsdManager.DiscoveryListener resumed = take(discoveries);
            resumed.onDiscoveryStarted("_nvstream._tcp");
            found(resumed).update(service);
            verify(observer, timeout(3000)).notifyComputerAdded(any(MdnsComputer.class));
            verify(observer, never()).notifyDiscoveryFailure(any());
        } finally {
            combined.close();
            ThreadPoolExecutor outerWorker = ReflectionHelpers.getField(combined, "worker");
            assertTrue(outerWorker.awaitTermination(3, TimeUnit.SECONDS));
            if (nativeAgent.get() != null) terminated(nativeAgent.get());
        }
    }

    private static <T> T take(BlockingQueue<T> queue) throws Exception {
        T value = queue.poll(3, TimeUnit.SECONDS);
        assertNotNull("Timed out waiting for framework registration", value);
        return value;
    }

    private static ThreadPoolExecutor worker(NsdManagerDiscoveryAgent target) {
        return ReflectionHelpers.getField(target, "executor");
    }

    private static void drain(NsdManagerDiscoveryAgent target) throws Exception {
        worker(target).submit(() -> {}).get(3, TimeUnit.SECONDS);
    }

    private static void terminated(NsdManagerDiscoveryAgent target) throws Exception {
        assertTrue("Closed NSD agent leaked its worker", worker(target).awaitTermination(3, TimeUnit.SECONDS));
    }
}

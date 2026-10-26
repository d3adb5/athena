package com.limelight.nvstream.mdns;

import org.junit.Test;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class FallbackDiscoveryAgentTest {
    private static class FakeAgent extends MdnsDiscoveryAgent {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        FakeAgent() { super(null); }
        MdnsDiscoveryAgent attach(MdnsDiscoveryListener listener) {
            this.listener = listener;
            return this;
        }
        @Override public void startDiscovery(int interval) { started.countDown(); }
        @Override public void stopDiscovery() { stopped.countDown(); }
        void emit(MdnsComputer pc) { listener.notifyComputerAdded(pc); }
        void fail() { listener.notifyDiscoveryFailure(new RuntimeException("NSD failure")); }
    }

    private static class Observer implements MdnsDiscoveryListener {
        final AtomicInteger count = new AtomicInteger();
        final CountDownLatch added;
        Observer() { this(1); }
        Observer(int results) { added = new CountDownLatch(results); }
        @Override public void notifyComputerAdded(MdnsComputer computer) {
            count.incrementAndGet();
            added.countDown();
        }
        @Override public void notifyDiscoveryFailure(Exception e) { fail(e.toString()); }
    }

    private static void await(CountDownLatch event) throws InterruptedException {
        assertTrue("Timed out waiting for discovery event", event.await(3, TimeUnit.SECONDS));
    }

    private static MdnsComputer pc() throws Exception {
        return new MdnsComputer("Hermes", InetAddress.getByName("192.168.1.10"), null, 47989);
    }

    @Test public void silentNativeDiscoveryStartsFallback() throws Exception {
        FakeAgent primary = new FakeAgent();
        FakeAgent fallback = new FakeAgent();
        Observer observer = new Observer();
        FallbackDiscoveryAgent agent = new FallbackDiscoveryAgent(observer,
                primary::attach, fallback::attach, 20);
        try {
            agent.startDiscovery(1000);
            await(primary.started);
            await(fallback.started);
            fallback.emit(pc());
            await(observer.added);
            assertEquals(1, observer.count.get());
            assertEquals(1, agent.getComputerSet().size());
        } finally { agent.close(); }
        await(primary.stopped);
        await(fallback.stopped);
    }

    @Test public void failureStartsFallbackWithoutWaitingForTimeout() throws Exception {
        FakeAgent primary = new FakeAgent();
        FakeAgent fallback = new FakeAgent();
        FallbackDiscoveryAgent agent = new FallbackDiscoveryAgent(new Observer(),
                primary::attach, fallback::attach, 60000);
        try {
            agent.startDiscovery(1000);
            await(primary.started);
            primary.fail();
            await(fallback.started);
        } finally { agent.close(); }
    }

    @Test public void nativeResultCancelsFallbackAndCallbacksStopWithDiscovery() throws Exception {
        FakeAgent primary = new FakeAgent();
        FakeAgent fallback = new FakeAgent();
        Observer observer = new Observer();
        FallbackDiscoveryAgent agent = new FallbackDiscoveryAgent(observer,
                primary::attach, fallback::attach, 300);
        try {
            agent.startDiscovery(1000);
            await(primary.started);
            primary.emit(pc());
            await(observer.added);
            assertFalse(fallback.started.await(400, TimeUnit.MILLISECONDS));
            agent.stopDiscovery();
            await(primary.stopped);
            primary.emit(pc()); // stale callback from the stopped backend
        } finally { agent.close(); }
        assertEquals(1, observer.count.get());
    }

    @Test public void stopBeforeTimeoutDoesNotStartFallback() throws Exception {
        FakeAgent primary = new FakeAgent();
        FakeAgent fallback = new FakeAgent();
        FallbackDiscoveryAgent agent = new FallbackDiscoveryAgent(new Observer(),
                primary::attach, fallback::attach, 300);
        try {
            agent.startDiscovery(1000);
            await(primary.started);
            agent.stopDiscovery();
            await(primary.stopped);
            assertFalse(fallback.started.await(400, TimeUnit.MILLISECONDS));
        } finally { agent.close(); }
    }

    @Test public void bothBackendsReportingSameHostOnlyNotifyOnce() throws Exception {
        FakeAgent primary = new FakeAgent();
        FakeAgent fallback = new FakeAgent();
        Observer observer = new Observer(2);
        FallbackDiscoveryAgent agent = new FallbackDiscoveryAgent(observer,
                primary::attach, fallback::attach, 20);
        try {
            agent.startDiscovery(1000);
            await(fallback.started);
            primary.emit(pc());
            fallback.emit(pc());
            // A different host is a queue barrier: the duplicate must have
            // been processed before this second distinct result is delivered.
            fallback.emit(new MdnsComputer("Other host", InetAddress.getByName("192.168.1.11"), null, 47989));
            await(observer.added);
            agent.stopDiscovery();
            await(primary.stopped);
            assertEquals(2, observer.count.get());
            assertEquals(2, agent.getComputerSet().size());
        } finally { agent.close(); }
    }
}

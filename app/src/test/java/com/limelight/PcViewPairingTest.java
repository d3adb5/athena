package com.limelight;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PcViewPairingTest {
    private AtomicBoolean active;
    private ComputerManagerService.ComputerManagerBinder binder;
    private PcView view;

    @Before
    public void setup() {
        active = ReflectionHelpers.getStaticField(PcView.class, "pairingInProgress");
        active.set(false);
        binder = mock(ComputerManagerService.ComputerManagerBinder.class);
        view = Robolectric.buildActivity(PcView.class).get();
        ReflectionHelpers.setField(view, "managerBinder", binder);
        ReflectionHelpers.setField(view, "inForeground", true);
    }

    @After
    public void cleanup() {
        active.set(false);
    }

    @Test
    public void closingMenuOrResumingDoesNotRestartPollingDuringPairing() {
        active.set(true);
        ReflectionHelpers.callInstanceMethod(view, "startComputerUpdates");
        verify(binder, never()).startPolling(any(ComputerManagerListener.class));
        active.set(false);
        ReflectionHelpers.callInstanceMethod(view, "startComputerUpdates");
        verify(binder).startPolling(any(ComputerManagerListener.class));
    }

    @Test
    public void secondTapDoesNotStartAnotherWorkerWhilePairingIsActive() {
        active.set(true);
        ComputerDetails computer = new ComputerDetails();
        computer.state = ComputerDetails.State.ONLINE;
        computer.activeAddress = new ComputerDetails.AddressTuple("127.0.0.1", 47989);
        ReflectionHelpers.callInstanceMethod(view, "doPair",
                from(ComputerDetails.class, computer), from(String.class, null), from(String.class, null));
        assertTrue(active.get());
        verifyNoInteractions(binder);
    }
}

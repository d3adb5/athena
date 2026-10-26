package com.limelight.binding.video;

import android.view.Surface;
import com.limelight.nvstream.jni.MoonBridge;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PyroWaveDecoderRendererTest {
    private PyroWaveDecoderRenderer renderer(PyroWaveDecoderRenderer.Backend backend) {
        Surface surface = mock(Surface.class);
        when(surface.isValid()).thenReturn(true);
        PyroWaveDecoderRenderer renderer = new PyroWaveDecoderRenderer(backend);
        renderer.setRenderTarget(surface);
        return renderer;
    }

    @Test public void rejectsUnsupportedFormatsAndDimensionsBeforeNativeAllocation() {
        PyroWaveDecoderRenderer.Backend backend = mock(PyroWaveDecoderRenderer.Backend.class);
        PyroWaveDecoderRenderer renderer = renderer(backend);
        assertEquals(-1, renderer.setup(MoonBridge.VIDEO_FORMAT_H264, 1920, 1080, 60));
        assertEquals(-1, renderer.setup(MoonBridge.VIDEO_FORMAT_PYROWAVE, 1919, 1080, 60));
        assertEquals(-1, renderer.setup(MoonBridge.VIDEO_FORMAT_PYROWAVE, 8192, 8192, 60));
        verifyNoInteractions(backend);
    }

    @Test public void cleanupIsIdempotentAndStopPreventsFurtherNativeSubmission() {
        PyroWaveDecoderRenderer.Backend backend = mock(PyroWaveDecoderRenderer.Backend.class);
        when(backend.create(any(), eq(1920), eq(1080))).thenReturn(42L);
        PyroWaveDecoderRenderer renderer = renderer(backend);
        assertEquals(0, renderer.setup(MoonBridge.VIDEO_FORMAT_PYROWAVE, 1920, 1080, 60));
        renderer.prepareForStop();
        assertEquals(MoonBridge.DR_OK, renderer.submitDecodeUnit(new byte[4], 4, 0, 1, 1, (char)0, 0, 0));
        renderer.cleanup();
        renderer.cleanup();
        verify(backend, times(1)).destroy(42L);
        verify(backend, never()).decode(anyLong(), any(), anyInt());
    }

    @Test public void failedSetupAndMalformedFramesNeverUseADeadHandle() {
        PyroWaveDecoderRenderer.Backend backend = mock(PyroWaveDecoderRenderer.Backend.class);
        PyroWaveDecoderRenderer renderer = renderer(backend);
        assertEquals(-1, renderer.setup(MoonBridge.VIDEO_FORMAT_PYROWAVE, 1280, 720, 60));
        assertEquals(MoonBridge.DR_NEED_IDR, renderer.submitDecodeUnit(new byte[4], 5, 0, 1, 1, (char)0, 0, 0));
        renderer.stop();
        renderer.cleanup();
        verify(backend, never()).destroy(anyLong());
        verify(backend, never()).decode(anyLong(), any(), anyInt());
    }

    @Test public void independentFrameAfterDecoderRejectionIsStillSubmitted() {
        PyroWaveDecoderRenderer.Backend backend = mock(PyroWaveDecoderRenderer.Backend.class);
        when(backend.create(any(), eq(1280), eq(720))).thenReturn(7L);
        byte[] damaged = {1}, next = {2};
        when(backend.decode(7L, damaged, 1)).thenReturn(MoonBridge.DR_NEED_IDR);
        when(backend.decode(7L, next, 1)).thenReturn(MoonBridge.DR_OK);
        PyroWaveDecoderRenderer renderer = renderer(backend);
        assertEquals(0, renderer.setup(MoonBridge.VIDEO_FORMAT_PYROWAVE, 1280, 720, 60));
        assertEquals(-1, renderer.submitDecodeUnit(damaged, 1, 0, 1, 1, (char)0, 0, 0));
        assertEquals(0, renderer.submitDecodeUnit(next, 1, 0, 2, 1, (char)0, 0, 0));
        renderer.cleanup();
    }
}

//! JNI bridge to DeepFilterNet3 (the deep_filter crate with its embedded model), backing
//! com.limelight.binding.audio.DeepFilterNet. Mono, 10 ms frames (480 samples) at 48 kHz, 16-bit PCM.

use df::tract::*;
use jni::objects::{JClass, JShortArray};
use jni::sys::{jfloat, jint, jlong};
use jni::JNIEnv;
use ndarray::prelude::*;

pub struct State {
    df: DfTract,
    pcm: Vec<i16>,
    noisy: Vec<f32>,
    enhanced: Vec<f32>,
}

/// 0 if the model could not be set up
#[no_mangle]
pub extern "C" fn Java_com_limelight_binding_audio_DeepFilterNet_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
    atten_lim_db: jfloat,
    post_filter_beta: jfloat,
    min_db_thresh: jfloat,
    max_db_erb_thresh: jfloat,
    max_db_df_thresh: jfloat,
) -> jlong {
    let params = RuntimeParams::default_with_ch(1)
        .with_atten_lim(atten_lim_db)
        .with_thresholds(min_db_thresh, max_db_erb_thresh, max_db_df_thresh)
        .with_post_filter(post_filter_beta.max(0.0))
        .with_mask_reduce(ReduceMask::MAX);
    match DfTract::new(DfParams::default(), &params) {
        Ok(df) => {
            let n = df.hop_size;
            Box::into_raw(Box::new(State {
                df,
                pcm: vec![0; n],
                noisy: vec![0.0; n],
                enhanced: vec![0.0; n],
            })) as jlong
        }
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "C" fn Java_com_limelight_binding_audio_DeepFilterNet_nativeFrameSize(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let state = unsafe { &*(handle as *const State) };
    state.df.hop_size as jint
}

/// One frame at offset, processed in place
#[no_mangle]
pub extern "C" fn Java_com_limelight_binding_audio_DeepFilterNet_nativeProcess(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    frame: JShortArray,
    offset: jint,
) {
    let state = unsafe { &mut *(handle as *mut State) };
    let n = state.df.hop_size;
    if env.get_short_array_region(&frame, offset, &mut state.pcm).is_err() {
        return;
    }
    for i in 0..n {
        state.noisy[i] = state.pcm[i] as f32 / 32768.0;
    }
    let noisy = ArrayView2::from_shape((1, n), &state.noisy).unwrap();
    let enhanced = ArrayViewMut2::from_shape((1, n), &mut state.enhanced).unwrap();
    if state.df.process(noisy, enhanced).is_err() {
        return;
    }
    for i in 0..n {
        state.pcm[i] = (state.enhanced[i] * 32768.0).clamp(-32768.0, 32767.0) as i16;
    }
    let _ = env.set_short_array_region(&frame, offset, &state.pcm);
}

#[no_mangle]
pub extern "C" fn Java_com_limelight_binding_audio_DeepFilterNet_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    drop(unsafe { Box::from_raw(handle as *mut State) });
}

package com.iblu01.portallauncher.voice;

import android.content.Context;
import com.rementia.openwakeword.lib.audio.AudioRecorder;
import kotlinx.coroutines.flow.Flow;

/**
 * Java bridge for the one openwakeword-android class still in use: its {@code AudioRecorder},
 * public in bytecode but Kotlin-internal. Inference itself lives in {@link OpenWakeWordPipeline}.
 */
public final class OpenWakeWordBridge {
    private OpenWakeWordBridge() {}

    /**
     * Bare microphone frames: 16 kHz mono, 80 ms float frames normalised to -1..1. Shared by the
     * wake engine and the calibration routine so their RMS measurements are comparable.
     */
    public static Flow<float[]> recordRaw(Context context) {
        return new AudioRecorder(context).startRecording();
    }
}

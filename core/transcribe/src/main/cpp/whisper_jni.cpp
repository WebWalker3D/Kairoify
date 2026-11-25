// Copyright (c) 2026 WebWalker3D. Licensed under CPAL-1.0; see LICENSE.
// The bridge between Kotlin and whisper.cpp: load a model, transcribe 16 kHz mono audio, free the model.
#include <jni.h>
#include <string>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_icu_kairo_kairoify_core_transcribe_WhisperNative_load(JNIEnv *env, jobject, jstring path) {
    const char *file = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(file, params);
    env->ReleaseStringUTFChars(path, file);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_icu_kairo_kairoify_core_transcribe_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}

/** Each segment comes back as UTF-8 bytes of "startMillis\tendMillis\ttext" (whisper can split a character, so Kotlin decodes); null when whisper fails. */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_icu_kairo_kairoify_core_transcribe_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray samples, jstring language, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    const char *lang = env->GetStringUTFChars(language, nullptr);
    params.language = lang;
    params.n_threads = threads;
    params.translate = false;
    params.no_context = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;

    jsize count = env->GetArrayLength(samples);
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    int failed = whisper_full(ctx, params, pcm, count);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    if (failed != 0) return nullptr;

    int segments = whisper_full_n_segments(ctx);
    jobjectArray out = env->NewObjectArray(segments, env->FindClass("[B"), nullptr);
    for (int i = 0; i < segments; i++) {
        std::string line = std::to_string(whisper_full_get_segment_t0(ctx, i) * 10) + "\t" +
                           std::to_string(whisper_full_get_segment_t1(ctx, i) * 10) + "\t" +
                           whisper_full_get_segment_text(ctx, i);
        jbyteArray text = env->NewByteArray(static_cast<jsize>(line.size()));
        env->SetByteArrayRegion(text, 0, static_cast<jsize>(line.size()), reinterpret_cast<const jbyte *>(line.data()));
        env->SetObjectArrayElement(out, i, text);
        env->DeleteLocalRef(text);
    }
    return out;
}

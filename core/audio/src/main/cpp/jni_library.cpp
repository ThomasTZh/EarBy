// Thin JNI surface for the offline renderer.
//
// One shared library ("ear_audio") owns this boundary. Every entry point validates its inputs,
// converts a typed native error into a checked Java exception and never performs work on a
// background thread: the Kotlin owner serializes calls on one worker.
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "error.h"
#include "renderer.h"
#include "wav_io.h"

namespace {

constexpr const char *kAudioException = "com/thomas/passiveeartraining/audio/NativeAudioException";
constexpr const char *kHandleException = "com/thomas/passiveeartraining/audio/RendererClosedException";

JavaVM *g_vm = nullptr;
jclass g_audio_exception = nullptr;
jclass g_handle_exception = nullptr;

const char *code_name(ear_error code) {
    return ear_error_message(code);
}

// FindClass sets a pending ClassNotFoundException when the lookup fails. Leaving that pending
// exception set makes ART abort as soon as the JNI call returns (the release R8 build did this
// before the keep rules). Clear it and let the caller fall back instead.
jclass find_exception_class(JNIEnv *env, const char *name) {
    jclass found = env->FindClass(name);
    if (found == nullptr) {
        env->ExceptionClear();
    }
    return found;
}

void throw_standard(JNIEnv *env, const std::string &message) {
    jclass fallback = find_exception_class(env, "java/lang/RuntimeException");
    if (fallback == nullptr) {
        return;  // Nothing safe to throw; playback can still continue.
    }
    env->ThrowNew(fallback, message.c_str());
    env->DeleteLocalRef(fallback);
}

void throw_typed(JNIEnv *env, ear_error code) {
    if (code == EAR_OK) {
        return;
    }
    jclass type = (code == EAR_ERR_CLOSED || code == EAR_ERR_HANDLE) ? g_handle_exception : g_audio_exception;
    if (type == nullptr) {
        type = find_exception_class(env, kAudioException);
    }
    const std::string message = std::string("ear_audio: ") + code_name(code);
    if (type == nullptr) {
        // A renamed/missing class must not leave a pending exception or kill playback. The
        // standard exception still reports the native error to Kotlin.
        throw_standard(env, message);
        return;
    }
    env->ThrowNew(type, message.c_str());
}

ear_renderer *as_renderer(jlong handle) {
    return reinterpret_cast<ear_renderer *>(static_cast<intptr_t>(handle));
}

jlong as_handle(ear_renderer *renderer) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(renderer));
}

bool require_renderer(JNIEnv *env, jlong handle, ear_renderer **out) {
    ear_renderer *renderer = as_renderer(handle);
    if (out == nullptr) {
        throw_typed(env, EAR_ERR_HANDLE);
        return false;
    }
    if (renderer == nullptr) {
        *out = nullptr;
        throw_typed(env, EAR_ERR_HANDLE);
        return false;
    }
    *out = renderer;
    return true;
}

std::string to_string(JNIEnv *env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    if (jclass found = find_exception_class(env, kAudioException); found != nullptr) {
        g_audio_exception = static_cast<jclass>(env->NewGlobalRef(found));
        env->DeleteLocalRef(found);
    }
    if (jclass found = find_exception_class(env, kHandleException); found != nullptr) {
        g_handle_exception = static_cast<jclass>(env->NewGlobalRef(found));
        env->DeleteLocalRef(found);
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_audio_exception != nullptr) {
            env->DeleteGlobalRef(g_audio_exception);
            g_audio_exception = nullptr;
        }
        if (g_handle_exception != nullptr) {
            env->DeleteGlobalRef(g_handle_exception);
            g_handle_exception = nullptr;
        }
    }
    g_vm = nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeCreate(JNIEnv *env, jclass, jint sample_rate,
                                                                    jint channels) {
    ear_renderer *renderer = nullptr;
    const ear_error error = ear_renderer_create(sample_rate, channels, &renderer);
    if (error != EAR_OK) {
        throw_typed(env, error);
        return 0;
    }
    return as_handle(renderer);
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeLoadSoundFont(
    JNIEnv *env, jclass, jlong handle, jstring path, jint bank, jint program) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    const std::string native_path = to_string(env, path);
    if (native_path.empty()) {
        throw_typed(env, EAR_ERR_INVALID_ARGUMENT);
        return;
    }
    throw_typed(env, ear_renderer_load_soundfont(renderer, native_path.c_str(), bank, program));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeSetGain(
    JNIEnv *env, jclass, jlong handle, jfloat gain) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    throw_typed(env, ear_renderer_set_gain(renderer, gain));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeSetPolyphony(
    JNIEnv *env, jclass, jlong handle, jint polyphony) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    throw_typed(env, ear_renderer_set_polyphony(renderer, polyphony));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeSelectProgram(
    JNIEnv *env, jclass, jlong handle, jint bank, jint program) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    throw_typed(env, ear_renderer_select_program(renderer, bank, program));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeNoteOn(
    JNIEnv *env, jclass, jlong handle, jint midi_note, jint velocity) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    throw_typed(env, ear_renderer_note_on(renderer, midi_note, velocity));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeNoteOff(
    JNIEnv *env, jclass, jlong handle, jint midi_note) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    throw_typed(env, ear_renderer_note_off(renderer, midi_note));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeAllNotesOff(
    JNIEnv *env, jclass, jlong handle) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    throw_typed(env, ear_renderer_all_notes_off(renderer));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeSystemReset(
    JNIEnv *env, jclass, jlong handle) {
    ear_renderer *renderer = nullptr;
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    throw_typed(env, ear_renderer_system_reset(renderer));
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeRender(
    JNIEnv *env, jclass, jlong handle, jfloatArray output, jint frames) {
    ear_renderer *renderer = nullptr;
    if (output == nullptr || frames <= 0 || frames > 1024) {
        throw_typed(env, EAR_ERR_INVALID_ARGUMENT);
        return;
    }
    if (!require_renderer(env, handle, &renderer)) {
        return;
    }
    int channels = 0;
    if (ear_renderer_format(renderer, nullptr, &channels) != EAR_OK || channels <= 0) {
        throw_typed(env, EAR_ERR_HANDLE);
        return;
    }
    const jsize available = env->GetArrayLength(output);
    const jsize needed = frames * channels;
    if (available < needed) {
        throw_typed(env, EAR_ERR_BUFFER);
        return;
    }
    // A bounded stack block keeps one JNI call from touching more than the negotiated block size.
    constexpr int kBlockFrames = 1024;
    if (frames > kBlockFrames) {
        throw_typed(env, EAR_ERR_INVALID_ARGUMENT);
        return;
    }
    std::vector<float> block(static_cast<size_t>(frames) * static_cast<size_t>(channels));
    const ear_error error = ear_renderer_render(renderer, block.data(), frames);
    if (error != EAR_OK) {
        throw_typed(env, error);
        return;
    }
    env->SetFloatArrayRegion(output, 0, needed, block.data());
}

extern "C" JNIEXPORT jlong JNICALL Java_com_thomas_passiveeartraining_audio_NativeRenderer_nativeClose(JNIEnv *env,
                                                                                                     jclass,
                                                                                                     jlong handle) {
    ear_renderer *renderer = as_renderer(handle);
    if (renderer != nullptr) {
        // Idempotent: the Kotlin owner clears its handle, so a second close has nothing to do.
        ear_renderer_destroy(renderer);
    }
    (void)env;
    return 0;
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_thomas_passiveeartraining_audio_NativeAudioFiles_nativeWavInfo(
    JNIEnv *env, jclass, jstring path) {
    const std::string native_path = to_string(env, path);
    if (native_path.empty()) {
        throw_typed(env, EAR_ERR_INVALID_ARGUMENT);
        return nullptr;
    }
    ear_wav_info info{};
    const ear_error error = ear_wav_read_info(native_path.c_str(), &info);
    if (error != EAR_OK) {
        throw_typed(env, error);
        return nullptr;
    }
    jlong values[3] = {static_cast<jlong>(info.sample_rate), static_cast<jlong>(info.channels),
                       static_cast<jlong>(info.frame_count)};
    jlongArray result = env->NewLongArray(3);
    if (result == nullptr) {
        return nullptr;
    }
    env->SetLongArrayRegion(result, 0, 3, values);
    return result;
}

extern "C" JNIEXPORT jint JNICALL Java_com_thomas_passiveeartraining_audio_NativeAudioFiles_nativeDecodeMono(
    JNIEnv *env, jclass, jstring path, jint target_sample_rate, jfloatArray output) {
    const std::string native_path = to_string(env, path);
    if (output == nullptr || native_path.empty()) {
        throw_typed(env, EAR_ERR_INVALID_ARGUMENT);
        return 0;
    }
    const jsize capacity = env->GetArrayLength(output);
    if (capacity <= 0 || capacity > 384000 ||
        (target_sample_rate != 44100 && target_sample_rate != 48000)) {
        throw_typed(env, EAR_ERR_INVALID_ARGUMENT);
        return 0;
    }
    std::vector<float> block(static_cast<size_t>(capacity));
    uint64_t produced = 0;
    const ear_error error =
        ear_wav_decode_mono(native_path.c_str(), target_sample_rate, block.data(), static_cast<uint64_t>(capacity),
                            &produced);
    if (error != EAR_OK) {
        throw_typed(env, error);
        return 0;
    }
    env->SetFloatArrayRegion(output, 0, static_cast<jsize>(produced), block.data());
    return static_cast<jint>(produced);
}

extern "C" JNIEXPORT void JNICALL Java_com_thomas_passiveeartraining_audio_NativeAudioFiles_nativeWriteWav(
    JNIEnv *env, jclass, jstring path, jfloatArray samples, jint channels, jint sample_rate, jint frames) {
    const std::string native_path = to_string(env, path);
    if (samples == nullptr || native_path.empty() || (channels != 1 && channels != 2) ||
        (sample_rate != 44100 && sample_rate != 48000) || frames <= 0 ||
        static_cast<int64_t>(frames) > static_cast<int64_t>(sample_rate) * 60) {
        throw_typed(env, EAR_ERR_INVALID_ARGUMENT);
        return;
    }
    const jsize available = env->GetArrayLength(samples);
    const jsize needed = frames * channels;
    if (available < needed) {
        throw_typed(env, EAR_ERR_BUFFER);
        return;
    }
    std::vector<float> block(static_cast<size_t>(needed));
    env->GetFloatArrayRegion(samples, 0, needed, block.data());
    throw_typed(env, ear_wav_write_float(native_path.c_str(), block.data(), static_cast<uint32_t>(channels),
                                         static_cast<uint32_t>(sample_rate), static_cast<uint64_t>(frames)));
}

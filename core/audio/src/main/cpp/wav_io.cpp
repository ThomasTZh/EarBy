#include "wav_io.h"

#define MA_NO_DEVICE_IO
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_GENERATION
#define MA_NO_THREADING
#define MA_IMPLEMENTATION
#include "miniaudio.h"

#include <cstdio>
#include <cstring>

namespace {

constexpr uint64_t kMaxDecodedFrames = 48000ULL * 8ULL;  // one voice clip is at most 8 s

}  // namespace

extern "C" {

ear_error ear_wav_read_info(const char *path, ear_wav_info *info) {
    if (path == nullptr || info == nullptr) {
        return EAR_ERR_INVALID_ARGUMENT;
    }
    ma_decoder decoder;
    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, 0);
    if (ma_decoder_init_file(path, &config, &decoder) != MA_SUCCESS) {
        return EAR_ERR_SOUNDFONT_OPEN;
    }
    info->sample_rate = decoder.outputSampleRate;
    info->channels = decoder.outputChannels;
    info->frame_count = 0;
    ma_uint64 total = 0;
    const ma_result length_result = ma_decoder_get_length_in_pcm_frames(&decoder, &total);
    ma_decoder_uninit(&decoder);
    if (length_result != MA_SUCCESS) {
        return EAR_ERR_DECODE;
    }
    info->frame_count = total;
    return EAR_OK;
}

ear_error ear_wav_decode_mono(const char *path, int target_sample_rate, float *output, uint64_t max_frames,
                              uint64_t *out_frames) {
    if (path == nullptr || output == nullptr || out_frames == nullptr) {
        return EAR_ERR_INVALID_ARGUMENT;
    }
    *out_frames = 0;
    if (target_sample_rate <= 0 || max_frames == 0 || max_frames > kMaxDecodedFrames) {
        return EAR_ERR_INVALID_ARGUMENT;
    }

    // Decode in bounded blocks: mono float32 at the requested rate, converted by miniaudio.
    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 1, static_cast<ma_uint32>(target_sample_rate));
    ma_decoder decoder;
    const ma_result init_result = ma_decoder_init_file(path, &config, &decoder);
    if (init_result != MA_SUCCESS) {
        std::fprintf(stderr, "ear_audio: decoder init failed with %d for '%s'\n",
                     static_cast<int>(init_result), path);
        return EAR_ERR_SOUNDFONT_OPEN;
    }

    constexpr ma_uint64 kBlockFrames = 4096;
    ma_uint64 produced = 0;
    ear_error error = EAR_OK;
    while (produced < max_frames) {
        const ma_uint64 want = (max_frames - produced) < kBlockFrames ? (max_frames - produced) : kBlockFrames;
        ma_uint64 read = 0;
        const ma_result result = ma_decoder_read_pcm_frames(&decoder, output + produced, want, &read);
        if (result == MA_AT_END) {
            // The file ended; a short clip is not an error.
            break;
        }
        if (result != MA_SUCCESS) {
            std::fprintf(stderr, "ear_audio: decoder read failed with %d at frame %llu (want %llu)\n",
                         static_cast<int>(result), static_cast<unsigned long long>(produced),
                         static_cast<unsigned long long>(want));
            error = EAR_ERR_DECODE;
            break;
        }
        produced += read;
        if (read == 0) {
            break;
        }
    }
    ma_decoder_uninit(&decoder);
    if (error != EAR_OK) {
        return error;
    }
    if (produced == 0) {
        return EAR_ERR_DECODE;
    }
    *out_frames = produced;
    return EAR_OK;
}

ear_error ear_wav_write_float(const char *path, const float *samples, uint32_t channels, uint32_t sample_rate,
                              uint64_t frames) {
    if (path == nullptr || samples == nullptr || channels == 0 || sample_rate == 0) {
        return EAR_ERR_INVALID_ARGUMENT;
    }
    ma_encoder_config config = ma_encoder_config_init(ma_encoding_format_wav, ma_format_f32, channels, sample_rate);
    ma_encoder encoder;
    if (ma_encoder_init_file(path, &config, &encoder) != MA_SUCCESS) {
        return EAR_ERR_DECODE;
    }
    ma_uint64 written = 0;
    const ma_result result = ma_encoder_write_pcm_frames(&encoder, samples, frames, &written);
    ma_encoder_uninit(&encoder);
    if (result != MA_SUCCESS || written != frames) {
        return EAR_ERR_DECODE;
    }
    return EAR_OK;
}

}  // extern "C"

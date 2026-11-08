// Bounded WAV decoding through miniaudio.
//
// miniaudio supplies WAV parsing, channel/sample conversion and (for the host harness) WAV
// encoding. Device output is disabled entirely: AudioTrack owns playback. Only PCM WAV input is
// supported, and allocations are bounded by the caller's limit so a bad file cannot exhaust the
// process.
#ifndef EAR_AUDIO_WAV_IO_H
#define EAR_AUDIO_WAV_IO_H

#include <cstdint>

#include "error.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ear_wav_info {
    uint32_t sample_rate;
    uint32_t channels;
    uint64_t frame_count;
} ear_wav_info;

/** Reads format information without decoding samples. */
ear_error ear_wav_read_info(const char *path, ear_wav_info *info);

/**
 * Decodes a WAV file to mono float samples, converting rate and channels as needed.
 *
 * `max_frames` bounds the accepted input length; a longer file fails with EAR_ERR_DECODE rather
 * than allocating an unbounded buffer. `out_frames` receives the produced frame count.
 */
ear_error ear_wav_decode_mono(const char *path, int target_sample_rate, float *output, uint64_t max_frames,
                              uint64_t *out_frames);

/** Writes interleaved float samples to a 32-bit float WAV file (host harness only). */
ear_error ear_wav_write_float(const char *path, const float *samples, uint32_t channels, uint32_t sample_rate,
                              uint64_t frames);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // EAR_AUDIO_WAV_IO_H

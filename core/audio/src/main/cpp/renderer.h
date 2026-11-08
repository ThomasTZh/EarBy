// Bounded offline sample player.
//
// The renderer owns one validated EARP1 pack and produces float PCM into caller-provided
// buffers. It never touches a device: AudioTrack is the single owner of device output, and the
// Kotlin side keeps every call on one worker.
#ifndef EAR_AUDIO_RENDERER_H
#define EAR_AUDIO_RENDERER_H

#include <cstddef>
#include <cstdint>

#include "error.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ear_renderer ear_renderer;

/**
 * Creates a renderer at the requested output format.
 *
 * @param sample_rate 44100 or 48000
 * @param channels 1 or 2
 */
ear_error ear_renderer_create(int sample_rate, int channels, ear_renderer **out);

/** Loads an EARP1 pack and selects the requested bank/program. */
ear_error ear_renderer_load_soundfont(ear_renderer *renderer, const char *path, int bank, int program);

/** Configures the output gain on the historical 0..10 scale. */
ear_error ear_renderer_set_gain(ear_renderer *renderer, float gain);

/** Bounds the number of simultaneously sounding voices. */
ear_error ear_renderer_set_polyphony(ear_renderer *renderer, int polyphony);

/** Selects bank/program explicitly; reverb and chorus stay disabled. */
ear_error ear_renderer_select_program(ear_renderer *renderer, int bank, int program);

/** Starts one note with the given velocity. */
ear_error ear_renderer_note_on(ear_renderer *renderer, int midi_note, int velocity);

/** Releases one note. */
ear_error ear_renderer_note_off(ear_renderer *renderer, int midi_note);

/** Silences every sounding voice immediately. */
ear_error ear_renderer_all_notes_off(ear_renderer *renderer);

/** Soft MIDI-style reset: releases notes, resets controllers and drops all voices. */
ear_error ear_renderer_system_reset(ear_renderer *renderer);

/**
 * Renders exactly `frames` frames of interleaved float PCM into `output`.
 *
 * `output` must hold frames * channels floats. Output is overwritten, never mixed with stale
 * content, and silence is written when no voice is sounding so specified gaps stay silent.
 */
ear_error ear_renderer_render(ear_renderer *renderer, float *output, int frames);

/** Idempotent release; `renderer` may be null. */
void ear_renderer_destroy(ear_renderer *renderer);

/** Output format of a live renderer; returns EAR_ERR_HANDLE for null. */
ear_error ear_renderer_format(const ear_renderer *renderer, int *sample_rate, int *channels);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // EAR_AUDIO_RENDERER_H

// Typed error codes shared by the native renderer and its JNI/Kotlin boundary.
#ifndef EAR_AUDIO_ERROR_H
#define EAR_AUDIO_ERROR_H

#ifdef __cplusplus
extern "C" {
#endif

// Every failure the renderer can report. Kotlin maps these one-to-one; a generic "failed" is
// deliberately absent so the app can show an actionable diagnostic.
typedef enum ear_error {
    EAR_OK = 0,
    EAR_ERR_INVALID_ARGUMENT = 1,
    EAR_ERR_HANDLE = 2,          // null, unknown or already closed handle
    EAR_ERR_CLOSED = 3,          // use after close
    EAR_ERR_SOUNDFONT_OPEN = 4,  // the file could not be opened
    EAR_ERR_SOUNDFONT_FORMAT = 5,// not a usable SoundFont
    EAR_ERR_SOUNDFONT_LOAD = 6,  // the synth rejected the SoundFont
    EAR_ERR_SYNTH_CREATE = 7,
    EAR_ERR_SYNTH_SETTINGS = 8,
    EAR_ERR_SETTINGS = 9,        // sample-rate/format request not supported
    EAR_ERR_GAIN = 10,
    EAR_ERR_POLYPHONY = 11,
    EAR_ERR_PROGRAM_SELECT = 12,
    EAR_ERR_NOTE = 13,           // out-of-range MIDI note
    EAR_ERR_VELOCITY = 14,
    EAR_ERR_RENDER = 15,
    EAR_ERR_DECODE = 16,         // WAV decode/conversion failure
    EAR_ERR_ALLOCATION = 17,
    EAR_ERR_BUFFER = 18,         // caller buffer too small or null
    EAR_ERR_NOT_IMPLEMENTED = 19
} ear_error;

/** Short stable description of an error code for logs and diagnostics. */
const char *ear_error_message(ear_error code);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // EAR_AUDIO_ERROR_H

#include "error.h"

const char *ear_error_message(ear_error code) {
    switch (code) {
        case EAR_OK:
            return "ok";
        case EAR_ERR_INVALID_ARGUMENT:
            return "invalid argument";
        case EAR_ERR_HANDLE:
            return "unknown or null renderer handle";
        case EAR_ERR_CLOSED:
            return "renderer is closed";
        case EAR_ERR_SOUNDFONT_OPEN:
            return "soundfont could not be opened";
        case EAR_ERR_SOUNDFONT_FORMAT:
            return "file is not a usable soundfont";
        case EAR_ERR_SOUNDFONT_LOAD:
            return "soundfont was rejected by the synthesizer";
        case EAR_ERR_SYNTH_CREATE:
            return "synthesizer could not be created";
        case EAR_ERR_SYNTH_SETTINGS:
            return "synthesizer settings could not be applied";
        case EAR_ERR_SETTINGS:
            return "unsupported sample rate or channel count";
        case EAR_ERR_GAIN:
            return "gain could not be applied";
        case EAR_ERR_POLYPHONY:
            return "polyphony could not be applied";
        case EAR_ERR_PROGRAM_SELECT:
            return "bank or program could not be selected";
        case EAR_ERR_NOTE:
            return "midi note outside 0..127";
        case EAR_ERR_VELOCITY:
            return "midi velocity outside 1..127";
        case EAR_ERR_RENDER:
            return "render failed";
        case EAR_ERR_DECODE:
            return "wav decode or conversion failed";
        case EAR_ERR_ALLOCATION:
            return "allocation failed";
        case EAR_ERR_BUFFER:
            return "output buffer is null or too small";
        case EAR_ERR_NOT_IMPLEMENTED:
            return "not implemented";
    }
    return "unknown error";
}

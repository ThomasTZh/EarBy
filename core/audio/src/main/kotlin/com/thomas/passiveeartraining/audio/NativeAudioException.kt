package com.thomas.passiveeartraining.audio

/**
 * Raised for any native renderer failure.
 *
 * The message always carries a typed reason derived from the native error code, so the app can
 * show an actionable diagnostic instead of a generic failure.
 */
open class NativeAudioException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Raised when a call is made on a renderer that has already been closed or is otherwise invalid. */
class RendererClosedException(message: String) : NativeAudioException(message)

/** Raised when a bundled SoundFont or WAV asset is missing, malformed or rejected by the engine. */
class AudioAssetException(message: String, cause: Throwable? = null) :
    NativeAudioException(message, cause)

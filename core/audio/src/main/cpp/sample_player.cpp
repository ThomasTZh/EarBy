// LGPL-free sample player behind the existing ear_renderer_* contract.
//
// This is the only renderer in the build. It reads the EARP1 pack documented in sample_player.h,
// keeps one immutable sample table and mixes up to 32 one-shot voices into float PCM. The
// velocity, release and level constants were measured against the pre-removal reference
// renderer capture recorded in .local/verification/sample-player/analysis/report.md:
//
//   * velocity: A(v) = A(127) * (v / 127)^2 (the measured exponent is 2.0009, within 0.03 dB of
//     the square law over velocities 32/64/96/127),
//   * note-off: one exponential release from the level at note-off, tau = 1.491 ms (the measured
//     slope is -5825 dB/s median, -60 dB after 10.209 ms median), forced to exact silence after
//     704 frames at 48 kHz and 640 frames at 44.1 kHz (14.5-14.7 ms),
//   * level: a uniform engine scale of 0.5238 reproduces the measured reference-renderer output
//     level for this font (sustain RMS ratios 0.5217-0.5269 over velocities 32-127, within
//     0.03 dB).
//     It is a constant, not per-note normalisation; the samples keep their recorded balance.
//
// The pack is 88 mono 48 kHz one-shots, so 48 kHz output plays them at unity rate and 44.1 kHz
// output uses linear interpolation. No other resampling, filtering or dynamics are applied.
#include "sample_player.h"

#include "renderer.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <memory>
#include <new>
#include <utility>

#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__)
#error "The EARP1 loader validates little-endian PCM bytes and assumes a little-endian host"
#endif

namespace {

constexpr int kMaxVoices = 256;
constexpr int kDefaultPolyphony = 32;
constexpr int kMaxPolyphony = 256;
constexpr float kMaxGain = 10.0f;
constexpr int kMaxFramesPerCall = 65536;

// Measured reference-renderer output scale for this font at gain 1.0 (see the file header).
constexpr float kEngineLevel = 0.5238f;
// Measured release: tau = 8.686 / 5825 dB/s = 1.491 ms; exact silence after 704/640 frames.
constexpr double kReleaseTauSeconds = 0.001491;
constexpr int kReleaseFrames48000 = 704;
constexpr int kReleaseFrames44100 = 640;

bool is_supported_rate(int sample_rate) {
    return sample_rate == 44100 || sample_rate == 48000;
}

uint16_t read_u16(const uint8_t *data) {
    return static_cast<uint16_t>(data[0] | (data[1] << 8));
}

uint32_t read_u32(const uint8_t *data) {
    return static_cast<uint32_t>(data[0]) | (static_cast<uint32_t>(data[1]) << 8) |
           (static_cast<uint32_t>(data[2]) << 16) | (static_cast<uint32_t>(data[3]) << 24);
}

// ---- SHA-256 ---------------------------------------------------------------------------------

constexpr uint32_t kSha256Round[64] = {
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u,
    0xab1c5ed5u, 0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu,
    0x9bdc06a7u, 0xc19bf174u, 0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu,
    0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau, 0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u,
    0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u, 0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu,
    0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u, 0xa2bfe8a1u, 0xa81a664bu,
    0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u, 0x19a4c116u,
    0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u,
    0xc67178f2u};

inline uint32_t rotr(uint32_t value, int bits) {
    return (value >> bits) | (value << (32 - bits));
}

uint32_t read_u32_be(const uint8_t *data) {
    return (static_cast<uint32_t>(data[0]) << 24) | (static_cast<uint32_t>(data[1]) << 16) |
           (static_cast<uint32_t>(data[2]) << 8) | static_cast<uint32_t>(data[3]);
}

void sha256_block(uint32_t state[8], const uint8_t block[64]) {
    uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
        w[i] = read_u32_be(block + 4 * i);
    }
    for (int i = 16; i < 64; ++i) {
        const uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
        const uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t e = state[4], f = state[5], g = state[6], h = state[7];
    for (int i = 0; i < 64; ++i) {
        const uint32_t s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        const uint32_t ch = (e & f) ^ (~e & g);
        const uint32_t temp1 = h + s1 + ch + kSha256Round[i] + w[i];
        const uint32_t s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        const uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        const uint32_t temp2 = s0 + maj;
        h = g;
        g = f;
        f = e;
        e = d + temp1;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2;
    }
    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
    state[4] += e;
    state[5] += f;
    state[6] += g;
    state[7] += h;
}

}  // namespace

namespace ear_sample {

namespace {

struct FileCloser {
    void operator()(std::FILE *file) const {
        if (file != nullptr) {
            std::fclose(file);
        }
    }
};

}  // namespace

void sha256(const uint8_t *data, size_t size, uint8_t out[32]) {
    uint32_t state[8] = {0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
                         0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u};
    size_t offset = 0;
    while (offset + 64 <= size) {
        sha256_block(state, data + offset);
        offset += 64;
    }
    uint8_t tail[128] = {};
    const size_t remaining = size - offset;
    if (remaining > 0) {
        std::memcpy(tail, data + offset, remaining);
    }
    tail[remaining] = 0x80;
    const size_t blocks = remaining < 56 ? 1 : 2;
    const uint64_t bits = static_cast<uint64_t>(size) * 8;
    for (int i = 0; i < 8; ++i) {
        tail[blocks * 64 - 1 - i] = static_cast<uint8_t>(bits >> (8 * i));
    }
    for (size_t index = 0; index < blocks; ++index) {
        sha256_block(state, tail + index * 64);
    }
    for (int i = 0; i < 8; ++i) {
        out[4 * i] = static_cast<uint8_t>(state[i] >> 24);
        out[4 * i + 1] = static_cast<uint8_t>(state[i] >> 16);
        out[4 * i + 2] = static_cast<uint8_t>(state[i] >> 8);
        out[4 * i + 3] = static_cast<uint8_t>(state[i]);
    }
}

SamplePack::~SamplePack() = default;

void SamplePack::clear() {
    samples_.clear();
    pcm_.clear();
}

PackStatus SamplePack::load(const char *path) {
    clear();
    if (path == nullptr || path[0] == '\0') {
        return PackStatus::kOpenFailed;
    }
    std::unique_ptr<std::FILE, FileCloser> file(std::fopen(path, "rb"));
    if (!file) {
        return PackStatus::kOpenFailed;
    }

    uint8_t header[kHeaderBytes];
    if (std::fread(header, 1, sizeof(header), file.get()) != sizeof(header)) {
        return PackStatus::kBadFormat;
    }
    if (std::memcmp(header, "EARP1\0\0\0", 8) != 0) {
        return PackStatus::kBadFormat;
    }
    const uint32_t version = read_u32(header + 8);
    const uint32_t sample_rate = read_u32(header + 12);
    const uint32_t channels = read_u32(header + 16);
    const uint32_t entry_count = read_u32(header + 20);
    const uint32_t index_offset = read_u32(header + 24);
    const uint32_t data_offset = read_u32(header + 28);
    const uint32_t payload_bytes = read_u32(header + 32);
    const uint32_t flags = read_u32(header + 36);
    const size_t expected_data_offset = kHeaderBytes + kEntryBytes * kEntryCount;
    if (version != kFormatVersion || sample_rate != kPackSampleRate ||
        channels != kPackChannels || entry_count != kEntryCount ||
        index_offset != kHeaderBytes || data_offset != expected_data_offset || flags != 0) {
        return PackStatus::kBadFormat;
    }

    if (std::fseek(file.get(), 0, SEEK_END) != 0) {
        return PackStatus::kBadFormat;
    }
    const long file_size = std::ftell(file.get());
    if (file_size < 0) {
        return PackStatus::kBadFormat;
    }
    const uint64_t file_bytes = static_cast<uint64_t>(file_size);
    if (file_bytes < data_offset || file_bytes - data_offset != payload_bytes ||
        (payload_bytes % 2) != 0) {
        return PackStatus::kBadFormat;
    }

    if (std::fseek(file.get(), static_cast<long>(index_offset), SEEK_SET) != 0) {
        return PackStatus::kBadFormat;
    }
    std::vector<uint8_t> index(kEntryBytes * kEntryCount);
    if (std::fread(index.data(), 1, index.size(), file.get()) != index.size()) {
        return PackStatus::kBadFormat;
    }

    struct Entry {
        int midi;
        uint32_t frames;
        uint32_t offset;
        const uint8_t *pcm_hash;
    };
    std::vector<Entry> entries(kEntryCount);
    uint64_t cursor = data_offset;
    const uint8_t *source_hash = index.data() + 12;
    for (size_t i = 0; i < kEntryCount; ++i) {
        const uint8_t *record = index.data() + i * kEntryBytes;
        const int midi = read_u16(record);
        const uint32_t entry_flags = read_u16(record + 2);
        const uint32_t frames = read_u32(record + 4);
        const uint32_t offset = read_u32(record + 8);
        if (midi != kMidiLow + static_cast<int>(i) || entry_flags != 0 || frames == 0) {
            return PackStatus::kBadFormat;
        }
        if (offset != cursor) {
            return PackStatus::kBadFormat;
        }
        if (std::memcmp(record + 12, source_hash, 32) != 0) {
            return PackStatus::kBadFormat;
        }
        const uint64_t sample_bytes = static_cast<uint64_t>(frames) * 2;
        if (sample_bytes > payload_bytes || cursor - data_offset + sample_bytes > payload_bytes) {
            return PackStatus::kBadFormat;
        }
        entries[i] = {midi, frames, offset, record + 44};
        cursor += sample_bytes;
    }
    if (cursor != static_cast<uint64_t>(data_offset) + payload_bytes) {
        return PackStatus::kBadFormat;
    }

    // Build into locals and publish only after every check passes, so a rejected pack cannot
    // leave half-loaded audio reachable through find().
    std::vector<int16_t> pcm(payload_bytes / 2);
    if (std::fseek(file.get(), static_cast<long>(data_offset), SEEK_SET) != 0) {
        return PackStatus::kBadFormat;
    }
    if (std::fread(pcm.data(), sizeof(int16_t), pcm.size(), file.get()) != pcm.size()) {
        return PackStatus::kBadFormat;
    }

    uint8_t digest[32];
    const uint8_t *payload = reinterpret_cast<const uint8_t *>(pcm.data());
    sha256(payload, payload_bytes, digest);
    if (std::memcmp(digest, header + 40, 32) != 0) {
        return PackStatus::kBadHash;
    }
    uint64_t offset = 0;
    for (const Entry &entry : entries) {
        sha256(payload + offset, static_cast<size_t>(entry.frames) * 2, digest);
        if (std::memcmp(digest, entry.pcm_hash, 32) != 0) {
            return PackStatus::kBadHash;
        }
        offset += static_cast<uint64_t>(entry.frames) * 2;
    }

    std::vector<Sample> samples(kEntryCount);
    offset = 0;
    for (size_t i = 0; i < kEntryCount; ++i) {
        samples[i] = {entries[i].midi, entries[i].frames, pcm.data() + offset};
        offset += entries[i].frames;
    }
    // Moving a vector transfers its allocation; the sample pointers stay valid.
    pcm_ = std::move(pcm);
    samples_ = std::move(samples);
    return PackStatus::kOk;
}

const Sample *SamplePack::find(int midi) const {
    if (midi < kMidiLow || midi > kMidiHigh || samples_.size() != kEntryCount) {
        return nullptr;
    }
    return &samples_[static_cast<size_t>(midi - kMidiLow)];
}

}  // namespace ear_sample

namespace {

using ear_sample::SamplePack;

struct Voice {
    bool active = false;
    bool releasing = false;
    const ear_sample::Sample *sample = nullptr;
    double position = 0.0;
    float amplitude = 0.0f;
    float release_gain = 1.0f;
    double release_decay = 1.0;
    int release_frame = 0;
    int release_frames = 0;
    uint64_t serial = 0;
};

}  // namespace

// One renderer owns one validated pack and a fixed voice table; handles never escape this library.
struct ear_renderer {
    ear_renderer() : voices(new (std::nothrow) Voice[kMaxVoices]) {}
    ~ear_renderer() { delete[] voices; }
    ear_renderer(const ear_renderer &) = delete;
    ear_renderer &operator=(const ear_renderer &) = delete;

    Voice *voices = nullptr;
    SamplePack pack;
    int sample_rate = 48000;
    int channels = 2;
    float gain = 1.0f;
    int polyphony = kDefaultPolyphony;
    bool closed = false;
    bool pack_loaded = false;
    uint64_t serial = 0;
    double source_step = 1.0;
    double release_decay = 1.0;
    int release_frames = kReleaseFrames48000;
};

namespace {

void silence_all(ear_renderer *renderer) {
    for (int i = 0; i < kMaxVoices; ++i) {
        renderer->voices[i].active = false;
    }
}

int active_voice_count(const ear_renderer *renderer) {
    int count = 0;
    for (int i = 0; i < kMaxVoices; ++i) {
        if (renderer->voices[i].active) {
            ++count;
        }
    }
    return count;
}

// Drops the oldest voices until at most `renderer->polyphony` remain, matching the old engine's
// bounded voice pool when the caller lowers the limit.
void enforce_polyphony(ear_renderer *renderer) {
    while (active_voice_count(renderer) > renderer->polyphony) {
        Voice *oldest = nullptr;
        for (int i = 0; i < kMaxVoices; ++i) {
            Voice &voice = renderer->voices[i];
            if (voice.active && (oldest == nullptr || voice.serial < oldest->serial)) {
                oldest = &voice;
            }
        }
        if (oldest == nullptr) {
            return;
        }
        oldest->active = false;
    }
}

Voice *allocate_voice(ear_renderer *renderer) {
    for (int i = 0; i < kMaxVoices; ++i) {
        if (!renderer->voices[i].active) {
            return &renderer->voices[i];
        }
    }
    // All 256 slots are active (only possible when the caller raised polyphony that far); steal
    // the oldest voice rather than dropping the note.
    Voice *oldest = &renderer->voices[0];
    for (int i = 1; i < kMaxVoices; ++i) {
        if (renderer->voices[i].serial < oldest->serial) {
            oldest = &renderer->voices[i];
        }
    }
    return oldest;
}

}  // namespace

extern "C" {

ear_error ear_renderer_create(int sample_rate, int channels, ear_renderer **out) {
    if (out == nullptr) {
        return EAR_ERR_INVALID_ARGUMENT;
    }
    *out = nullptr;
    if (!is_supported_rate(sample_rate) || (channels != 1 && channels != 2)) {
        return EAR_ERR_SETTINGS;
    }
    ear_renderer *renderer = new (std::nothrow) ear_renderer();
    if (renderer == nullptr || renderer->voices == nullptr) {
        delete renderer;
        return EAR_ERR_ALLOCATION;
    }
    renderer->sample_rate = sample_rate;
    renderer->channels = channels;
    renderer->source_step = static_cast<double>(ear_sample::kPackSampleRate) / sample_rate;
    renderer->release_decay = std::exp(-1.0 / (kReleaseTauSeconds * sample_rate));
    renderer->release_frames =
        sample_rate == 48000 ? kReleaseFrames48000 : kReleaseFrames44100;
    *out = renderer;
    return EAR_OK;
}

// The renderer expects an EARP1 pack; the app stages the packaged pack and host tools pass the
// same kind of file. The SoundFont is now only a tracked generator input under synthNotes/.
ear_error ear_renderer_load_soundfont(ear_renderer *renderer, const char *path, int bank,
                                      int program) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    if (path == nullptr || path[0] == '\0') {
        return EAR_ERR_INVALID_ARGUMENT;
    }
    if (bank < 0 || bank > 128 || program < 0 || program > 127) {
        return EAR_ERR_PROGRAM_SELECT;
    }
    // Old voices point into the pack that is about to be replaced.
    silence_all(renderer);
    renderer->pack_loaded = false;
    switch (renderer->pack.load(path)) {
        case ear_sample::PackStatus::kOk:
            renderer->pack_loaded = true;
            return ear_renderer_select_program(renderer, bank, program);
        case ear_sample::PackStatus::kOpenFailed:
            return EAR_ERR_SOUNDFONT_OPEN;
        case ear_sample::PackStatus::kBadHash:
        case ear_sample::PackStatus::kBadFormat:
        default:
            // A rejected pack is "not a usable SoundFont" in the shared error vocabulary.
            return EAR_ERR_SOUNDFONT_FORMAT;
    }
}

ear_error ear_renderer_set_gain(ear_renderer *renderer, float gain) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    if (!(gain >= 0.0f) || gain > kMaxGain) {
        return EAR_ERR_GAIN;
    }
    renderer->gain = gain;
    return EAR_OK;
}

ear_error ear_renderer_set_polyphony(ear_renderer *renderer, int polyphony) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    if (polyphony < 1 || polyphony > kMaxPolyphony) {
        return EAR_ERR_POLYPHONY;
    }
    renderer->polyphony = polyphony;
    enforce_polyphony(renderer);
    return EAR_OK;
}

ear_error ear_renderer_select_program(ear_renderer *renderer, int bank, int program) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    if (bank < 0 || bank > 128 || program < 0 || program > 127) {
        return EAR_ERR_PROGRAM_SELECT;
    }
    // The pack has one instrument (bank 0 / program 0, like the SF2 it replaces). Other valid
    // selections keep the instrument, matching the old engine's single-preset fallback.
    return EAR_OK;
}

ear_error ear_renderer_note_on(ear_renderer *renderer, int midi_note, int velocity) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    if (midi_note < 0 || midi_note > 127) {
        return EAR_ERR_NOTE;
    }
    if (velocity < 1 || velocity > 127) {
        return EAR_ERR_VELOCITY;
    }
    if (!renderer->pack_loaded) {
        return EAR_OK;
    }
    const ear_sample::Sample *sample = renderer->pack.find(midi_note);
    if (sample == nullptr) {
        // Outside the instrument's 21-108 range: a valid key with no zone, like the old font.
        return EAR_OK;
    }
    // Keep the active voice count at the negotiated limit, stealing the oldest voice first so a
    // new note is never dropped while an old one still sounds.
    if (active_voice_count(renderer) >= renderer->polyphony) {
        Voice *oldest = nullptr;
        for (int i = 0; i < kMaxVoices; ++i) {
            Voice &candidate = renderer->voices[i];
            if (candidate.active && (oldest == nullptr || candidate.serial < oldest->serial)) {
                oldest = &candidate;
            }
        }
        if (oldest != nullptr) {
            oldest->active = false;
        }
    }
    Voice *voice = allocate_voice(renderer);
    if (voice == nullptr) {
        return EAR_ERR_ALLOCATION;
    }
    const float normalized = static_cast<float>(velocity) / 127.0f;
    voice->active = true;
    voice->releasing = false;
    voice->sample = sample;
    voice->position = 0.0;
    voice->amplitude = normalized * normalized * kEngineLevel;
    voice->release_gain = 1.0f;
    voice->release_decay = renderer->release_decay;
    voice->release_frame = 0;
    voice->release_frames = renderer->release_frames;
    voice->serial = ++renderer->serial;
    return EAR_OK;
}

ear_error ear_renderer_note_off(ear_renderer *renderer, int midi_note) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    if (midi_note < 0 || midi_note > 127) {
        return EAR_ERR_NOTE;
    }
    for (int i = 0; i < kMaxVoices; ++i) {
        Voice &voice = renderer->voices[i];
        if (!voice.active || voice.releasing) {
            continue;
        }
        if (voice.sample != nullptr && voice.sample->midi == midi_note) {
            // Start the measured exponential release from the current sample level.
            voice.releasing = true;
            voice.release_frame = 0;
            voice.release_gain = 1.0f;
            voice.release_decay = renderer->release_decay;
            voice.release_frames = renderer->release_frames;
        }
    }
    return EAR_OK;
}

ear_error ear_renderer_all_notes_off(ear_renderer *renderer) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    silence_all(renderer);
    return EAR_OK;
}

ear_error ear_renderer_system_reset(ear_renderer *renderer) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    silence_all(renderer);
    return EAR_OK;
}

ear_error ear_renderer_render(ear_renderer *renderer, float *output, int frames) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (renderer->closed) {
        return EAR_ERR_CLOSED;
    }
    if (output == nullptr) {
        return EAR_ERR_BUFFER;
    }
    if (frames <= 0 || frames > kMaxFramesPerCall) {
        return EAR_ERR_INVALID_ARGUMENT;
    }

    if (active_voice_count(renderer) == 0) {
        // Exact silence: the caller relies on unsounded gaps being true zero.
        std::memset(output, 0, sizeof(float) * static_cast<size_t>(frames) * renderer->channels);
        return EAR_OK;
    }

    constexpr float kPcmScale = 1.0f / 32768.0f;
    for (int frame = 0; frame < frames; ++frame) {
        float mix = 0.0f;
        for (int i = 0; i < kMaxVoices; ++i) {
            Voice &voice = renderer->voices[i];
            if (!voice.active) {
                continue;
            }
            const ear_sample::Sample &sample = *voice.sample;
            const size_t index = static_cast<size_t>(voice.position);
            if (index >= sample.frames) {
                voice.active = false;
                continue;
            }
            float value = static_cast<float>(sample.pcm[index]) * kPcmScale;
            if (renderer->source_step != 1.0 && index + 1 < sample.frames) {
                const float fraction = static_cast<float>(voice.position - static_cast<double>(index));
                const float next = static_cast<float>(sample.pcm[index + 1]) * kPcmScale;
                value += (next - value) * fraction;
            }
            mix += value * voice.amplitude * voice.release_gain;
            voice.position += renderer->source_step;
            if (voice.position >= static_cast<double>(sample.frames)) {
                voice.active = false;
                continue;
            }
            if (voice.releasing) {
                voice.release_gain *= static_cast<float>(voice.release_decay);
                ++voice.release_frame;
                if (voice.release_frame >= voice.release_frames) {
                    voice.active = false;
                }
            }
        }
        mix *= renderer->gain;
        float *const target = output + static_cast<size_t>(frame) * renderer->channels;
        for (int channel = 0; channel < renderer->channels; ++channel) {
            target[channel] = mix;
        }
    }
    return EAR_OK;
}

void ear_renderer_destroy(ear_renderer *renderer) {
    if (renderer == nullptr) {
        return;
    }
    renderer->closed = true;
    renderer->pack.clear();
    delete renderer;
}

ear_error ear_renderer_format(const ear_renderer *renderer, int *sample_rate, int *channels) {
    if (renderer == nullptr) {
        return EAR_ERR_HANDLE;
    }
    if (sample_rate != nullptr) {
        *sample_rate = renderer->sample_rate;
    }
    if (channels != nullptr) {
        *channels = renderer->channels;
    }
    return EAR_OK;
}

}  // extern "C"

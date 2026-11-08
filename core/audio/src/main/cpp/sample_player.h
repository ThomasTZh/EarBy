// EARP1 sample-pack reader and SHA-256 helper for the LGPL-free sample player.
//
// The pack is the only sample container the sample player reads. It is produced by
// synthNotes/tools/build_sample_pack.py from the tracked SoundFont and is byte-deterministic.
// Layout (all integers little-endian, mirrored by the generator):
//
//   header (72 bytes)
//     0   char[8] magic "EARP1\0\0\0"
//     8   u32 format version (1)
//     12  u32 sample rate (48000)
//     16  u32 channels (1)
//     20  u32 entry count (88)
//     24  u32 index offset (72)
//     28  u32 data offset (72 + 76 * entry count)
//     32  u32 payload bytes
//     36  u32 reserved flags (0)
//     40  u8[32] SHA-256 of the whole PCM payload
//   index entry (76 bytes, ascending MIDI note)
//     0   u16 MIDI note (21-108)
//     2   u16 reserved flags (0)
//     4   u32 frame count
//     8   u32 absolute file offset of the first PCM byte
//     12  u8[32] SHA-256 of the source SF2
//     44  u8[32] SHA-256 of this entry's PCM bytes
//   payload: PCM16 little-endian mono frames.
//
// Loading validates the header, the contiguous MIDI 21-108 coverage, the payload hash and every
// per-entry hash before a sample becomes reachable. The loader assumes a little-endian host,
// which covers both shipped Android ABIs and the x86_64 host harness.
#ifndef EAR_AUDIO_SAMPLE_PLAYER_H
#define EAR_AUDIO_SAMPLE_PLAYER_H

#include <cstddef>
#include <cstdint>
#include <vector>

namespace ear_sample {

constexpr uint32_t kFormatVersion = 1;
constexpr uint32_t kPackSampleRate = 48000;
constexpr uint32_t kPackChannels = 1;
constexpr int kMidiLow = 21;
constexpr int kMidiHigh = 108;
constexpr size_t kEntryCount = 88;
constexpr size_t kHeaderBytes = 72;
constexpr size_t kEntryBytes = 76;

/** One immutable one-shot sample: MIDI key, frame count and PCM16 data inside the pack. */
struct Sample {
    int midi = 0;
    size_t frames = 0;
    const int16_t *pcm = nullptr;
};

/** Result of reading and validating one pack. */
enum class PackStatus {
    kOk,
    kOpenFailed,
    kBadFormat,
    kBadHash,
};

/** Owns the pack bytes and exposes the validated sample table. */
class SamplePack {
public:
    SamplePack() = default;
    SamplePack(const SamplePack &) = delete;
    SamplePack &operator=(const SamplePack &) = delete;
    ~SamplePack();

    /** Reads and validates a pack; on any failure the previous contents are dropped. */
    PackStatus load(const char *path);

    /** Returns the sample for a MIDI key, or null when the key is outside 21-108. */
    const Sample *find(int midi) const;

    const std::vector<Sample> &samples() const { return samples_; }

    /** Resident PCM bytes held by this pack (the loaded sample data). */
    size_t resident_bytes() const { return pcm_.size() * sizeof(int16_t); }

    void clear();

private:
    std::vector<int16_t> pcm_;      // one allocation for the whole payload
    std::vector<Sample> samples_;   // 88 entries, pcm points into pcm_
};

/** Standard SHA-256 over `size` bytes; exposed for the pack tests and the loader. */
void sha256(const uint8_t *data, size_t size, uint8_t out[32]);

}  // namespace ear_sample

#endif  // EAR_AUDIO_SAMPLE_PLAYER_H

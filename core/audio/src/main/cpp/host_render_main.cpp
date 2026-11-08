// Host-only harness: renders fixed note fixtures with the production sample-player sources and
// writes float WAV files for numeric analysis. It is never part of the Android build.
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "renderer.h"
#include "wav_io.h"

namespace {

constexpr int kSampleRate = 48000;
constexpr int kChannels = 1;
constexpr int kBlockFrames = 1024;
// Kept in step with RenderRequest.DEFAULT_GAIN in the Kotlin contract.
constexpr float kDefaultMusicGain = 1.0f;
// The widest fixture is the fifteen-note both-direction scale traversal; intervals use at
// most two windows and the chord fixture four.
constexpr int kMaxNotes = 15;

/** One sounding window: a MIDI note, its onset and its scheduled end, in seconds. */
struct NoteWindow {
    int midi;
    double startSeconds;
    double endSeconds;
};

struct Fixture {
    const char *name;
    const NoteWindow *notes;
    int noteCount;
    // The requested length; a note that ends later extends the rendered file.
    double seconds;
};

// Covers the roadmap's WAV fixtures: single notes (C4, A4), an ascending interval, a harmonic
// third, a unison pair and the M5 four-note seventh chord. The unison case is expected to
// contain exactly one audible note.
const NoteWindow kC4[] = {{60, 0.0, 1.0}};
const NoteWindow kA4[] = {{69, 0.0, 1.0}};
const NoteWindow kG4[] = {{67, 0.0, 1.0}};
const NoteWindow kAscendingC4G4[] = {{60, 0.0, 0.7}, {67, 0.8, 1.5}};
const NoteWindow kDescendingG4C4[] = {{67, 0.0, 0.7}, {60, 0.8, 1.5}};
const NoteWindow kHarmonicC4E4[] = {{60, 0.0, 1.0}, {64, 0.0, 1.0}};
const NoteWindow kUnisonC4[] = {{60, 0.0, 1.0}, {60, 0.0, 1.0}};
const NoteWindow kChordCmaj7[] = {{60, 0.0, 1.0}, {64, 0.0, 1.0}, {67, 0.0, 1.0}, {71, 0.0, 1.0}};
// M6: C major both directions, 15 emitted notes with the turning tonic once and the lower
// tonic at both ends, at the app's 700 ms note and 100 ms gap timing.
const NoteWindow kScaleCMajorBoth[] = {
    {60, 0.0, 0.7},   {62, 0.8, 1.5},   {64, 1.6, 2.3},   {65, 2.4, 3.1},   {67, 3.2, 3.9},
    {69, 4.0, 4.7},   {71, 4.8, 5.5},   {72, 5.6, 6.3},   {71, 6.4, 7.1},   {69, 7.2, 7.9},
    {67, 8.0, 8.7},   {65, 8.8, 9.5},   {64, 9.6, 10.3},  {62, 10.4, 11.1}, {60, 11.2, 11.9},
};

const Fixture kFixtures[] = {
    {"c4", kC4, 1, 1.0},
    {"a4", kA4, 1, 1.0},
    {"g4", kG4, 1, 1.0},
    {"ascending_c4_g4", kAscendingC4G4, 2, 0.7},
    {"descending_g4_c4", kDescendingG4C4, 2, 0.7},
    {"harmonic_c4_e4", kHarmonicC4E4, 2, 1.0},
    {"unison_c4", kUnisonC4, 2, 1.0},
    {"chord_cmaj7", kChordCmaj7, 4, 1.0},
    {"scale_c_major_both", kScaleCMajorBoth, 15, 11.9},
};

}  // namespace

int main(int argc, char **argv) {
    if (argc < 3) {
        std::fprintf(stderr, "usage: %s <earby-string-pack.bin> <output-dir>\n", argv[0]);
        return 2;
    }
    const std::string pack = argv[1];
    const std::string outDir = argv[2];

    for (const Fixture &fixture : kFixtures) {
        ear_renderer *renderer = nullptr;
        ear_error error = ear_renderer_create(kSampleRate, kChannels, &renderer);
        if (error != EAR_OK) {
            std::fprintf(stderr, "%s: create failed: %s\n", fixture.name, ear_error_message(error));
            return 1;
        }
        error = ear_renderer_load_soundfont(renderer, pack.c_str(), 0, 0);
        if (error != EAR_OK) {
            std::fprintf(stderr, "%s: load failed: %s\n", fixture.name, ear_error_message(error));
            ear_renderer_destroy(renderer);
            return 1;
        }
        ear_renderer_set_polyphony(renderer, 32);
        // The harness renders at the app's default music gain so the measured level is meaningful.
        ear_renderer_set_gain(renderer, kDefaultMusicGain);
        error = ear_renderer_system_reset(renderer);
        if (error != EAR_OK) {
            std::fprintf(stderr, "%s: reset failed: %s\n", fixture.name, ear_error_message(error));
            ear_renderer_destroy(renderer);
            return 1;
        }

        // The requested length, extended so a note that starts later still fits.
        double totalSeconds = fixture.seconds;
        for (int index = 0; index < fixture.noteCount; ++index) {
            totalSeconds = std::max(totalSeconds, fixture.notes[index].endSeconds);
        }
        const int totalFrames = static_cast<int>(totalSeconds * kSampleRate);
        std::vector<int> startFrames(fixture.noteCount);
        std::vector<int> endFrames(fixture.noteCount);
        std::vector<bool> sounding(fixture.noteCount, false);
        for (int index = 0; index < fixture.noteCount; ++index) {
            startFrames[index] = static_cast<int>(fixture.notes[index].startSeconds * kSampleRate);
            endFrames[index] = static_cast<int>(fixture.notes[index].endSeconds * kSampleRate);
        }

        std::vector<float> block(kBlockFrames * kChannels);
        std::vector<float> rendered;
        rendered.reserve(static_cast<size_t>(totalFrames));
        for (int frame = 0; frame < totalFrames; frame += kBlockFrames) {
            const int frames = (totalFrames - frame) < kBlockFrames ? (totalFrames - frame) : kBlockFrames;

            // Note ends are processed before note starts, matching the Kotlin renderer's rule.
            for (int index = 0; index < fixture.noteCount; ++index) {
                if (sounding[index] && frame >= endFrames[index]) {
                    ear_renderer_note_off(renderer, fixture.notes[index].midi);
                    sounding[index] = false;
                }
            }
            for (int index = 0; index < fixture.noteCount; ++index) {
                if (!sounding[index] && frame >= startFrames[index] && frame < endFrames[index]) {
                    error = ear_renderer_note_on(renderer, fixture.notes[index].midi, 96);
                    if (error != EAR_OK) {
                        std::fprintf(stderr, "%s: note on failed: %s\n", fixture.name,
                                     ear_error_message(error));
                        ear_renderer_destroy(renderer);
                        return 1;
                    }
                    sounding[index] = true;
                }
            }

            error = ear_renderer_render(renderer, block.data(), frames);
            if (error != EAR_OK) {
                std::fprintf(stderr, "%s: render failed: %s\n", fixture.name, ear_error_message(error));
                ear_renderer_destroy(renderer);
                return 1;
            }
            rendered.insert(rendered.end(), block.begin(), block.begin() + frames * kChannels);
        }

        // Same 5 ms final envelope the Kotlin renderer applies, so a specified gap is silent in
        // these fixtures exactly as it is in the app. Each declared window fades out on its own
        // edge; overlapping windows therefore fade together, as the old two-note logic did.
        constexpr double kEnvelopeSeconds = 0.005;
        const int envelopeFrames = static_cast<int>(kEnvelopeSeconds * kSampleRate);
        for (int index = 0; index < fixture.noteCount; ++index) {
            const int windowStart = startFrames[index];
            const int windowEnd = endFrames[index];
            const int fadeStart = std::max(windowStart, windowEnd - envelopeFrames);
            for (int frame = fadeStart; frame < windowEnd && frame < totalFrames; ++frame) {
                const float gain = static_cast<float>(windowEnd - frame) / envelopeFrames;
                for (int channel = 0; channel < kChannels; ++channel) {
                    rendered[static_cast<size_t>(frame) * kChannels + channel] *= gain;
                }
            }
        }

        // Same release-tail clearing the Kotlin renderer applies: audio after a note window is
        // removed up to the next audible event so declared gaps are silent.
        {
            int lastEndFrame = 0;
            for (int index = 0; index < fixture.noteCount; ++index) {
                lastEndFrame = std::max(lastEndFrame, endFrames[index]);
            }
            for (int index = 0; index < fixture.noteCount; ++index) {
                int nextStart = -1;
                for (int other = 0; other < fixture.noteCount; ++other) {
                    if (startFrames[other] > endFrames[index] &&
                        (nextStart < 0 || startFrames[other] < nextStart)) {
                        nextStart = startFrames[other];
                    }
                }
                if (nextStart < 0) {
                    continue;
                }
                for (int frame = endFrames[index]; frame < nextStart && frame < totalFrames; ++frame) {
                    for (int channel = 0; channel < kChannels; ++channel) {
                        rendered[static_cast<size_t>(frame) * kChannels + channel] = 0.0f;
                    }
                }
            }
            for (int frame = lastEndFrame; frame < totalFrames; ++frame) {
                for (int channel = 0; channel < kChannels; ++channel) {
                    rendered[static_cast<size_t>(frame) * kChannels + channel] = 0.0f;
                }
            }
        }

        // The file is written only after the envelope and tail clearing, so a fixture always
        // contains exactly the audio the app would produce.
        const std::string path = outDir + "/" + fixture.name + ".wav";
        error = ear_wav_write_float(path.c_str(), rendered.data(), kChannels, kSampleRate, rendered.size());
        if (error != EAR_OK) {
            std::fprintf(stderr, "%s: write failed: %s\n", fixture.name, ear_error_message(error));
            ear_renderer_destroy(renderer);
            return 1;
        }

        float peak = 0.0f;
        double energy = 0.0;
        for (float value : rendered) {
            peak = value > peak ? value : (-value > peak ? -value : peak);
            energy += static_cast<double>(value) * value;
        }

        // Frame markers and request metadata are exported next to the WAV so the offline analysis
        // can assert scheduled boundaries and declare the gaps that must be silent.
        const std::string metaPath = outDir + "/" + fixture.name + ".json";
        std::FILE *meta = std::fopen(metaPath.c_str(), "w");
        if (meta != nullptr) {
            std::fprintf(meta,
                         "{\n  \"fixture\": \"%s\",\n  \"sampleRate\": %d,\n  \"channels\": %d,\n"
                         "  \"totalFrames\": %d,\n  \"velocity\": 96,\n  \"notes\": [\n",
                         fixture.name, kSampleRate, kChannels, totalFrames);
            for (int index = 0; index < fixture.noteCount; ++index) {
                std::fprintf(meta, "    {\"midi\": %d, \"startFrame\": %d, \"endFrame\": %d}%s\n",
                             fixture.notes[index].midi, startFrames[index], endFrames[index],
                             index + 1 < fixture.noteCount ? "," : "");
            }
            std::fprintf(meta, "  ]\n}\n");
            std::fclose(meta);
        }

        std::printf("{\"fixture\": \"%s\", \"frames\": %zu, \"peak\": %.5f, \"rms\": %.6f, "
                    "\"notes\": %d, \"path\": \"%s\"}\n",
                    fixture.name, rendered.size(), peak,
                    rendered.empty() ? 0.0 : std::sqrt(energy / static_cast<double>(rendered.size())),
                    fixture.noteCount, path.c_str());
        ear_renderer_destroy(renderer);
    }
    return 0;
}

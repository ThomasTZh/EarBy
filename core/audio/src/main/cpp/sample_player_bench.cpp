// Host benchmark for the EARP1 sample player: pack load cost, resident memory and render speed.
//
// Usage: ear_sample_player_bench <earby-string-pack.bin>
//
// Prints one JSON object so the numbers can be pasted into the A/B evidence. RSS is read from
// /proc/self/status on Linux; on other hosts the memory fields are omitted.
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "renderer.h"

namespace {

long read_rss_kb() {
    std::FILE *file = std::fopen("/proc/self/status", "r");
    if (file == nullptr) {
        return -1;
    }
    char line[256];
    long value = -1;
    while (std::fgets(line, sizeof(line), file) != nullptr) {
        if (std::strncmp(line, "VmRSS:", 6) == 0) {
            std::sscanf(line + 6, "%ld", &value);
            break;
        }
    }
    std::fclose(file);
    return value;
}

struct Result {
    int sample_rate;
    int channels;
    double wall_seconds;
    double audio_seconds;
    double realtime_factor;
};

Result render_benchmark(const std::string &pack, int sample_rate, int channels, int voices,
                        double seconds) {
    ear_renderer *renderer = nullptr;
    if (ear_renderer_create(sample_rate, channels, &renderer) != EAR_OK) {
        return {sample_rate, channels, 0.0, 0.0, 0.0};
    }
    if (ear_renderer_load_soundfont(renderer, pack.c_str(), 0, 0) != EAR_OK) {
        ear_renderer_destroy(renderer);
        return {sample_rate, channels, 0.0, 0.0, 0.0};
    }
    ear_renderer_set_gain(renderer, 1.0f);
    ear_renderer_set_polyphony(renderer, voices);
    // A dense 32-note cluster: the worst realistic case for the voice loop.
    const int low = 48;
    for (int i = 0; i < voices; ++i) {
        ear_renderer_note_on(renderer, low + (i % 32), 100);
    }
    const int block_frames = 1024;
    std::vector<float> block(static_cast<size_t>(block_frames) * channels);
    const long long total_frames = static_cast<long long>(seconds * sample_rate);
    // The one-shots are 2.5 s long; re-trigger the cluster every 2 s so all 32 voices really
    // sound for the whole measured interval instead of the benchmark decaying into silence.
    const long long retrigger_frames = static_cast<long long>(2.0 * sample_rate);
    long long next_retrigger = retrigger_frames;
    long long rendered = 0;
    const auto start = std::chrono::steady_clock::now();
    while (rendered < total_frames) {
        if (rendered >= next_retrigger) {
            for (int i = 0; i < voices; ++i) {
                ear_renderer_note_on(renderer, low + (i % 32), 100);
            }
            next_retrigger += retrigger_frames;
        }
        const int frames = static_cast<int>(std::min<long long>(block_frames, total_frames - rendered));
        if (ear_renderer_render(renderer, block.data(), frames) != EAR_OK) {
            break;
        }
        rendered += frames;
    }
    const auto end = std::chrono::steady_clock::now();
    ear_renderer_destroy(renderer);
    const double wall = std::chrono::duration<double>(end - start).count();
    const double audio = static_cast<double>(rendered) / sample_rate;
    return {sample_rate, channels, wall, audio, wall > 0.0 ? audio / wall : 0.0};
}

}  // namespace

int main(int argc, char **argv) {
    if (argc != 2) {
        std::fprintf(stderr, "usage: %s <earby-string-pack.bin>\n", argv[0]);
        return 2;
    }
    const std::string pack = argv[1];
    const long rss_before = read_rss_kb();
    const auto load_start = std::chrono::steady_clock::now();
    ear_renderer *loader = nullptr;
    if (ear_renderer_create(48000, 1, &loader) != EAR_OK) {
        std::fprintf(stderr, "renderer create failed\n");
        return 1;
    }
    const ear_error load_error = ear_renderer_load_soundfont(loader, pack.c_str(), 0, 0);
    const auto load_end = std::chrono::steady_clock::now();
    const long rss_after = read_rss_kb();
    if (load_error != EAR_OK) {
        std::fprintf(stderr, "pack load failed: %s\n", ear_error_message(load_error));
        ear_renderer_destroy(loader);
        return 1;
    }
    ear_renderer_destroy(loader);
    const double load_ms =
        std::chrono::duration<double, std::milli>(load_end - load_start).count();

    std::FILE *file = std::fopen(pack.c_str(), "rb");
    long long pack_bytes = 0;
    if (file != nullptr) {
        std::fseek(file, 0, SEEK_END);
        pack_bytes = std::ftell(file);
        std::fclose(file);
    }

    const Result mono48 = render_benchmark(pack, 48000, 1, 32, 120.0);
    const Result stereo44 = render_benchmark(pack, 44100, 2, 32, 120.0);
    const Result mono48_one = render_benchmark(pack, 48000, 1, 1, 120.0);

    std::printf("{\n");
    std::printf("  \"packBytes\": %lld,\n", pack_bytes);
    std::printf("  \"loadMilliseconds\": %.3f,\n", load_ms);
    if (rss_before > 0 && rss_after > 0) {
        std::printf("  \"rssBeforeKb\": %ld,\n", rss_before);
        std::printf("  \"rssAfterKb\": %ld,\n", rss_after);
        std::printf("  \"rssDeltaKb\": %ld,\n", rss_after - rss_before);
        std::printf("  \"rssDeltaMb\": %.2f,\n", (rss_after - rss_before) / 1024.0);
    }
    std::printf("  \"render\": [\n");
    const Result results[] = {mono48, stereo44, mono48_one};
    for (size_t i = 0; i < sizeof(results) / sizeof(results[0]); ++i) {
        const Result &result = results[i];
        std::printf("    {\"sampleRate\": %d, \"channels\": %d, \"audioSeconds\": %.1f, "
                    "\"wallMilliseconds\": %.2f, \"realtimeFactor\": %.2f}%s\n",
                    result.sample_rate, result.channels, result.audio_seconds,
                    result.wall_seconds * 1000.0, result.realtime_factor,
                    i + 1 < sizeof(results) / sizeof(results[0]) ? "," : "");
    }
    std::printf("  ]\n}\n");
    return 0;
}

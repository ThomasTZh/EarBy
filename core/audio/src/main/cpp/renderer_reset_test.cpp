// Regression for reset isolation using the production sample player and reviewed EARP1 pack.
#include "renderer.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    ear_renderer *renderer = nullptr;
    if (ear_renderer_create(48000, 1, &renderer) != EAR_OK) return 1;
    if (ear_renderer_load_soundfont(renderer, argv[1], 0, 0) != EAR_OK) return 1;
    ear_renderer_set_gain(renderer, 1.0f);
    ear_renderer_set_polyphony(renderer, 32);
    std::vector<float> block(1024);
    ear_renderer_note_on(renderer, 60, 100);
    for (int i = 0; i < 2; ++i) ear_renderer_render(renderer, block.data(), 1024);
    ear_renderer_system_reset(renderer);
    float second_peak = 0;
    for (int i = 0; i < 8; ++i) {
        ear_renderer_render(renderer, block.data(), 1024);
        float peak = 0;
        for (float sample : block) peak = std::max(peak, std::fabs(sample));
        std::fprintf(stderr, "reset block %d peak=%g\n", i, peak);
        if (i == 1) second_peak = peak;
    }
    ear_renderer_destroy(renderer);
    return second_peak < 0.01f ? 0 : 1;
}

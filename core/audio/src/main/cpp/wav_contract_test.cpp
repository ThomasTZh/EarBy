// Host-only decoder contract checks against production miniaudio integration.
#include "wav_io.h"
#include <cstdio>
#include <vector>
int main() {
    const char *path = "wav-contract-test.wav";
    for (int frames : {1, 127, 4095, 4096, 4097, 48001}) {
        std::vector<float> source(frames, 0.25f);
        if (ear_wav_write_float(path, source.data(), 1, 48000, frames) != EAR_OK) return 1;
        std::vector<float> decoded(frames + 4096, 0);
        uint64_t produced = 0;
        const auto result = ear_wav_decode_mono(path, 48000, decoded.data(), decoded.size(), &produced);
        if (result != EAR_OK || produced != static_cast<uint64_t>(frames) || decoded[frames - 1] != 0.25f) {
            std::fprintf(stderr, "decode %d frames: result=%d produced=%llu last=%g\n", frames,
                result, static_cast<unsigned long long>(produced), decoded[frames - 1]);
            std::remove(path);
            return 1;
        }
    }
    std::remove(path);
    return 0;
}

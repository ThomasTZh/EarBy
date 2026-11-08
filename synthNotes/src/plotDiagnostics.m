function plotDiagnostics(summary, outDir, splitMidi)
%PLOTDIAGNOSTICS Write the four verification figures for the note library.
%   plotDiagnostics(summary, outDir) uses outDir/audio/candidates and writes
%   PNG files to outDir/figures. plotDiagnostics(summary, outDir, splitMidi)
%   additionally marks the chosen bass-to-guitar crossover in the coverage
%   figure. Pass NaN or omit the argument before the crossover is chosen.

arguments
    summary table
    outDir {mustBeTextScalar}
    splitMidi (1,1) double = NaN
end

figuresDir = fullfile(outDir, "figures");
if ~isfolder(figuresDir)
    mkdir(figuresDir);
end
audioDir = fullfile(outDir, "audio", "candidates");
colors = [0.20 0.45 0.85; 0.90 0.45 0.10];
instrumentNames = ["bass" "guitar"];
notes = noteTable();

% 1. Long-term spectra of the same pitch played by both instruments.
fig = figure("Visible", "off");
tiledlayout(fig, 2, 1, "TileSpacing", "compact");
spectrumMidi = [45 21];
for k = 1:numel(spectrumMidi)
    midi = spectrumMidi(k);
    nexttile;
    hold on;
    for j = 1:2
        [x, fs] = audioread(fullfile(audioDir, instrumentNames(j), sprintf("%03d.wav", midi)));
        [pxx, f] = pwelch(x, hann(8192), 4096, 2^15, fs);
        plot(f, 10 * log10(pxx + eps), "Color", colors(j, :), "LineWidth", 0.8, ...
            "DisplayName", instrumentNames(j));
    end
    hold off;
    grid on;
    set(gca, "XScale", "log");
    xlim([20 20000]);
    ylim([-140 -40]);
    xlabel("Frequency (Hz)");
    ylabel("PSD (dB/Hz)");
    title(sprintf("%s (MIDI %03d, %.1f Hz) played by both models", ...
        notes.name(midi - 20), midi, 440 * 2^((midi - 69) / 12)));
    legend("Location", "southwest");
end
exportgraphics(fig, fullfile(figuresDir, "01_spectra_bass_vs_guitar.png"), "Resolution", 150);
close(fig);

% 2. Modelled partial amplitudes at a common fundamental.
fig = figure("Visible", "off");
tiledlayout(fig, 1, 1, "TileSpacing", "compact");
nexttile;
hold on;
firstPartialLevel = 0;
for j = 1:2
    preset = instrumentPreset(instrumentNames(j));
    partials = partialModel(preset, 110);
    if j == 1
        firstPartialLevel = 20 * log10(max(partials.amplitude(1), eps));
    end
    count = min(40, numel(partials.n));
    level = 20 * log10(max(partials.amplitude(1:count), eps));
    stem(partials.n(1:count), level, "Color", colors(j, :), "LineWidth", 1, ...
        "MarkerSize", 3, "DisplayName", string(preset.name));
end
referenceN = (1:40).';
plot(referenceN, firstPartialLevel - 20 * log10(referenceN), "k--", ...
    "LineWidth", 0.8, "DisplayName", "1/n reference");
hold off;
grid on;
xlim([0.5 40.5]);
xlabel("Partial number n");
ylabel("Partial level (dB, model max = 0 dB)");
title("Partial amplitude template at f0 = 110 Hz");
legend("Location", "southwest");
exportgraphics(fig, fullfile(figuresDir, "02_partial_templates.png"), "Resolution", 150);
close(fig);

% 3. Decay spectrograms: high partials die faster than the fundamental.
fig = figure("Visible", "off");
tiledlayout(fig, 2, 1, "TileSpacing", "compact");
spectrogramCases = struct( ...
    "instrument", {"guitar", "bass"}, ...
    "midi", {40, 28}, ...
    "label", {"Guitar E2 (MIDI 40)", "Bass E1 (MIDI 28)"});
for k = 1:numel(spectrogramCases)
    nexttile;
    [x, fs] = audioread(fullfile(audioDir, spectrogramCases(k).instrument, ...
        sprintf("%03d.wav", spectrogramCases(k).midi)));
    [~, f, t, ps] = spectrogram(x, hann(2048), 1024, 4096, fs);
    imagesc(t, f, 10 * log10(ps + eps));
    axis xy;
    ylim([40 10000]);
    xlim([0 2.5]);
    clim([-130 -40]);
    colormap(gca, "turbo");
    colorbar;
    xlabel("Time (s)");
    ylabel("Frequency (Hz)");
    title(spectrogramCases(k).label);
end
exportgraphics(fig, fullfile(figuresDir, "03_decay_spectrograms.png"), "Resolution", 150);
close(fig);

% 4. Coverage across the requested A0-C8 range.
fig = figure("Visible", "off");
tiledlayout(fig, 3, 1, "TileSpacing", "compact");
for j = 1:2
    rows = summary(summary.preset == instrumentNames(j), :);
    nexttile(1);
    hold on;
    semilogy(rows.midi, rows.freqHz, ".", "Color", colors(j, :), "MarkerSize", 8, ...
        "DisplayName", instrumentNames(j));
    nexttile(2);
    hold on;
    plot(rows.midi, rows.partials, ".", "Color", colors(j, :), "MarkerSize", 8, ...
        "DisplayName", instrumentNames(j));
    nexttile(3);
    hold on;
    plot(rows.midi, rows.centroidHz, ".", "Color", colors(j, :), "MarkerSize", 8, ...
        "DisplayName", instrumentNames(j));
end
nexttile(1);
set(gca, "YScale", "log");
xlim([21 108]);
ylim([20 5000]);
grid on;
ylabel("f0 (Hz)");
legend("Location", "northwest");
title("Requested range A0-C8: frequencies, partial counts and spectral centroid");
nexttile(2);
xlim([21 108]);
grid on;
ylabel("Partials");
nexttile(3);
xlim([21 108]);
grid on;
xlabel("MIDI note (021 = A0, 108 = C8)");
ylabel("Centroid (Hz)");
if isfinite(splitMidi)
    for panel = 1:3
        nexttile(panel);
        xline(splitMidi + 0.5, "k--", "Split", ...
            "LabelVerticalAlignment", "bottom", "HandleVisibility", "off");
    end
end
exportgraphics(fig, fullfile(figuresDir, "04_range_coverage.png"), "Resolution", 150);
close(fig);
end

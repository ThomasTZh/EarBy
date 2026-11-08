function run_synthesis()
%RUN_SYNTHESIS Render both candidate note libraries and the review material.
%   run_synthesis() renders MIDI 21-108 for the guitar and bass models into
%   out/audio/candidates/<instrument>/, writes out/summary.csv and
%   out/partials.csv, builds out/review/index.html and writes the four
%   diagnostic figures. It does not write the final SF2: that happens after
%   the human crossover review.

projectRoot = fileparts(mfilename("fullpath"));
addpath(fullfile(projectRoot, "src"));
outDir = fullfile(projectRoot, "out");
audioDir = fullfile(outDir, "audio");
if isfolder(audioDir)
    rmdir(audioDir, "s");
end
if ~isfolder(outDir)
    mkdir(outDir);
end
if ~isfolder(fullfile(outDir, "review"))
    mkdir(fullfile(outDir, "review"));
end

started = tic;
instrumentNames = ["guitar" "bass"];
summaries = cell(1, numel(instrumentNames));
partialTables = cell(1, numel(instrumentNames));
for j = 1:numel(instrumentNames)
    preset = instrumentPreset(instrumentNames(j));
    noteDir = fullfile(audioDir, "candidates", instrumentNames(j));
    [summaries{j}, partialTables{j}] = writeNoteLibrary(preset, noteDir);
    fprintf("%-6s done: %d notes in %.1f s\n", instrumentNames(j), ...
        height(summaries{j}), toc(started));
end

summary = vertcat(summaries{:});
writetable(summary, fullfile(outDir, "summary.csv"));
partials = vertcat(partialTables{:});
writetable(partials, fullfile(outDir, "partials.csv"));
writeReviewPage(summary, fullfile(outDir, "review"));
plotDiagnostics(summary, outDir, NaN);

fprintf("Rendered %d candidate notes in %.1f s.\n", height(summary), toc(started));
fprintf("Review: %s\n", fullfile(outDir, "review", "index.html"));
fprintf("Next: build_final(splitMidi), then tools/build_sf2.py.\n");
end

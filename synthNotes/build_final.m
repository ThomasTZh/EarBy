function build_final(splitMidi)
%BUILD_FINAL Assemble the reviewed 88-note set and the SF2 mapping manifest.
%   build_final(splitMidi) copies the reviewed candidate WAVs into
%   out/audio/notes/ (bass for MIDI <= splitMidi, guitar above), verifies
%   that each copied file is byte-identical to its candidate, and writes
%   out/mapping.json, out/final_notes.csv and the marked coverage figure.
%   Run tools/build_sf2.py afterwards to package the single SF2.

arguments
    splitMidi (1,1) double {mustBeInteger, mustBeGreaterThanOrEqual(splitMidi, 21), ...
        mustBeLessThanOrEqual(splitMidi, 107)}
end

projectRoot = fileparts(mfilename("fullpath"));
addpath(fullfile(projectRoot, "src"));
outDir = fullfile(projectRoot, "out");
summaryFile = fullfile(outDir, "summary.csv");
if ~isfile(summaryFile)
    error("synthNotes:missingLibrary", "Run run_synthesis before build_final.");
end
summary = readtable(summaryFile, "TextType", "string");
finalDir = fullfile(outDir, "audio", "notes");
if isfolder(finalDir)
    rmdir(finalDir, "s");
end
mkdir(finalDir);

notes = noteTable();
midiValues = (21:108).';
sources = strings(numel(midiValues), 1);
fileRecords = repmat(struct("midi", 0, "source", "", "file", "", "sha256", ""), ...
    numel(midiValues), 1);
for idx = 1:numel(midiValues)
    midi = midiValues(idx);
    if midi <= splitMidi
        source = "bass";
    else
        source = "guitar";
    end
    fileName = sprintf("%03d.wav", midi);
    candidatePath = fullfile(outDir, "audio", "candidates", source, fileName);
    finalPath = fullfile(finalDir, fileName);
    if ~isfile(candidatePath)
        error("synthNotes:missingCandidate", "Missing candidate file '%s'.", candidatePath);
    end
    copyfile(candidatePath, finalPath);
    candidateHash = fileSha256(candidatePath);
    finalHash = fileSha256(finalPath);
    if candidateHash ~= finalHash
        error("synthNotes:copyMismatch", ...
            "Copied note '%s' does not match its candidate.", finalPath);
    end
    sources(idx) = source;
    fileRecords(idx).midi = midi;
    fileRecords(idx).source = source;
    fileRecords(idx).file = fileName;
    fileRecords(idx).sha256 = finalHash;
end

mapping = struct();
mapping.presetName = "String Synth A0-C8";
mapping.splitMidi = splitMidi;
mapping.sampleRate = 48000;
mapping.frames = 120000;
mapping.durationSeconds = 2.5;
mapping.rmsTarget = 0.120;
mapping.sourceByMidi = struct("midi", midiValues, "source", sources);
mapping.files = fileRecords;
writelines(string(jsonencode(mapping, "PrettyPrint", true)), ...
    fullfile(outDir, "mapping.json"));

finalRows = cell(numel(midiValues), 1);
for idx = 1:numel(midiValues)
    midi = midiValues(idx);
    row = summary(summary.preset == sources(idx) & summary.midi == midi, :);
    finalRows{idx} = table(midi, notes.name(midi - 20), sources(idx), row.freqHz, ...
        row.partials, row.centroidHz, row.slopeDbPerOct, row.rms, row.peak, row.file, ...
        'VariableNames', {'midi', 'note', 'source', 'freqHz', 'partials', ...
        'centroidHz', 'slopeDbPerOct', 'rms', 'peak', 'file'});
end
finalTable = vertcat(finalRows{:});
writetable(finalTable, fullfile(outDir, "final_notes.csv"));
writeReviewPage(summary, fullfile(outDir, "review"), splitMidi);
plotDiagnostics(summary, outDir, splitMidi);

fprintf("Final set: %d notes (%d bass, %d guitar), split at MIDI %d.\n", ...
    numel(midiValues), nnz(sources == "bass"), nnz(sources == "guitar"), splitMidi);
fprintf("Wrote %s and %s\n", finalDir, fullfile(outDir, "mapping.json"));
end

function [summary, partialsTable] = writeNoteLibrary(preset, outDir, options)
%WRITENOTELIBRARY Render and write one 16-bit/48 kHz mono WAV per MIDI note.
%   [summary, partialsTable] = writeNoteLibrary(preset, outDir) renders MIDI
%   notes 21 through 108, writes "<midi>.wav" with three-digit names, and
%   returns a summary table plus the modelled partial table.
%
%   Name-value options:
%     MidiNotes     column vector of notes to render (default 21:108)
%     WritePartials also return the partial table (default true)
%     Verbose       print one progress line per note (default true)

arguments
    preset struct
    outDir {mustBeTextScalar}
    options.MidiNotes (:,1) double {mustBeInteger, mustBeGreaterThanOrEqual(options.MidiNotes, 21), ...
        mustBeLessThanOrEqual(options.MidiNotes, 108)} = (21:108).'
    options.WritePartials (1,1) logical = true
    options.Verbose (1,1) logical = true
end

if ~isfolder(outDir)
    mkdir(outDir);
end
stale = dir(fullfile(outDir, "*.wav"));
for k = 1:numel(stale)
    delete(fullfile(stale(k).folder, stale(k).name));
end

notes = noteTable();
noteCount = numel(options.MidiNotes);
summaryRows = cell(noteCount, 1);
partialRows = cell(noteCount, 1);
for idx = 1:noteCount
    midi = options.MidiNotes(idx);
    [x, partials] = synthesizeNote(preset, midi);
    fileName = sprintf("%03d.wav", midi);
    filePath = fullfile(outDir, fileName);
    audiowrite(filePath, x, preset.fs, "BitsPerSample", 16);
    targetCount = min(8, numel(partials.freqHz));
    m = analyzeNote(x, preset.fs, partials.freqHz(1:targetCount));
    summaryRows{idx} = table( ...
        string(preset.name), midi, notes.name(midi - 20), notes.freqHz(midi - 20), ...
        numel(partials.n), m.centroidHz, m.slopeDbPerOct, m.rms, m.peak, fileName, ...
        'VariableNames', {'preset', 'midi', 'note', 'freqHz', 'partials', ...
        'centroidHz', 'slopeDbPerOct', 'rms', 'peak', 'file'});
    if options.WritePartials
        partialRows{idx} = table( ...
            repmat(string(preset.name), numel(partials.n), 1), ...
            repmat(midi, numel(partials.n), 1), partials.n, partials.freqHz, ...
            20 * log10(max(partials.amplitude, eps)), partials.tauS, ...
            'VariableNames', {'preset', 'midi', 'n', 'freqHz', 'amplitudeDb', 'tauS'});
    end
    if options.Verbose
        fprintf("%-6s %-3s %3d: %3d partials, centroid %5.0f Hz\n", ...
            preset.name, notes.name(midi - 20), midi, numel(partials.n), m.centroidHz);
    end
end
summary = vertcat(summaryRows{:});
if options.WritePartials
    partialsTable = vertcat(partialRows{:});
else
    partialsTable = table();
end
end

function writeReviewPage(summary, reviewDir, splitMidi)
%WRITEREVIEWPAGE Write the A/B listening page used to choose the crossover.
%   writeReviewPage(summary, reviewDir) writes reviewDir/index.html with one
%   bass and one guitar player per MIDI note, plus reviewDir/pairs.csv with
%   the same information as a table. Audio paths are relative to the page, so
%   the whole out/ directory can be opened from any local browser.
%   writeReviewPage(summary, reviewDir, splitMidi) records the chosen split
%   in the page header and marks that row.

arguments
    summary table
    reviewDir {mustBeTextScalar}
    splitMidi (1,1) double = NaN
end

if ~isfolder(reviewDir)
    mkdir(reviewDir);
end
midiValues = (21:108).';
notes = noteTable();
pairRows = cell(numel(midiValues), 1);

htmlPath = fullfile(reviewDir, "index.html");
identifier = fopen(htmlPath, "w");
if identifier < 0
    error("synthNotes:fileWrite", "Cannot write '%s'.", htmlPath);
end
cleanup = onCleanup(@() fclose(identifier));

fprintf(identifier, '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">\n');
fprintf(identifier, '<title>String instrument crossover review</title>\n');
fprintf(identifier, '<style>\n');
fprintf(identifier, 'body{font-family:system-ui,sans-serif;margin:2rem;max-width:1100px}\n');
fprintf(identifier, 'table{border-collapse:collapse;width:100%%;font-size:0.9rem}\n');
fprintf(identifier, 'th,td{border-bottom:1px solid #ddd;padding:0.35rem 0.5rem;text-align:left}\n');
fprintf(identifier, 'th{position:sticky;top:0;background:#fafafa}\n');
fprintf(identifier, 'tr.boundary td{background:#fff6d6}\n');
fprintf(identifier, 'tr.chosen td{border-top:2px solid #333;border-bottom:2px solid #333}\n');
fprintf(identifier, 'audio{height:2rem;width:230px}\n');
fprintf(identifier, '.meta{color:#555;font-size:0.82rem}\n');
fprintf(identifier, '.note{font-weight:600;white-space:nowrap}\n');
fprintf(identifier, 'code{background:#f2f2f2;padding:0 0.2rem}\n');
fprintf(identifier, '</style></head><body>\n');
fprintf(identifier, '<h1>String instrument crossover review</h1>\n');
if isfinite(splitMidi)
    fprintf(identifier, '<p>Selected split: bass through <code>%s (MIDI %d)</code> ', ...
        notes.name(splitMidi - 20), splitMidi);
    fprintf(identifier, 'and guitar from <code>%s (MIDI %d)</code>. ', ...
        notes.name(splitMidi - 19), splitMidi + 1);
    fprintf(identifier, 'The marked row is the crossover.</p>\n');
else
    fprintf(identifier, '<p>Compare the exact one-shot files that will ship. The recommendation is ');
    fprintf(identifier, 'bass up to <code>C3 (MIDI 48)</code> and guitar from <code>C#3 (MIDI 49)</code>; ');
    fprintf(identifier, 'the amber rows are the region where both instruments are physically ');
    fprintf(identifier, 'playable (bass MIDI 28-67, guitar MIDI 40-88).</p>\n');
end
fprintf(identifier, '<p class="meta">All renders: mono, 48 kHz, 16-bit, RMS 0.120, 2.5 s, ');
fprintf(identifier, 'including the release tail. Files are <code>out/audio/candidates/&lt;instrument&gt;/&lt;midi&gt;.wav</code>.</p>\n');
fprintf(identifier, '<table>\n<thead><tr><th>MIDI</th><th>Note</th>');
fprintf(identifier, '<th>Bass</th><th>Guitar</th><th>Metrics (partials / centroid)</th></tr></thead>\n<tbody>\n');

for idx = 1:numel(midiValues)
    midi = midiValues(idx);
    bassRow = summary(summary.preset == "bass" & summary.midi == midi, :);
    guitarRow = summary(summary.preset == "guitar" & summary.midi == midi, :);
    rowClass = "";
    if midi >= 40 && midi <= 67
        rowClass = ' class="boundary"';
    end
    if midi == splitMidi
        rowClass = ' class="chosen"';
    end
    fprintf(identifier, '<tr%s><td>%03d</td><td class="note">%s</td>', ...
        rowClass, midi, notes.name(midi - 20));
    fprintf(identifier, '<td><audio controls preload="none" src="../audio/candidates/bass/%03d.wav"></audio></td>', midi);
    fprintf(identifier, '<td><audio controls preload="none" src="../audio/candidates/guitar/%03d.wav"></audio></td>', midi);
    fprintf(identifier, '<td class="meta">bass %3d / %5.0f Hz &middot; guitar %3d / %5.0f Hz</td>', ...
        bassRow.partials, bassRow.centroidHz, guitarRow.partials, guitarRow.centroidHz);
    fprintf(identifier, '</tr>\n');
    pairRows{idx} = table(midi, notes.name(midi - 20), bassRow.file, guitarRow.file, ...
        bassRow.partials, guitarRow.partials, bassRow.centroidHz, guitarRow.centroidHz, ...
        'VariableNames', {'midi', 'note', 'bassFile', 'guitarFile', ...
        'bassPartials', 'guitarPartials', 'bassCentroidHz', 'guitarCentroidHz'});
end

fprintf(identifier, '</tbody></table>\n');
fprintf(identifier, '<p class="meta">Choose one MIDI split: bass below or equal to it, guitar above it.</p>\n');
fprintf(identifier, '</body></html>\n');

pairs = vertcat(pairRows{:});
writetable(pairs, fullfile(reviewDir, "pairs.csv"));
end

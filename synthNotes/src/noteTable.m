function notes = noteTable()
%NOTETABLE Chromatic note table from A0 (MIDI 21) to C8 (MIDI 108).
%   notes = noteTable() returns an 88-by-1 table with the columns midi,
%   name, freqHz, guitarNative and bassNative. Frequencies use twelve-tone
%   equal temperament with A4 = 440 Hz. Native ranges are E2-E6 (MIDI
%   40-88) for a 24-fret guitar and E1-G4 (MIDI 28-67) for a 24-fret bass.

midi = (21:108).';
pitchClass = ["C" "C#" "D" "D#" "E" "F" "F#" "G" "G#" "A" "A#" "B"];
octave = floor(midi / 12) - 1;
pitchNames = reshape(pitchClass(mod(midi, 12) + 1), size(midi));
name = pitchNames + string(octave);
freqHz = 440 * 2.^((midi - 69) / 12);
guitarNative = midi >= 40 & midi <= 88;
bassNative = midi >= 28 & midi <= 67;
notes = table(midi, name, freqHz, guitarNative, bassNative);
end

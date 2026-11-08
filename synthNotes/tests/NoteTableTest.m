classdef NoteTableTest < matlab.unittest.TestCase
    %NOTE TABLETEST Contract of the A0-C8 note table.

    methods (TestClassSetup)
        function addSource(testCase)
            sourceDir = fullfile(fileparts(fileparts(mfilename("fullpath"))), "src");
            testCase.applyFixture(matlab.unittest.fixtures.PathFixture(sourceDir));
        end
    end

    methods (Test)
        function testSizeAndEndpoints(testCase)
            notes = noteTable();
            testCase.verifyEqual(height(notes), 88);
            testCase.verifyEqual(notes.midi, (21:108).');
            testCase.verifyEqual(notes.name(1), "A0");
            testCase.verifyEqual(notes.name(end), "C8");
            testCase.verifyEqual(notes.freqHz(1), 27.5, AbsTol=1e-9);
        end

        function testConcertA(testCase)
            notes = noteTable();
            row = notes(notes.midi == 69, :);
            testCase.verifyEqual(row.name, "A4");
            testCase.verifyEqual(row.freqHz, 440, AbsTol=1e-9);
        end

        function testFrequencyFormula(testCase)
            notes = noteTable();
            expected = 440 * 2.^((notes.midi - 69) / 12);
            testCase.verifyEqual(notes.freqHz, expected, AbsTol=1e-9);
        end

        function testNamesAreUnique(testCase)
            notes = noteTable();
            testCase.verifyEqual(numel(unique(notes.name)), 88);
        end

        function testNativeRanges(testCase)
            notes = noteTable();
            testCase.verifyEqual(find(notes.guitarNative, 1), 20);      % E2, MIDI 40
            testCase.verifyEqual(find(notes.guitarNative, 1, "last"), 68); % E6, MIDI 88
            testCase.verifyEqual(find(notes.bassNative, 1), 8);        % E1, MIDI 28
            testCase.verifyEqual(find(notes.bassNative, 1, "last"), 47); % G4, MIDI 67
        end
    end
end

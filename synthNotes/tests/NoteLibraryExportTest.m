classdef NoteLibraryExportTest < matlab.unittest.TestCase
    %NOTELIBRARYEXPORTTEST WAV naming, format and summary contract.

    properties
        TempDir
    end

    methods (TestClassSetup)
        function addSource(testCase)
            sourceDir = fullfile(fileparts(fileparts(mfilename("fullpath"))), "src");
            testCase.applyFixture(matlab.unittest.fixtures.PathFixture(sourceDir));
        end
    end

    methods (TestMethodSetup)
        function createTempDir(testCase)
            testCase.TempDir = string(tempname);
            mkdir(testCase.TempDir);
            testCase.addTeardown(@() rmdir(testCase.TempDir, "s"));
        end
    end

    methods (Test)
        function testWavContract(testCase)
            preset = instrumentPreset("bass");
            summary = writeNoteLibrary(preset, testCase.TempDir, ...
                MidiNotes=[21; 45; 69], WritePartials=false, Verbose=false);
            testCase.verifyEqual(height(summary), 3);
            testCase.verifyTrue(isfile(fullfile(testCase.TempDir, "021.wav")));
            info = audioinfo(fullfile(testCase.TempDir, "021.wav"));
            testCase.verifyEqual(info.NumChannels, 1);
            testCase.verifyEqual(info.SampleRate, 48000);
            testCase.verifyEqual(info.BitsPerSample, 16);
            testCase.verifyEqual(info.TotalSamples, 120000);
        end

        function testDecodedRms(testCase)
            preset = instrumentPreset("bass");
            writeNoteLibrary(preset, testCase.TempDir, ...
                MidiNotes=[21; 69], WritePartials=false, Verbose=false);
            [y, fs] = audioread(fullfile(testCase.TempDir, "069.wav"));
            testCase.verifyEqual(fs, 48000);
            testCase.verifyEqual(sqrt(mean(y.^2)), 0.120, AbsTol=0.002);
        end

        function testSummaryColumns(testCase)
            preset = instrumentPreset("bass");
            summary = writeNoteLibrary(preset, testCase.TempDir, ...
                MidiNotes=[21; 45], WritePartials=false, Verbose=false);
            expected = ["preset" "midi" "note" "freqHz" "partials" ...
                "centroidHz" "slopeDbPerOct" "rms" "peak" "file"];
            testCase.verifyEqual(summary.Properties.VariableNames, cellstr(expected));
        end

        function testPartialsTable(testCase)
            preset = instrumentPreset("bass");
            [~, partials] = writeNoteLibrary(preset, testCase.TempDir, ...
                MidiNotes=[21; 45], WritePartials=true, Verbose=false);
            expected = ["preset" "midi" "n" "freqHz" "amplitudeDb" "tauS"];
            testCase.verifyEqual(partials.Properties.VariableNames, cellstr(expected));
            testCase.verifyGreaterThan(height(partials), 100);
            testCase.verifyTrue(all(partials.tauS > 0));
        end
    end
end

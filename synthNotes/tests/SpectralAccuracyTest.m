classdef SpectralAccuracyTest < matlab.unittest.TestCase
    %SPECTRALACCURACYTEST FFT checks of the synthesized partial structure.

    properties (TestParameter)
        presetName = struct("guitar", "guitar", "bass", "bass");
        midiNote = struct("A0", 21, "A4", 69, "C8", 108);
    end

    methods (TestClassSetup)
        function addSource(testCase)
            sourceDir = fullfile(fileparts(fileparts(mfilename("fullpath"))), "src");
            testCase.applyFixture(matlab.unittest.fixtures.PathFixture(sourceDir));
        end
    end

    methods (Test)
        function testPartialFrequencies(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            [x, partials] = synthesizeNote(preset, midiNote);
            count = min(5, numel(partials.freqHz));
            measurement = analyzeNote(x, preset.fs, partials.freqHz(1:count));
            testCase.verifyEqual(measurement.measuredFreqHz, ...
                partials.freqHz(1:count), RelTol=0.003);
        end

        function testFundamentalMatchesEqualTemperament(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            [x, partials] = synthesizeNote(preset, midiNote);
            measurement = analyzeNote(x, preset.fs, partials.freqHz(1));
            expected = 440 * 2^((midiNote - 69) / 12);
            testCase.verifyEqual(measurement.measuredFreqHz(1), expected, RelTol=0.002);
        end

        function testNoEnergyAboveNyquistGuard(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            [x, partials] = synthesizeNote(preset, midiNote);
            measurement = analyzeNote(x, preset.fs, partials.freqHz(1));
            testCase.verifyLessThan(measurement.highBandDbRel, -60);
        end

        function testInharmonicityApplied(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            [~, partials] = synthesizeNote(preset, midiNote);
            testCase.verifyGreaterThan(partials.freqHz(2) / partials.freqHz(1), 2);
        end

        function testInverseFftMatchesAdditiveModel(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            fftNote = synthesizeNote(preset, midiNote);
            additiveNote = synthesizeNoteAdditive(preset, midiNote);
            residualDb = 20 * log10(norm(fftNote - additiveNote) / norm(fftNote));
            testCase.verifyLessThan(residualDb, -40);
        end
    end
end

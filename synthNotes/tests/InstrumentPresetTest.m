classdef InstrumentPresetTest < matlab.unittest.TestCase
    %INSTRUMENTPRESETTEST Validity of the guitar and bass parameter sets.

    properties (TestParameter)
        presetName = struct("guitar", "guitar", "bass", "bass");
    end

    methods (TestClassSetup)
        function addSource(testCase)
            sourceDir = fullfile(fileparts(fileparts(mfilename("fullpath"))), "src");
            testCase.applyFixture(matlab.unittest.fixtures.PathFixture(sourceDir));
        end
    end

    methods (Test)
        function testCommonFields(testCase, presetName)
            preset = instrumentPreset(presetName);
            testCase.verifyEqual(preset.fs, 48000);
            testCase.verifyEqual(preset.durationS, 2.5);
            testCase.verifyEqual(preset.rmsTarget, 0.120);
            testCase.verifyEqual(preset.fftLength, 2^20);
        end

        function testDampingAndInharmonicityRanges(testCase, presetName)
            preset = instrumentPreset(presetName);
            testCase.verifyGreaterThan(preset.tau1, 0);
            testCase.verifyGreaterThanOrEqual(preset.inharmonicityB, 1e-6);
            testCase.verifyLessThanOrEqual(preset.inharmonicityB, 1e-3);
        end

        function testBodyModesAscending(testCase, presetName)
            preset = instrumentPreset(presetName);
            testCase.verifyTrue(issorted(preset.bodyResHz));
            testCase.verifyEqual(numel(preset.bodyResHz), numel(preset.bodyQ));
            testCase.verifyEqual(numel(preset.bodyResHz), numel(preset.bodyPeakGain));
        end

        function testPluckPositionInRange(testCase, presetName)
            preset = instrumentPreset(presetName);
            testCase.verifyGreaterThan(preset.pluckBeta, 0);
            testCase.verifyLessThan(preset.pluckBeta, 0.5);
        end

        function testUnknownPresetFails(testCase)
            testCase.verifyError(@() instrumentPreset("piano"), "synthNotes:unknownPreset");
        end
    end
end

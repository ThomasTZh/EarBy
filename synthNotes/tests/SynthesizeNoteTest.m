classdef SynthesizeNoteTest < matlab.unittest.TestCase
    %SYNTHESIZENOTETEST Export contract of one rendered note.

    properties (TestParameter)
        presetName = struct("guitar", "guitar", "bass", "bass");
        midiNote = struct("A0", 21, "A2", 45, "A4", 69, "C7", 96, "C8", 108);
    end

    methods (TestClassSetup)
        function addSource(testCase)
            sourceDir = fullfile(fileparts(fileparts(mfilename("fullpath"))), "src");
            testCase.applyFixture(matlab.unittest.fixtures.PathFixture(sourceDir));
        end
    end

    methods (Test)
        function testFormat(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            x = synthesizeNote(preset, midiNote);
            testCase.verifySize(x, [round(preset.durationS * preset.fs), 1]);
            testCase.verifyTrue(isreal(x));
            testCase.verifyTrue(all(isfinite(x)));
        end

        function testRmsAndPeak(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            x = synthesizeNote(preset, midiNote);
            testCase.verifyEqual(sqrt(mean(x.^2)), preset.rmsTarget, AbsTol=0.0015);
            testCase.verifyLessThan(max(abs(x)), 0.98);
        end

        function testAttackHasNoLeadingSilence(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            x = synthesizeNote(preset, midiNote);
            window = x(1:round(0.005 * preset.fs));
            testCase.verifyGreaterThan(sqrt(mean(window.^2)), 0.005);
        end

        function testReleaseTailEndsAtZero(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            x = synthesizeNote(preset, midiNote);
            tail = x(end-round(0.001 * preset.fs)+1:end);
            testCase.verifyLessThan(sqrt(mean(tail.^2)), 1e-3);
        end

        function testDeterministic(testCase, presetName, midiNote)
            preset = instrumentPreset(presetName);
            testCase.verifyEqual(synthesizeNote(preset, midiNote), ...
                synthesizeNote(preset, midiNote));
        end

        function testPresetsDiffer(testCase, midiNote)
            guitar = synthesizeNote(instrumentPreset("guitar"), midiNote);
            bass = synthesizeNote(instrumentPreset("bass"), midiNote);
            testCase.verifyGreaterThan(norm(guitar - bass) / norm(guitar), 0.05);
        end
    end
end

function x = synthesizeNoteAdditive(preset, midiNote)
%SYNTHESIZENOTEADDITIVE Direct time-domain reference for the FFT synthesis.
%   x = synthesizeNoteAdditive(preset, midiNote) evaluates
%
%       x(t) = sum_n phase_n*A_n*exp(-t/tau_n)*cos(2*pi*f_n*t)
%
%   sample by sample over the final note length. It exists so the tests can
%   prove that the inverse-FFT path reproduces the intended additive model,
%   and it is not used to produce exported audio.

arguments
    preset struct
    midiNote (1,1) double {mustBeInteger, mustBeGreaterThanOrEqual(midiNote, 21), ...
        mustBeLessThanOrEqual(midiNote, 108)}
end

f0 = 440 * 2^((midiNote - 69) / 12);
partials = partialModel(preset, f0);
frameCount = round(preset.durationS * preset.fs);
t = (0:frameCount-1).' / preset.fs;
x = zeros(frameCount, 1);
for k = 1:numel(partials.n)
    x = x + partials.phaseSign(k) * partials.amplitude(k) * ...
        exp(-t / partials.tauS(k)) .* cos(2 * pi * partials.freqHz(k) * t);
end
x = applyNoteFades(x, preset.fs, preset.attackS, preset.releaseS);
x = normalizeNoteRms(x, preset.rmsTarget, preset.peakLimit);
end

function [x, partials] = synthesizeNote(preset, midiNote)
%SYNTHESIZENOTE Render one mono note by inverse FFT of a modelled spectrum.
%   [x, partials] = synthesizeNote(preset, midiNote) builds a one-sided
%   spectrum as a sum of Lorentzian peaks, one per string partial:
%
%       S(f) = sum_n phase_n*A_n*( 1/(1/tau_n + 1i*2*pi*(f - f_n))
%                                + 1/(1/tau_n + 1i*2*pi*(f + f_n)) )
%
%   The inverse transform of that pair is A_n*exp(-t/tau_n)*cos(2*pi*f_n*t),
%   so the inverse FFT output is the additive string model with one transform
%   per note instead of one oscillator per partial. Both terms are needed for
%   low notes: omitting the negative-frequency image leaves out a broad tail
%   that is worth about 3 percent of the waveform at A0. The transform is long
%   (fftLength/fs seconds) so long decays are resolved; the note is cropped to
%   durationS, faded, and normalized to rmsTarget.

arguments
    preset struct
    midiNote (1,1) double {mustBeInteger, mustBeGreaterThanOrEqual(midiNote, 21), ...
        mustBeLessThanOrEqual(midiNote, 108)}
end

f0 = 440 * 2^((midiNote - 69) / 12);
partials = partialModel(preset, f0);

nfft = preset.fftLength;
frequencyBins = (0:nfft/2).' * (preset.fs / nfft);
omega = 2 * pi * frequencyBins;
spectrum = complex(zeros(nfft/2 + 1, 1));
for k = 1:numel(partials.n)
    omega0 = 2 * pi * partials.freqHz(k);
    inverseTau = 1 / partials.tauS(k);
    spectrum = spectrum + partials.phaseSign(k) * partials.amplitude(k) * ...
        (1 ./ (inverseTau + 1i * (omega - omega0)) + ...
         1 ./ (inverseTau + 1i * (omega + omega0)));
end
spectrum(1) = real(spectrum(1));
spectrum(end) = real(spectrum(end));

twoSided = [spectrum; conj(spectrum(end-1:-1:2))];
wave = real(ifft(twoSided));
frameCount = round(preset.durationS * preset.fs);
x = wave(1:frameCount);
x = applyNoteFades(x, preset.fs, preset.attackS, preset.releaseS);
x = normalizeNoteRms(x, preset.rmsTarget, preset.peakLimit);
end

function partials = partialModel(preset, f0)
%PARTIALMODEL Modal frequencies, amplitudes and decay times of one note.
%   partials = partialModel(preset, f0) returns a struct with the column
%   vectors n, freqHz, amplitude, tauS and phaseSign for the modelled
%   plucked string:
%
%       f_n     = n * f0 * sqrt(1 + B * n^2)          (string stiffness)
%       A_n     = |sin(pi*n*beta) * sin(pi*n*u)| / n^p * |H_body(f_n)|
%       tau_n   = tau1 / n^q                          (faster HF decay)
%       phase_n = sign(sin(pi*n*beta) * sin(pi*n*u))
%
%   Partials at or above nyquistFactor*fs are removed, zero-amplitude comb
%   nulls are skipped (they do not end the series), and amplitudes below
%   amplitudeFloorDb relative to the strongest partial are dropped.

arguments
    preset struct
    f0 (1,1) double {mustBeReal, mustBeFinite, mustBePositive}
end

limitHz = preset.nyquistFactor * preset.fs;
n = (1:preset.maxPartials).';
freq = n .* f0 .* sqrt(1 + preset.inharmonicityB .* n.^2);
keep = freq < limitHz;
n = n(keep);
freq = freq(keep);
if isempty(n)
    n = 1;
    freq = f0;
end

comb = sin(pi * n * preset.pluckBeta);
if isfinite(preset.pickupU)
    comb = comb .* sin(pi * n * preset.pickupU);
end
phase = sign(comb);
amplitude = abs(comb) .* bodyResponse(preset, freq) ./ (n .^ preset.slopeP);

keep = phase ~= 0 & amplitude > 0 & isfinite(amplitude);
n = n(keep);
freq = freq(keep);
phase = phase(keep);
amplitude = amplitude(keep);

if isempty(n)
    n = 1;
    freq = f0;
    phase = 1;
    amplitude = 1;
else
    floorValue = max(amplitude) * 10^(preset.amplitudeFloorDb / 20);
    keep = amplitude >= floorValue;
    n = n(keep);
    freq = freq(keep);
    phase = phase(keep);
    amplitude = amplitude(keep) / max(amplitude(keep));
end

tau = preset.tau1 ./ (n .^ preset.tauExponentQ);
partials = struct( ...
    "n", n, ...
    "freqHz", freq, ...
    "amplitude", amplitude, ...
    "tauS", tau, ...
    "phaseSign", phase);
end

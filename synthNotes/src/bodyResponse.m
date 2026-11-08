function magnitude = bodyResponse(preset, freqHz)
%BODYRESPONSE Relative radiated magnitude of the instrument body or pickup.
%   magnitude = bodyResponse(preset, freqHz) returns the magnitude response
%   of the preset's body model, normalized to unit gain at 1 kHz. The
%   response is a flat baseline with a set of broad second-order peaks at
%   the measured body-mode frequencies, plus an optional high-pass and a
%   second-order low-pass. Only the magnitude is used; the phase of the
%   body is not modelled.

arguments
    preset struct
    freqHz double {mustBeReal, mustBeNonnegative}
end

f = freqHz(:);
magnitude = responseAt(preset, f) / responseAt(preset, 1000);
end

function h = responseAt(preset, f)
h = ones(size(f));
for k = 1:numel(preset.bodyResHz)
    u = f / preset.bodyResHz(k);
    bandpass = (u / preset.bodyQ(k)) ./ sqrt((1 - u.^2).^2 + (u / preset.bodyQ(k)).^2);
    h = h + preset.bodyPeakGain(k) * bandpass;
end
if isfinite(preset.highpassHz)
    u = f / preset.highpassHz;
    highpass = u.^2 ./ sqrt((1 - u.^2).^2 + (u / preset.highpassQ).^2);
    h = h .* highpass;
end
u = f / preset.lowpassHz;
lowpass = 1 ./ sqrt((1 - u.^2).^2 + (u / preset.lowpassQ).^2);
h = h .* lowpass;
end

function [x, info] = normalizeNoteRms(x, targetRms, peakLimit)
%NORMALIZENOTERMS Scale a note to a target RMS without exceeding a peak limit.
%   [x, info] = normalizeNoteRms(x, targetRms, peakLimit) scales x so that
%   its RMS equals targetRms. If that would push the absolute peak above
%   peakLimit, the whole note is scaled down to the peak limit and
%   info.peakLimited is true. The export requires RMS 0.060, so a limited
%   note is an anomaly that callers record and tests reject.

x = x(:);
rmsBefore = sqrt(mean(x.^2));
if rmsBefore <= 0
    error("synthNotes:silentNote", "Cannot normalize a silent note.");
end
gain = targetRms / rmsBefore;
x = x * gain;
peak = max(abs(x));
limited = peak > peakLimit;
if limited
    x = x * (peakLimit / peak);
end
info = struct( ...
    "gain", gain, ...
    "peakLimited", limited, ...
    "rms", sqrt(mean(x.^2)), ...
    "peak", max(abs(x)));
end

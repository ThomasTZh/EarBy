function m = analyzeNote(x, fs, targetFreqHz)
%ANALYZENOTE Spectral measurements of a rendered note.
%   m = analyzeNote(x, fs, targetFreqHz) takes a Hann-windowed FFT of the
%   steady part of the note and measures, for every expected partial
%   frequency in targetFreqHz, the nearest spectral peak with parabolic
%   interpolation. It also reports RMS, peak, spectral centroid, the
%   peak-level slope in dB per octave, and the highest level above
%   0.46*fs relative to the spectral maximum.

arguments
    x double {mustBeReal, mustBeFinite}
    fs (1,1) double {mustBeReal, mustBePositive}
    targetFreqHz (:,1) double {mustBeReal, mustBePositive}
end

x = x(:);
m = struct();
m.rms = sqrt(mean(x.^2));
m.peak = max(abs(x));
m.rmsDbfs = 20 * log10(max(m.rms, eps));
m.peakDbfs = 20 * log10(max(m.peak, eps));

segmentStart = min(round(0.25 * fs) + 1, numel(x));
segmentEnd = min(round(1.25 * fs), numel(x));
if segmentEnd - segmentStart < 16
    segmentStart = 1;
    segmentEnd = numel(x);
end
segment = x(segmentStart:segmentEnd);
nfft = 2^nextpow2(2 * numel(segment));
window = hann(numel(segment));
spectrum = abs(fft(segment .* window, nfft));
half = nfft / 2 + 1;
spectrum = spectrum(1:half);
frequency = (0:half-1).' * fs / nfft;
power = spectrum.^2;
m.centroidHz = sum(frequency .* power) / max(sum(power), eps);

binWidth = fs / nfft;
measured = zeros(numel(targetFreqHz), 1);
for k = 1:numel(targetFreqHz)
    halfWidth = max(3 * binWidth, 0.01 * targetFreqHz(k));
    firstBin = find(frequency >= targetFreqHz(k) - halfWidth, 1, "first");
    lastBin = find(frequency <= targetFreqHz(k) + halfWidth, 1, "last");
    if isempty(firstBin) || isempty(lastBin) || lastBin <= firstBin
        measured(k) = NaN;
        continue
    end
    [~, offset] = max(spectrum(firstBin:lastBin));
    index = firstBin + offset - 1;
    delta = 0;
    if index > 1 && index < half
        y1 = log(spectrum(index-1) + eps);
        y2 = log(spectrum(index) + eps);
        y3 = log(spectrum(index+1) + eps);
        denominator = y1 - 2 * y2 + y3;
        if abs(denominator) > 0
            delta = 0.5 * (y1 - y3) / denominator;
            delta = max(min(delta, 0.5), -0.5);
        end
    end
    measured(k) = (index - 1 + delta) * binWidth;
end
m.targetFreqHz = targetFreqHz;
m.measuredFreqHz = measured;
m.errorCents = 1200 * log2(measured ./ targetFreqHz);

peakCount = min(8, numel(measured));
peakLevelDb = 20 * log10(max(...
    interp1(frequency, spectrum, measured(1:peakCount), "linear", "extrap"), eps));
if peakCount >= 2
    fit = polyfit(log2(measured(1:peakCount)), peakLevelDb, 1);
    m.slopeDbPerOct = fit(1);
else
    m.slopeDbPerOct = NaN;
end

highBandStart = find(frequency >= 0.46 * fs, 1, "first");
if isempty(highBandStart)
    m.highBandDbRel = -Inf;
else
    m.highBandDbRel = 20 * log10(max(spectrum(highBandStart:end)) / max(spectrum));
end
end

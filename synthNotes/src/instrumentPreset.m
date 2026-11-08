function preset = instrumentPreset(presetName)
%INSTRUMENTPRESET Parameters of the supported plucked-string models.
%   preset = instrumentPreset("guitar") returns the steel-string acoustic
%   guitar model. instrumentPreset("bass") returns the electric bass model.
%   The model, its parameters and their sources are documented in README.md.
%
%   Common fields (SI units unless stated):
%     fs, durationS, rmsTarget, peakLimit, attackS, releaseS
%     slopeP, pluckBeta, pickupU, inharmonicityB, tau1, tauExponentQ
%     bodyResHz, bodyQ, bodyPeakGain, highpassHz/highpassQ,
%     lowpassHz/lowpassQ, fftLength, nyquistFactor, maxPartials,
%     amplitudeFloorDb, nativeMidi, sources

arguments
    presetName {mustBeTextScalar}
end

presetName = lower(string(presetName));
switch presetName
    case "guitar"
        preset = guitarPreset();
    case "bass"
        preset = bassPreset();
    otherwise
        error("synthNotes:unknownPreset", ...
            "Unknown instrument preset '%s'. Use 'guitar' or 'bass'.", presetName);
end
validatePreset(preset);
end

function p = guitarPreset()
p.name = "guitar";
p.displayName = "Steel-string acoustic guitar";
p.fs = 48000;
p.durationS = 2.5;
p.rmsTarget = 0.120;
p.peakLimit = 0.98;
p.attackS = 0.0015;
p.releaseS = 0.050;
p.slopeP = 1.55;
p.pluckBeta = 0.20;
p.pickupU = NaN;
p.inharmonicityB = 4.0e-5;
p.tau1 = 1.10;
p.tauExponentQ = 0.70;
p.bodyResHz = [106 216 268 431 553 1010];
p.bodyQ = [30 25 25 20 20 15];
p.bodyPeakGain = [0.40 0.64 0.48 0.28 0.16 0.04];
p.highpassHz = 80;
p.highpassQ = 0.7;
p.lowpassHz = 1500;
p.lowpassQ = 0.5;
p.fftLength = 2^20;
p.nyquistFactor = 0.45;
p.maxPartials = 1000;
p.amplitudeFloorDb = -80;
p.nativeMidi = [40 88];
p.sources = [ ...
    "H. Wright, The Acoustics and Psychoacoustics of the Guitar, PhD thesis, 1996"; ...
    "D. A. Russell, The Plucked Fixed-Fixed String, Penn State Acoustics"; ...
    "J. Wolfe, Euphonics section 7.2, Choosing strings"];
end

function p = bassPreset()
p.name = "bass";
p.displayName = "Electric bass guitar";
p.fs = 48000;
p.durationS = 2.5;
p.rmsTarget = 0.120;
p.peakLimit = 0.98;
p.attackS = 0.0015;
p.releaseS = 0.050;
p.slopeP = 1.55;
p.pluckBeta = 0.18;
p.pickupU = 0.20;
p.inharmonicityB = 3.0e-5;
p.tau1 = 1.50;
p.tauExponentQ = 0.75;
p.bodyResHz = [95 700];
p.bodyQ = [1.2 2.0];
p.bodyPeakGain = [0.28 0.04];
p.highpassHz = NaN;
p.highpassQ = 0.7;
p.lowpassHz = 900;
p.lowpassQ = 0.5;
p.fftLength = 2^20;
p.nyquistFactor = 0.45;
p.maxPartials = 1000;
p.amplitudeFloorDb = -80;
p.nativeMidi = [28 67];
p.sources = [ ...
    "H. Wright, The Acoustics and Psychoacoustics of the Guitar, PhD thesis, 1996"; ...
    "D. A. Russell, The Plucked Fixed-Fixed String, Penn State Acoustics"; ...
    "Wikipedia, Pickup (music technology), resonance of inductance and cable capacitance"];
end

function validatePreset(p)
requiredFields = ["name" "displayName" "fs" "durationS" "rmsTarget" ...
    "peakLimit" "attackS" "releaseS" "slopeP" "pluckBeta" "pickupU" ...
    "inharmonicityB" "tau1" "tauExponentQ" "bodyResHz" "bodyQ" ...
    "bodyPeakGain" "highpassHz" "highpassQ" "lowpassHz" "lowpassQ" ...
    "fftLength" "nyquistFactor" "maxPartials" "amplitudeFloorDb" ...
    "nativeMidi" "sources"];
for k = 1:numel(requiredFields)
    field = requiredFields(k);
    if ~isfield(p, field)
        error("synthNotes:presetField", "Preset '%s' is missing field '%s'.", p.name, field);
    end
end
if numel(p.bodyResHz) ~= numel(p.bodyQ) || numel(p.bodyResHz) ~= numel(p.bodyPeakGain)
    error("synthNotes:presetField", ...
        "Preset '%s' has inconsistent body resonance arrays.", p.name);
end
if ~isscalar(p.fs) || p.fs <= 0 || p.durationS <= 0 || p.rmsTarget <= 0
    error("synthNotes:presetField", "Preset '%s' has invalid sample-rate fields.", p.name);
end
if p.pluckBeta < 0 || p.pluckBeta >= 1
    error("synthNotes:presetField", "Preset '%s' has an invalid pluck position.", p.name);
end
end

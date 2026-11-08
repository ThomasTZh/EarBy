function x = applyNoteFades(x, fs, attackS, releaseS)
%APPLYNOTEFADES Raised-cosine attack and release ramps.
%   x = applyNoteFades(x, fs, attackS, releaseS) fades the first attackS
%   seconds in and the last releaseS seconds out. The first and last
%   samples become exactly zero, so a one-shot never starts or ends with a
%   step.

x = x(:);
attacksamples = max(1, round(attackS * fs));
releasesamples = max(1, round(releaseS * fs));
attacksamples = min(attacksamples, numel(x));
releasesamples = min(releasesamples, numel(x));
attackRamp = 0.5 - 0.5 * cos(pi * linspace(0, 1, attacksamples).');
releaseRamp = 0.5 + 0.5 * cos(pi * linspace(0, 1, releasesamples).');
x(1:attacksamples) = x(1:attacksamples) .* attackRamp;
if releasesamples >= 1
    x(end-releasesamples+1:end) = x(end-releasesamples+1:end) .* releaseRamp;
end
end

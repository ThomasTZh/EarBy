import com.sun.media.sound.AudioSynthesizer;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

/**
 * Independent playback check for the exported SoundFont.
 *
 * Loads the font through the JDK's Gervill synthesizer, renders every key
 * 21-108 offline at 48 kHz mono, verifies that each one produces sound, and
 * checks that A0, A2, A4, C6 and C8 contain the expected fundamental and at
 * least three of its harmonics. The fundamental check uses a narrow search
 * around the expected frequency; the harmonic check accepts a weak
 * fundamental (a bridge pickup can suppress it) as long as the partial
 * series is coherent.
 *
 *   java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED \
 *        tools/Sf2RenderProbe.java out/string_synth.sf2
 */
public final class Sf2RenderProbe {
    private static final int SAMPLE_RATE = 48000;
    private static final int FFT_SIZE = 65536;
    private static final int[] PITCH_KEYS = {21, 45, 69, 84, 108};

    private Sf2RenderProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Sf2RenderProbe <soundfont.sf2>");
            System.exit(2);
        }
        File fontFile = new File(args[0]);
        if (!fontFile.isFile()) {
            System.err.println("missing soundfont: " + fontFile);
            System.exit(2);
        }

        Soundbank bank = MidiSystem.getSoundbank(fontFile);
        int instrumentCount = bank.getInstruments().length;
        System.out.printf("soundbank=%s instruments=%d first=%s%n",
                bank.getName(), instrumentCount,
                instrumentCount > 0 ? bank.getInstruments()[0].getName() : "none");
        if (instrumentCount != 1) {
            System.out.println("FAIL: expected exactly one preset/instrument");
            System.exit(1);
        }

        AudioSynthesizer synth = (AudioSynthesizer) MidiSystem.getSynthesizer();
        AudioFormat format = new AudioFormat(48000.0f, 16, 1, true, false);
        Map<String, Object> info = new HashMap<>();
        AudioInputStream stream = synth.openStream(format, info);
        synth.loadAllInstruments(bank);
        Receiver receiver = synth.getReceiver();
        receiver.send(new ShortMessage(0xC0, 0, 0, 0), -1);

        int renderFrames = (int) (2.0 * SAMPLE_RATE);
        int tailFrames = (int) (2.6 * SAMPLE_RATE);
        boolean ok = true;
        int silentKeys = 0;
        int analyzed = 0;
        double minRms = Double.MAX_VALUE;
        double maxRms = 0.0;

        for (int key = 21; key <= 108; key++) {
            receiver.send(new ShortMessage(0x90, 0, key, 100), -1);
            byte[] audio = readFrames(stream, renderFrames);
            receiver.send(new ShortMessage(0x80, 0, key, 0), -1);
            readFrames(stream, tailFrames);

            double rms = rms(audio);
            minRms = Math.min(minRms, rms);
            maxRms = Math.max(maxRms, rms);
            if (rms < 5.0e-4) {
                System.out.printf("SILENT key %d rms %.2e%n", key, rms);
                silentKeys++;
                ok = false;
            }
            if (indexOf(PITCH_KEYS, key) >= 0) {
                double expected = 440.0 * Math.pow(2.0, (key - 69) / 12.0);
                double[] check = checkHarmonics(audio, expected);
                double errorPercent = 100.0 * (check[0] / expected - 1.0);
                System.out.printf(
                        "pitch key %3d expected %8.2f Hz fundamental %8.2f Hz (%+.2f%%), "
                                + "matched harmonics %d%n",
                        key, expected, check[0], errorPercent, (int) check[1]);
                if (Math.abs(errorPercent) > 2.0 || check[1] < 3) {
                    ok = false;
                }
                analyzed++;
            }
        }

        System.out.printf("rendered keys=88 silent=%d pitchChecks=%d rmsRange=%.4f..%.4f%n",
                silentKeys, analyzed, minRms, maxRms);
        System.out.println(ok ? "PASS" : "FAIL");
        stream.close();
        synth.close();
        System.exit(ok ? 0 : 1);
    }

    private static int indexOf(int[] values, int target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == target) {
                return index;
            }
        }
        return -1;
    }

    private static byte[] readFrames(AudioInputStream stream, int frames) throws java.io.IOException {
        byte[] buffer = new byte[2 * frames];
        int offset = 0;
        while (offset < buffer.length) {
            int count = stream.read(buffer, offset, buffer.length - offset);
            if (count < 0) {
                throw new java.io.EOFException("synthesis stream ended early");
            }
            offset += count;
        }
        return buffer;
    }

    private static double rms(byte[] audio) {
        int frames = audio.length / 2;
        double sum = 0.0;
        for (int index = 0; index < frames; index++) {
            int low = audio[2 * index] & 0xFF;
            int high = audio[2 * index + 1];
            short value = (short) ((high << 8) | low);
            sum += (double) value * value;
        }
        return Math.sqrt(sum / frames) / 32768.0;
    }

    /** Returns {fundamentalHz, number of distinct matched harmonic numbers}. */
    private static double[] checkHarmonics(byte[] audio, double expectedF0) {
        int frames = audio.length / 2;
        int start = Math.min((int) (0.10 * SAMPLE_RATE), Math.max(0, frames - 1));
        int length = Math.min((int) (0.60 * SAMPLE_RATE), frames - start);
        double[] real = new double[FFT_SIZE];
        double[] imag = new double[FFT_SIZE];
        for (int index = 0; index < length; index++) {
            int low = audio[2 * (start + index)] & 0xFF;
            int high = audio[2 * (start + index) + 1];
            short value = (short) ((high << 8) | low);
            double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * index / (length - 1));
            real[index] = value * window;
        }
        fft(real, imag);
        int half = FFT_SIZE / 2;
        double[] magnitude = new double[half];
        double maximum = 0.0;
        for (int index = 0; index < half; index++) {
            magnitude[index] = Math.hypot(real[index], imag[index]);
            maximum = Math.max(maximum, magnitude[index]);
        }
        double binHz = (double) SAMPLE_RATE / FFT_SIZE;

        int lowBin = Math.max(2, (int) Math.ceil(0.97 * expectedF0 / binHz));
        int highBin = Math.min(half - 2, (int) Math.floor(1.03 * expectedF0 / binHz));
        int fundamentalBin = lowBin;
        for (int index = lowBin; index <= highBin; index++) {
            if (magnitude[index] > magnitude[fundamentalBin]) {
                fundamentalBin = index;
            }
        }
        double fundamental = interpolate(magnitude, fundamentalBin, binHz);

        Set<Integer> matched = new HashSet<>();
        int harmonicLimit = Math.min(half - 2, (int) Math.floor(Math.min(15000.0,
                expectedF0 * 12.0) / binHz));
        for (int index = Math.max(2, (int) Math.ceil(20.0 / binHz));
                index <= harmonicLimit; index++) {
            if (magnitude[index] <= magnitude[index - 1] || magnitude[index] < magnitude[index + 1]) {
                continue;
            }
            if (magnitude[index] < 0.01 * maximum) {
                continue;
            }
            double frequency = interpolate(magnitude, index, binHz);
            long harmonic = Math.round(frequency / expectedF0);
            if (harmonic < 1 || harmonic > 12) {
                continue;
            }
            if (Math.abs(frequency / (harmonic * expectedF0) - 1.0) < 0.015) {
                matched.add((int) harmonic);
            }
        }
        return new double[] {fundamental, matched.size()};
    }

    /** Log-magnitude parabolic interpolation of a spectral peak. */
    private static double interpolate(double[] magnitude, int peak, double binHz) {
        if (peak <= 0 || peak >= magnitude.length - 1) {
            return peak * binHz;
        }
        double y1 = Math.log(magnitude[peak - 1] + 1.0e-12);
        double y2 = Math.log(magnitude[peak] + 1.0e-12);
        double y3 = Math.log(magnitude[peak + 1] + 1.0e-12);
        double denominator = y1 - 2.0 * y2 + y3;
        double delta = 0.0;
        if (Math.abs(denominator) > 0.0) {
            delta = 0.5 * (y1 - y3) / denominator;
            delta = Math.max(-0.5, Math.min(0.5, delta));
        }
        return (peak + delta) * binHz;
    }

    private static void fft(double[] real, double[] imag) {
        int size = real.length;
        int levels = Integer.numberOfTrailingZeros(size);
        for (int index = 0; index < size; index++) {
            int reversed = Integer.reverse(index) >>> (32 - levels);
            if (reversed > index) {
                double temporaryReal = real[index];
                real[index] = real[reversed];
                real[reversed] = temporaryReal;
                double temporaryImag = imag[index];
                imag[index] = imag[reversed];
                imag[reversed] = temporaryImag;
            }
        }
        for (int length = 2; length <= size; length <<= 1) {
            double angle = -2.0 * Math.PI / length;
            double stepReal = Math.cos(angle);
            double stepImag = Math.sin(angle);
            for (int offset = 0; offset < size; offset += length) {
                double rotationReal = 1.0;
                double rotationImag = 0.0;
                for (int index = 0; index < length / 2; index++) {
                    int even = offset + index;
                    int odd = even + length / 2;
                    double oddReal = real[odd] * rotationReal - imag[odd] * rotationImag;
                    double oddImag = real[odd] * rotationImag + imag[odd] * rotationReal;
                    real[odd] = real[even] - oddReal;
                    imag[odd] = imag[even] - oddImag;
                    real[even] += oddReal;
                    imag[even] += oddImag;
                    double nextReal = rotationReal * stepReal - rotationImag * stepImag;
                    rotationImag = rotationReal * stepImag + rotationImag * stepReal;
                    rotationReal = nextReal;
                }
            }
        }
    }
}

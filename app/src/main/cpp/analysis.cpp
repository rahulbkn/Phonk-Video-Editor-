#include "analysis.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <iomanip>
#include <limits>
#include <sstream>

namespace phonk {
namespace {

constexpr double kPi = 3.14159265358979323846;

// Cooperative analysis cancellation flag. The JNI layer sets this from
// Kotlin when the user taps "cancel"; the DSP loop polls it so the native
// thread can exit early instead of burning CPU to completion.
static std::atomic<bool> g_analysisCancelled{false};

void normalize(std::vector<double>& v) {
    double mx = 0.0;
    for (double x : v) mx = std::max(mx, std::fabs(x));
    if (mx <= 1e-9) return;
    for (double& x : v) x /= mx;
}

double mean(const std::vector<double>& v, size_t from, size_t to) {
    if (v.empty()) return 0.0;
    if (from >= to) return 0.0;
    if (from >= v.size()) return 0.0;
    if (to > v.size()) to = v.size();
    if (from >= to) return 0.0;
    double s = 0.0;
    for (size_t i = from; i < to; ++i) s += v[i];
    return s / static_cast<double>(to - from);
}

double sumRange(const std::vector<double>& arr, size_t from, size_t to) {
    if (arr.empty() || from >= to) return 0.0;
    if (from >= arr.size()) return 0.0;
    if (to > arr.size()) to = arr.size();
    double s = 0.0;
    for (size_t i = from; i < to; ++i) s += arr[i];
    return s;
}

std::vector<double> downsample(const std::vector<double>& src, size_t max) {
    if (src.empty()) return {};
    size_t cap = std::min(max, src.size());
    std::vector<double> out(cap);
    if (cap == 0) return out;
    for (size_t i = 0; i < cap; ++i) {
        size_t start = i * src.size() / cap;
        size_t end = (i + 1) * src.size() / cap;
        if (end <= start) end = start + 1;
        out[i] = mean(src, start, end);
    }
    return out;
}

}  // namespace

// ---------------------------------------------------------------------------
void setAnalysisCancelled(bool v) {
    g_analysisCancelled.store(v);
}

bool isAnalysisCancelled() {
    return g_analysisCancelled.load();
}

// ---------------------------------------------------------------------------
void fft(double* re, double* im, std::size_t n, bool inverse) {
    if (n < 2) return;
    for (std::size_t i = 1, j = 0; i < n; ++i) {
        std::size_t bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
            std::swap(re[i], re[j]);
            std::swap(im[i], im[j]);
        }
    }
    for (std::size_t len = 2; len <= n; len <<= 1) {
        double ang = (inverse ? 2.0 : -2.0) * kPi / static_cast<double>(len);
        double wRe = std::cos(ang), wIm = std::sin(ang);
        for (std::size_t i = 0; i < n; i += len) {
            double uRe = 1.0, uIm = 0.0;
            for (std::size_t j = 0; j < len / 2; ++j) {
                double aRe = re[i + j], aIm = im[i + j];
                double bRe = re[i + j + len / 2] * uRe - im[i + j + len / 2] * uIm;
                double bIm = re[i + j + len / 2] * uIm + im[i + j + len / 2] * uRe;
                re[i + j] = aRe + bRe;
                im[i + j] = aIm + bIm;
                double nRe = uRe * wRe - uIm * wIm;
                uIm = uRe * wIm + uIm * wRe;
                uRe = nRe;
            }
        }
    }
    if (inverse) {
        for (std::size_t i = 0; i < n; ++i) {
            re[i] /= static_cast<double>(n);
            im[i] /= static_cast<double>(n);
        }
    }
}

// ---------------------------------------------------------------------------
AnalysisFeatures extractFeatures(const float* pcm, size_t count, int sampleRate) {
    AnalysisFeatures f;
    f.sampleRate = sampleRate;
    f.windowSize = 1024;
    f.hopSize = 512;
    if (sampleRate <= 0 || count < f.windowSize) return f;

    const size_t nFrames = (count - f.windowSize) / f.hopSize + 1;
    f.frameCount = nFrames;
    f.durationSec = static_cast<double>(count) / sampleRate;

    std::vector<double> window(f.windowSize);
    for (size_t i = 0; i < f.windowSize; ++i)
        window[i] = 0.5 - 0.5 * std::cos(2.0 * kPi * i / static_cast<double>(f.windowSize - 1));

    std::vector<double> re(f.windowSize, 0.0);
    std::vector<double> im(f.windowSize, 0.0);

    f.rms.resize(nFrames);
    f.flux.resize(nFrames, 0.0);
    f.bassEnergy.resize(nFrames);
    f.kickEnv.resize(nFrames);
    f.snareEnv.resize(nFrames);
    f.spectralCentroid.resize(nFrames);
    f.spectralFlatness.resize(nFrames);
    f.onsetEnvelope.resize(nFrames);

    const size_t bins = f.windowSize / 2 + 1;
    std::vector<double> prevMag(bins, 0.0);
    const double nyquist = sampleRate * 0.5;
    const double binHz = nyquist / static_cast<double>(bins - 1);

    const size_t bassLo = static_cast<size_t>(50.0 / binHz);
    const size_t bassHi = static_cast<size_t>(250.0 / binHz);
    const size_t snareLo = static_cast<size_t>(1500.0 / binHz);
    const size_t snareHi = static_cast<size_t>(5000.0 / binHz);

    for (size_t n = 0; n < nFrames; ++n) {
        if (isAnalysisCancelled()) {
            // Return partially built features so the caller can abort cleanly.
            f.frameCount = n;
            f.rms.resize(n);
            f.flux.resize(n);
            f.bassEnergy.resize(n);
            f.kickEnv.resize(n);
            f.snareEnv.resize(n);
            f.spectralCentroid.resize(n);
            f.spectralFlatness.resize(n);
            f.onsetEnvelope.resize(n);
            return f;
        }
        size_t start = n * f.hopSize;
        double energy = 0.0;
        for (size_t i = 0; i < f.windowSize; ++i) {
            double s = pcm[start + i];
            re[i] = s * window[i];
            im[i] = 0.0;
            energy += s * s;
        }
        f.rms[n] = std::sqrt(energy / static_cast<double>(f.windowSize));

        fft(re.data(), im.data(), f.windowSize, false);

        std::vector<double> mag(bins);
        for (size_t k = 0; k < bins; ++k)
            mag[k] = std::sqrt(re[k] * re[k] + im[k] * im[k]) / f.windowSize;

        double bass = 0.0, snare = 0.0;
        size_t bl = std::min(bassLo, bins), bh = std::min(bassHi, bins);
        for (size_t k = bl; k < bh; ++k) bass += mag[k] * mag[k];
        size_t sl = std::min(snareLo, bins), sh = std::min(snareHi, bins);
        for (size_t k = sl; k < sh; ++k) snare += mag[k] * mag[k];

        f.bassEnergy[n] = bass;
        f.kickEnv[n] = bass;
        f.snareEnv[n] = snare;

        double num = 0.0, den = 0.0;
        double geoSum = 0.0, arith = 0.0;
        int geoCount = 0;
        for (size_t k = 1; k < bins; ++k) {
            double v = mag[k];
            num += v * (k * binHz);
            den += v;
            arith += v;
            if (v > 1e-12) {
                geoSum += std::log(v);
                ++geoCount;
            }
        }
        f.spectralCentroid[n] = den > 0 ? num / den : 0.0;
        if (geoCount > 4 && arith > 0) {
            double geoMean = std::exp(geoSum / geoCount);
            f.spectralFlatness[n] = geoMean / (arith / bins);
        } else {
            f.spectralFlatness[n] = 1.0;
        }

        if (n > 0) {
            double fl = 0.0;
            for (size_t k = 0; k < bins; ++k) {
                double d = mag[k] - prevMag[k];
                fl += d > 0 ? d : 0.0;
            }
            f.flux[n] = fl;
        }
        prevMag = std::move(mag);
    }

    normalize(f.rms);
    normalize(f.flux);
    normalize(f.bassEnergy);
    normalize(f.kickEnv);
    normalize(f.snareEnv);

    for (size_t n = 0; n < nFrames; ++n) {
        double burst = 0.0;
        if (n > 0) {
            double dK = f.kickEnv[n] - f.kickEnv[n - 1];
            double dS = f.snareEnv[n] - f.snareEnv[n - 1];
            burst = 0.75 * std::max(0.0, dK) + 0.25 * std::max(0.0, dS);
        }
        f.onsetEnvelope[n] = f.flux[n] + 0.7 * burst;
    }
    normalize(f.onsetEnvelope);
    return f;
}

// ---------------------------------------------------------------------------
double estimateBpm(const AnalysisFeatures& feat) {
    const size_t n = feat.frameCount;
    if (n < 12) return 0.0;
    const double hop = static_cast<double>(feat.hopSize) / feat.sampleRate;
    const size_t minLag = static_cast<size_t>((60.0 / 240.0) / hop);
    const size_t maxLag = static_cast<size_t>((60.0 / 40.0) / hop);
    if (maxLag - minLag < 4 || maxLag >= n) return 0.0;
    std::vector<double> ac(n, 0.0);
    for (size_t t = 0; t < n; ++t) {
        const double o = feat.onsetEnvelope[t];
        if (o <= 0.0) continue;
        for (size_t lag = minLag; lag <= maxLag; ++lag) {
            size_t u = t + lag;
            if (u >= n) break;
            ac[lag] += o * feat.onsetEnvelope[u];
        }
    }
    double bestScore = -1.0, bestBpm = 0.0;
    size_t bestLag = 0;
    for (size_t lag = minLag; lag <= maxLag; ++lag) {
        double period = lag * hop;
        if (period <= 0) continue;
        double bpm = 60.0 / period;
        if (bpm < 40.0 || bpm > 240.0) continue;
        double score = ac[lag];
        if (lag > 1) score += 0.5 * ac[lag / 2];
        if (lag * 2 <= maxLag) score += 0.5 * ac[lag * 2];
        if (score > bestScore) {
            bestScore = score;
            bestBpm = bpm;
            bestLag = lag;
        }
    }
    if (bestScore <= 0) return 0.0;
    // Sub-frame refinement: parabolic interpolation of the autocorrelation
    // peak removes integer-lag quantization (a frame is ~46 ms at 11025 Hz).
    if (bestLag > minLag && bestLag < maxLag) {
        const double y0 = ac[bestLag - 1], y1 = ac[bestLag], y2 = ac[bestLag + 1];
        const double den = y0 - 2.0 * y1 + y2;
        if (std::fabs(den) > 1e-12) {
            const double delta = 0.5 * (y0 - y2) / den;
            const double lagF = static_cast<double>(bestLag) + delta;
            if (lagF >= 1.0) {
                double period = lagF * hop;
                if (period > 0) {
                    double bpm = 60.0 / period;
                    if (bpm >= 40.0 && bpm <= 240.0) bestBpm = bpm;
                }
            }
        }
    }
    return bestBpm;
}

// ---------------------------------------------------------------------------
std::vector<size_t> detectBeatFrames(const AnalysisFeatures& feat, double bpm) {
    std::vector<size_t> result;
    if (bpm <= 0) return result;
    const size_t n = feat.frameCount;
    const double hopSec = static_cast<double>(feat.hopSize) / feat.sampleRate;
    double period = std::round((60.0 / bpm) / hopSec);
    if (period < 1) return result;
    size_t P = static_cast<size_t>(period);

    double bestSum = -1.0;
    size_t bestPhase = 0;
    for (size_t phase = 0; phase < P; ++phase) {
        double s = 0.0;
        for (size_t k = phase; k < n; k += P) s += feat.onsetEnvelope[k];
        if (s > bestSum) {
            bestSum = s;
            bestPhase = phase;
        }
    }
    for (size_t k = bestPhase; k < n; k += P) result.push_back(k);
    return result;
}

// ---------------------------------------------------------------------------
std::vector<bool> detectDownbeats(const std::vector<double>& beatsMs,
                                  const AnalysisFeatures& feat) {
    std::vector<bool> out(beatsMs.size(), false);
    if (beatsMs.size() < 8 || feat.frameCount < 4) return out;
    const double hopSec = static_cast<double>(feat.hopSize) / feat.sampleRate;
    double span = beatsMs.back() - beatsMs.front();
    if (span <= 0) return out;
    double avgPeriodMs = span / static_cast<double>(beatsMs.size() - 1);
    (void)avgPeriodMs;
    size_t bar = 4;

    double best = -1.0;
    size_t bestPhaseIdx = 0;
    for (size_t b = 0; b < bar; ++b) {
        double s = 0.0;
        for (size_t i = b; i < beatsMs.size(); i += bar) {
            size_t frame = static_cast<size_t>(beatsMs[i] / 1000.0 / hopSec);
            if (frame >= feat.frameCount) frame = feat.frameCount - 1;
            if (frame == 0) continue;
            s += feat.bassEnergy[frame] + 0.5 * feat.rms[frame];
        }
        if (s > best) {
            best = s;
            bestPhaseIdx = b;
        }
    }
    for (size_t i = bestPhaseIdx; i < beatsMs.size(); i += bar) out[i] = true;
    return out;
}

// ---------------------------------------------------------------------------
std::vector<DropMarker> detectDrops(const AnalysisFeatures& feat,
                                    const std::vector<size_t>& beatFrames,
                                    double bpm, double durationMs) {
    (void)beatFrames;
    (void)durationMs;
    std::vector<DropMarker> out;
    const size_t n = feat.frameCount;
    if (n < 16 || bpm <= 0) return out;

    const double hopSec = static_cast<double>(feat.hopSize) / feat.sampleRate;
    double beatSec = 60.0 / bpm;
    size_t window = std::max<size_t>(2, static_cast<size_t>((beatSec * 4) / hopSec));

    const double wEnergy = 0.20, wBass = 0.25, wOnset = 0.20, wSpectral = 0.15, wLow = 0.20;

    std::vector<double> score(n, 0.0);
    for (size_t f = window; f + window < n; ++f) {
        size_t b0 = f - window, a0 = f, a1 = f + window;
        double eBefore = mean(feat.rms, b0, a0);
        double eAfter = mean(feat.rms, a0, a1);
        double deltaEnergy = eBefore > 1e-6 ? eAfter / eBefore : 0.0;

        double bassBefore = mean(feat.bassEnergy, b0, a0);
        double bassAfter = mean(feat.bassEnergy, a0, a1);
        double deltaBass = bassBefore > 1e-6 ? bassAfter / bassBefore : 0.0;

        double onsetBefore = sumRange(feat.onsetEnvelope, b0, a0);
        double onsetAfter = sumRange(feat.onsetEnvelope, a0, a1);
        double deltaOnset = onsetBefore > 1e-6 ? onsetAfter / onsetBefore : 0.0;

        double scBefore = mean(feat.spectralCentroid, b0, a0);
        double scAfter = mean(feat.spectralCentroid, a0, a1);
        double deltaSpectral = scBefore > 1e-6 ? scAfter / scBefore : 0.0;

        double lowBefore = mean(feat.kickEnv, b0, a0);
        double lowAfter = mean(feat.kickEnv, a0, a1);
        double deltaLow = lowBefore > 1e-6 ? lowAfter / lowBefore : 0.0;

        score[f] = wEnergy * deltaEnergy + wBass * deltaBass + wOnset * deltaOnset +
                   wSpectral * deltaSpectral + wLow * deltaLow;
    }

    std::vector<double> smoothed(n, 0.0);
    const size_t halfWin = 4;
    for (size_t f = 0; f < n; ++f) {
        size_t a = f > halfWin ? f - halfWin : 0;
        size_t b = std::min(n, f + halfWin + 1);
        smoothed[f] = mean(score, a, b);
    }

    double best = 0.0;
    for (double v : smoothed) best = std::max(best, v);
    if (best <= 0) return out;
    double threshold = best * 0.45;
    if (threshold < 1.0) threshold = 1.0;
    double minGapSec = beatSec * 2.0;

    double lastDropF = -1e18;
    for (size_t f = 0; f < n; ++f) {
        size_t a = f > halfWin ? f - halfWin : 0;
        size_t b = std::min(n, f + halfWin + 1);
        bool isLocal = true;
        for (size_t j = a; j < b; ++j) {
            if (j != f && smoothed[j] > smoothed[f]) {
                isLocal = false;
                break;
            }
        }
        if (!isLocal || smoothed[f] < threshold) continue;
        if ((static_cast<double>(f) - lastDropF) * hopSec < minGapSec) continue;
        lastDropF = static_cast<double>(f);

        DropMarker m;
        m.timeMs = static_cast<double>(f) * hopSec * 1000.0;
        m.strength = smoothed[f] / best;
        m.confidence = 0.7 * m.strength + 0.3 * (smoothed[f] / (smoothed[f] + 1.0));
        if (m.confidence > 1.0) m.confidence = 1.0;
        m.type = "section_drop";
        out.push_back(m);
    }
    return out;
}

// ---------------------------------------------------------------------------
std::vector<AudioSection> detectSections(const AnalysisFeatures& feat,
                                         const std::vector<DropMarker>& drops,
                                         double bpm) {
    (void)bpm;
    std::vector<AudioSection> out;
    const size_t n = feat.frameCount;
    if (n == 0) return out;
    const double hopSec = static_cast<double>(feat.hopSize) / feat.sampleRate;

    for (const auto& d : drops) {
        AudioSection a;
        a.type = "drop";
        a.startMs = std::max(0.0, d.timeMs - 600.0);
        a.endMs = d.timeMs + 900.0;
        a.energy = d.strength;
        out.push_back(a);
    }

    double silenceStartMs = 0.0;
    bool inSilence = false;
    for (size_t f = 0; f < n; ++f) {
        bool quiet = feat.rms[f] < 0.05;
        if (quiet && !inSilence) {
            inSilence = true;
            silenceStartMs = static_cast<double>(f) * hopSec * 1000.0;
        } else if (!quiet && inSilence) {
            double endMs = static_cast<double>(f) * hopSec * 1000.0;
            if (endMs - silenceStartMs > 600) {
                AudioSection s;
                s.type = "silence";
                s.startMs = silenceStartMs;
                s.endMs = endMs;
                out.push_back(s);
            }
            inSilence = false;
        }
    }
    if (inSilence) {
        double endMs = static_cast<double>(n) * hopSec * 1000.0;
        if (endMs - silenceStartMs > 600) {
            AudioSection s;
            s.type = "silence";
            s.startMs = silenceStartMs;
            s.endMs = endMs;
            out.push_back(s);
        }
    }

    std::sort(out.begin(), out.end(),
              [](const AudioSection& a, const AudioSection& b) { return a.startMs < b.startMs; });
    return out;
}

// ---------------------------------------------------------------------------
static void classifyDrops(const AnalysisFeatures& feat, double bpm,
                          std::vector<DropMarker>* drops) {
    const double hopSec = static_cast<double>(feat.hopSize) / feat.sampleRate;
    double beatSec = bpm > 0 ? 60.0 / bpm : 0.5;
    size_t beatFrames = static_cast<size_t>(beatSec / hopSec);
    if (beatFrames == 0) beatFrames = 1;

    for (auto& d : *drops) {
        size_t frame = static_cast<size_t>(d.timeMs / 1000.0 / hopSec);
        if (frame >= feat.frameCount) frame = feat.frameCount - 1;
        size_t backWin = frame > 8 * beatFrames ? 8 * beatFrames : frame;
        double startE = mean(feat.rms, backWin > frame ? 0 : backWin, frame);
        bool quietBefore = startE < 0.04;

        double m6 = mean(feat.rms, frame > 6 ? frame - 6 : 0, frame);
        double bwd = mean(feat.rms, frame > 6 * 3 ? frame - 6 * 3 : 0, frame);
        bool ramp = m6 > 1.4 * bwd + 1e-3;

        if (quietBefore) {
            d.type = "silence_drop";
        } else if (ramp) {
            d.type = "build_up_drop";
        } else {
            size_t a1 = std::min(feat.frameCount, frame + 4 * beatFrames);
            double bBefore = mean(feat.bassEnergy, backWin, frame);
            double bAfter = mean(feat.bassEnergy, frame, a1);
            d.type = (bAfter > 1.3 * bBefore) ? "bass_switch" : "hard_drop";
        }
    }
}

// ---------------------------------------------------------------------------
AnalysisResult analyzeAudio(const float* pcm, size_t count, int sampleRate) {
    AnalysisResult res;
    res.sampleRate = sampleRate;
    res.durationMs = count > 0 ? static_cast<double>(count) * 1000.0 / sampleRate : 0.0;
    if (count < 1024) return res;

    if (isAnalysisCancelled()) return res;

    AnalysisFeatures feat = extractFeatures(pcm, count, sampleRate);
    if (isAnalysisCancelled()) return res;
    res.bpm = estimateBpm(feat);

    std::vector<size_t> beatFrames = detectBeatFrames(feat, res.bpm);
    double hopMs = static_cast<double>(feat.hopSize) * 1000.0 / feat.sampleRate;

    for (size_t i = 0; i < beatFrames.size(); ++i) {
        BeatMarker b;
        b.timeMs = static_cast<double>(beatFrames[i]) * hopMs;
        b.beatIndex = static_cast<int>(i);
        size_t a = beatFrames[i] > 2 ? beatFrames[i] - 2 : 0;
        size_t c = std::min(feat.frameCount, beatFrames[i] + 3);
        double local = mean(feat.onsetEnvelope, a, c);
        b.confidence = std::min(1.0, local / 0.6);
        res.beats.push_back(b);
    }

    std::vector<double> beatsMs(res.beats.size());
    for (size_t i = 0; i < res.beats.size(); ++i) beatsMs[i] = res.beats[i].timeMs;
    std::vector<bool> downbeats = detectDownbeats(beatsMs, feat);
    for (size_t i = 0; i < res.beats.size() && i < downbeats.size(); ++i)
        res.beats[i].downbeat = downbeats[i];

    res.drops = detectDrops(feat, beatFrames, res.bpm, res.durationMs / 1000.0);
    classifyDrops(feat, res.bpm, &res.drops);
    res.sections = detectSections(feat, res.drops, res.bpm);

    double bSum = 0.0;
    for (const auto& b : res.beats) bSum += b.confidence;
    res.beatConfidence = res.beats.empty() ? 0.0 : bSum / static_cast<double>(res.beats.size());
    double dSum = 0.0;
    for (const auto& d : res.drops) dSum += d.confidence;
    res.dropConfidence = res.drops.empty() ? 0.0 : dSum / static_cast<double>(res.drops.size());

    res.energyCurve = downsample(feat.rms, 256);
    res.fluxCurve = downsample(feat.flux, 256);
    return res;
}

// ---------------------------------------------------------------------------
std::string resultToJson(const AnalysisResult& r) {
    std::ostringstream o;
    o << std::setprecision(10);
    o << "{\"bpm\":" << r.bpm;
    o << ",\"sampleRate\":" << r.sampleRate;
    o << ",\"durationMs\":" << (long long)std::llround(r.durationMs);
    o << std::setprecision(6);
    o << ",\"beatConfidence\":" << r.beatConfidence;
    o << ",\"dropConfidence\":" << r.dropConfidence;
    o << ",\"beats\":[";
    for (size_t i = 0; i < r.beats.size(); ++i) {
        if (i) o << ",";
        o << "{\"timeMs\":" << (long long)std::llround(r.beats[i].timeMs)
          << ",\"confidence\":" << r.beats[i].confidence
          << ",\"beatIndex\":" << r.beats[i].beatIndex
          << ",\"downbeat\":" << (r.beats[i].downbeat ? "true" : "false") << "}";
    }
    o << "],\"drops\":[";
    for (size_t i = 0; i < r.drops.size(); ++i) {
        if (i) o << ",";
        o << "{\"timeMs\":" << (long long)std::llround(r.drops[i].timeMs)
          << ",\"confidence\":" << r.drops[i].confidence
          << ",\"strength\":" << r.drops[i].strength
          << ",\"type\":\"" << r.drops[i].type << "\"}";
    }
    o << "],\"sections\":[";
    for (size_t i = 0; i < r.sections.size(); ++i) {
        if (i) o << ",";
        o << "{\"type\":\"" << r.sections[i].type
          << "\",\"startMs\":" << (long long)std::llround(r.sections[i].startMs)
          << ",\"endMs\":" << (long long)std::llround(r.sections[i].endMs)
          << ",\"energy\":" << r.sections[i].energy << "}";
    }
    o << "],\"energyCurve\":[";
    for (size_t i = 0; i < r.energyCurve.size(); ++i) {
        if (i) o << ",";
        o << r.energyCurve[i];
    }
    o << "],\"fluxCurve\":[";
    for (size_t i = 0; i < r.fluxCurve.size(); ++i) {
        if (i) o << ",";
        o << r.fluxCurve[i];
    }
    o << "]}";
    return o.str();
}

}  // namespace phonk
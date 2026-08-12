#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

namespace phonk {

// Iterative radix-2 in-place FFT. n must be a power of two.
void fft(double* re, double* im, std::size_t n, bool inverse);

// Spectral analysis of a mono signal.
//
// Frame/feature extraction:
//  windowSize / hopSize (in samples) define the analysis frames.
// The audio is assumed normalized to [-1, 1].
struct AnalysisFeatures {
    int sampleRate = 0;
    size_t windowSize = 1024;
    size_t hopSize = 512;
    size_t frameCount = 0;
    double durationSec = 0.0;

    // Per-frame energy/onset signals (size == frameCount)
    std::vector<double> rms;
    std::vector<double> flux;
    std::vector<double> bassEnergy;   // 50..250 Hz
    std::vector<double> kickEnv;      // 50..250 Hz envelope (attack emphasis)
    std::vector<double> snareEnv;     // 1500..5000 Hz envelope
    std::vector<double> spectralCentroid;
    std::vector<double> spectralFlatness;

    // Beat grid is measured against the onset envelope.
    std::vector<double> onsetEnvelope;
};

struct BeatMarker {
    double timeMs = 0.0;
    double confidence = 0.0;
    int beatIndex = 0;
    bool downbeat = false;
};

struct DropMarker {
    double timeMs = 0.0;
    double confidence = 0.0;
    double strength = 0.0;
    std::string type = "section_drop";
};

struct AudioSection {
    double startMs = 0.0;
    double endMs = 0.0;
    std::string type = "section"; // "build" | "drop" | "silence" | "energy"
    double energy = 0.0;
};

struct AnalysisResult {
    int sampleRate = 0;
    double durationMs = 0.0;
    double bpm = 0.0;
    double beatConfidence = 0.0;
    double dropConfidence = 0.0;
    std::vector<BeatMarker> beats;
    std::vector<DropMarker> drops;
    std::vector<AudioSection> sections;
    // downsized energy+flux for waveform drawing (<=ACAV reliability, capped)
    std::vector<double> energyCurve;
    std::vector<double> fluxCurve;
};

// Extract analysis features from mono PCM.
AnalysisFeatures extractFeatures(const float* pcm, size_t count, int sampleRate);

// Estimate BPM from onset envelope via autocorrelation + harmonic weighting.
double estimateBpm(const AnalysisFeatures& feat);

// Detect beats (dynamic-programming phase tracking on the onset envelope).
// Returns frame indices of beat onsets.
std::vector<size_t> detectBeatFrames(const AnalysisFeatures& feat, double bpm);

// Detect downbeats: pick periodic bar-level accents from bass energy alignment.
// beatsMs must be sorted ascending. Returns a parallel vector of booleans.
std::vector<bool> detectDownbeats(const std::vector<double>& beatsMs,
                                  const AnalysisFeatures& feat);

// Detect drop candidates using the multi-signal drop score.
std::vector<DropMarker> detectDrops(const AnalysisFeatures& feat,
                                    const std::vector<size_t>& beatFrames,
                                    double bpm, double durationMs);

std::vector<AudioSection> detectSections(const AnalysisFeatures& feat,
                                         const std::vector<DropMarker>& drops,
                                         double bpm);

// Full entry point used by JNI nativeAnalyzeAudio.
AnalysisResult analyzeAudio(const float* pcm, size_t count, int sampleRate);

// Cooperative cancellation: set before calling analyzeAudio, check inside
// the DSP loop. Returns true when the caller requested cancellation.
void setAnalysisCancelled(bool v);
bool isAnalysisCancelled();

// Convert AnalysisResult to compact JSON (later parsed by Kotlin/Java).
std::string resultToJson(const AnalysisResult& result);

// ---------- Timeline engine ----------

struct CutSegment {
    long sourceStartMs = 0;
    long sourceEndMs = 0;
    long destStartMs = 0;
    long destEndMs = 0;
    std::string effect = "none"; // "flash" | "zoom" | "shake" | "glitch" | "none"
    double effectStrength = 0.0;
    bool dropTransition = false;
};

struct TimelinePlan {
    std::vector<CutSegment> segments;
    long totalDurationMs = 0;
    // Effects schedule aligned to destination timeline.
};

// Compute cut schedule + effect assignment from analysis JSON and strategy.
//   strategy bits: 'cutsPerBeat' = 0.5..8, 'emphasizeDrops', 'effectsEnabled'
std::string buildTimelinePlan(const std::string& analysisJson,
                              double cutSubdivision,
                              int windowHalfBeats,
                              bool emphasizeDrops,
                              bool effectsEnabled);

}
#include <jni.h>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

#include "analysis.h"
#include "json.h"

namespace {

std::vector<float> toFloatVector(JNIEnv* env, jfloatArray arr) {
    std::vector<float> out;
    if (!arr) return out;
    jsize n = env->GetArrayLength(arr);
    if (n <= 0) return out;
    jfloat* elems = env->GetFloatArrayElements(arr, nullptr);
    if (!elems) return out;
    out.assign(elems, elems + n);
    env->ReleaseFloatArrayElements(arr, elems, JNI_ABORT);
    return out;
}

std::string toCppString(JNIEnv* env, jstring str) {
    if (!str) return std::string();
    const char* chars = env->GetStringUTFChars(str, nullptr);
    if (!chars) return std::string();
    std::string out(chars);
    env->ReleaseStringUTFChars(str, chars);
    return out;
}

jstring toJavaString(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

}  // namespace

extern "C" {

static jstring analyzeAudioToJson(JNIEnv* env, const std::vector<float>& pcm, int sampleRate) {
    if (pcm.empty()) return toJavaString(env, "{}");
    phonk::AnalysisResult res = phonk::analyzeAudio(pcm.data(), pcm.size(), sampleRate);
    std::string out = phonk::resultToJson(res);
    return toJavaString(env, out);
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeAnalyzeAudio (JNIEnv* env, jclass /*clazz*/, jfloatArray pcm, jint sampleRate) {
    auto v = toFloatVector(env, pcm);
    return analyzeAudioToJson(env, v, sampleRate);
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeDetectBeats (JNIEnv* env, jclass /*clazz*/, jfloatArray pcm, jint sampleRate) {
    std::vector<float> v = toFloatVector(env, pcm);
    if (v.empty()) return toJavaString(env, "{\"beats\":[]}");
    phonk::AnalysisResult res = phonk::analyzeAudio(v.data(), v.size(), sampleRate);
    std::ostringstream o;
    o << "{\"bpm\":" << res.bpm << ",\"beats\":[";
    for (size_t i = 0; i < res.beats.size(); ++i) {
        if (i) o << ",";
        o << "{\"timeMs\":" << (long long)std::llround(res.beats[i].timeMs)
          << ",\"confidence\":" << res.beats[i].confidence
          << ",\"beatIndex\":" << res.beats[i].beatIndex
          << ",\"downbeat\":" << (res.beats[i].downbeat ? "true" : "false") << "}";
    }
    o << "]}";
    return toJavaString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeDetectDrops (JNIEnv* env, jclass /*clazz*/, jfloatArray pcm, jint sampleRate) {
    std::vector<float> v = toFloatVector(env, pcm);
    if (v.empty()) return toJavaString(env, "{\"drops\":[]}");
    phonk::AnalysisResult res = phonk::analyzeAudio(v.data(), v.size(), sampleRate);
    std::ostringstream o;
    o << "{\"bpm\":" << std::setprecision(10) << res.bpm << ",\"drops\":[";
    for (size_t i = 0; i < res.drops.size(); ++i) {
        if (i) o << ",";
        o << "{\"timeMs\":" << (long long)std::llround(res.drops[i].timeMs)
          << ",\"confidence\":" << res.drops[i].confidence
          << ",\"strength\":" << res.drops[i].strength
          << ",\"type\":\"" << res.drops[i].type << "\"}";
    }
    o << "]}";
    return toJavaString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeExtractFrames (JNIEnv* env, jclass /*clazz*/, jfloatArray pcm, jint sampleRate,
                              jint windowSize, jint hopSize) {
    std::vector<float> v = toFloatVector(env, pcm);
    phonk::AnalysisFeatures f = phonk::extractFeatures(v.empty() ? nullptr : v.data(), v.size(),
                                                       static_cast<int>(sampleRate));
    f.windowSize = static_cast<size_t>(windowSize);
    f.hopSize = static_cast<size_t>(hopSize);
    std::ostringstream o;
    o << "{\"frameCount\":" << f.frameCount << ",\"sampleRate\":" << f.sampleRate
      << ",\"rms\":[";
    for (size_t i = 0; i < f.rms.size(); ++i) {
        if (i) o << ",";
        o << f.rms[i];
    }
    o << "],\"flux\":[";
    for (size_t i = 0; i < f.flux.size(); ++i) {
        if (i) o << ",";
        o << f.flux[i];
    }
    o << "],\"bassEnergy\":[";
    for (size_t i = 0; i < f.bassEnergy.size(); ++i) {
        if (i) o << ",";
        o << f.bassEnergy[i];
    }
    o << "],\"snareEnergy\":[";
    for (size_t i = 0; i < f.snareEnv.size(); ++i) {
        if (i) o << ",";
        o << f.snareEnv[i];
    }
    o << "]}";
    return toJavaString(env, o.str());
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeProcessTimeline (JNIEnv* env, jclass /*clazz*/, jstring analysisJson,
                                jdouble subdivision, jint windowHalfBeats,
                                jboolean emphasizeDrops, jboolean effectsEnabled) {
    std::string jsonStr = toCppString(env, analysisJson);
    std::string plan = phonk::buildTimelinePlan(jsonStr, static_cast<double>(subdivision),
                                                static_cast<int>(windowHalfBeats),
                                                emphasizeDrops == JNI_TRUE,
                                                effectsEnabled == JNI_TRUE);
    return toJavaString(env, plan);
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeVersion (JNIEnv* env, jclass /*clazz*/) {
    return toJavaString(env, "phonk-native-1.0.0");
}

extern "C" JNIEXPORT void JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeSetAnalysisCancelled (JNIEnv* env, jclass /*clazz*/, jboolean v) {
    phonk::setAnalysisCancelled(v == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL Java_dev_phonk_editor_native_PhonkNative_nativeIsAnalysisCancelled (JNIEnv* env, jclass /*clazz*/) {
    return phonk::isAnalysisCancelled() ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
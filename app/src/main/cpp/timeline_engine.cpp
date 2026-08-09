#include "analysis.h"

#include <algorithm>
#include <cmath>
#include <sstream>

#include "json.h"

namespace phonk {

// Builds the cut schedule (and effect assignment) from detection JSON.
// The plan is a list of segments; every segment maps a source span to a
// destination span. Destination spans are laid out back-to-back so that the
// final render is simply 'for each segment: cut source, append, apply effect'.
std::string buildTimelinePlan(const std::string& analysisJson, double cutSubdivision,
                              int windowHalfBeats, bool emphasizeDrops,
                              bool effectsEnabled) {
    JValue root;
    bool parsed = false;
    root = JValue::parse(analysisJson, &parsed);
    if (!parsed || root.type != JValue::OBJ) {
        return "{\"segments\":[],\"totalDurationMs\":0}";
    }

    const JValue* beatsNode = root.find("beats");
    const JValue* dropsNode = root.find("drops");

    std::vector<double> timesMs;
    if (beatsNode && beatsNode->type == JValue::ARR) {
        for (const auto& b : beatsNode->arr) {
            if (b.type == JValue::OBJ) {
                timesMs.push_back(b.numOf("timeMs", 0.0));
            }
        }
    }
    std::sort(timesMs.begin(), timesMs.end());

    std::vector<double> dropTimes;
    std::vector<double> dropStrength;
    if (dropsNode && dropsNode->type == JValue::ARR) {
        for (const auto& d : dropsNode->arr) {
            if (d.type == JValue::OBJ) {
                dropTimes.push_back(d.numOf("timeMs", 0.0));
                dropStrength.push_back(d.numOf("strength", 0.5));
            }
        }
    }

    if (timesMs.empty()) {
        return "{\"segments\":[],\"totalDurationMs\":0}";
    }

    double sub = cutSubdivision;  // beats per cut (e.g. 0.5, 1, 2, 4, 8)
    if (sub <= 0) sub = 1.0;
    if (sub > 16) sub = 16.0;

    double barMs = 0.0;
    const JValue* bpmNode = root.find("bpm");
    if (bpmNode && bpmNode->type == JValue::NUM && bpmNode->num > 0) {
        barMs = 60000.0 / bpmNode->num;
    }

    if (windowHalfBeats > 0 && !timesMs.empty()) {
        // optional: restrict to a window around audible content (not used by editor)
    }

    // Compute boundaries: for k-th beat fence starting at beat[0].
    std::vector<double> cuts;
    double start = timesMs.front();
    double end = timesMs.back();
    auto appendCut = [&](double t) {
        if (t > start - 0.5 && t < end + 0.5 && (cuts.empty() || t > cuts.back() + 0.5)) {
            cuts.push_back(t);
        }
    };

    double step = sub * (barMs > 0 ? barMs : (timesMs.size() > 1 ? (timesMs.back() - timesMs.front()) /
                                                         (timesMs.size() - 1)
                                                              : 500.0));
    if (step <= 1) step = 500.0;
    for (double t = start; t < end; t += step) appendCut(t);
    // always keep the very first/last fence
    appendCut(end);

    // Drop emphasis: force a cut exactly at each drop; pre-drop tightening.
    std::vector<double> dropEnvelope;  // not used for scheduling positions
    for (size_t i = 0; i < dropTimes.size(); ++i) {
        double t = dropTimes[i];
        appendCut(t);
        if (emphasizeDrops) {
            // progressively shorter cuts before the drop
            double s = step;
            for (int k = 1; k <= 4 && s > 40; ++k) {
                appendCut(t - s);
                s = s * 0.5;
            }
            // catch-up cut after the drop
            appendCut(t + step * 0.5);
        }
    }

    std::sort(cuts.begin(), cuts.end());
    std::vector<double> uniq;
    for (double c : cuts) {
        if (uniq.empty() || c - uniq.back() > 0.5) uniq.push_back(c);
    }

    std::vector<CutSegment> sessions;
    double depStart = 0.0;
    for (size_t i = 0; i + 1 < uniq.size(); ++i) {
        long sStart = static_cast<long>(std::llround(uniq[i]));
        long sEnd = static_cast<long>(std::llround(std::min(uniq[i + 1], end)));
        if (sEnd <= sStart) continue;
        CutSegment seg;
        seg.sourceStartMs = sStart;
        seg.sourceEndMs = sEnd;
        seg.destStartMs = static_cast<long>(std::llround(depStart));
        seg.destEndMs = seg.destStartMs + (sEnd - sStart);
        seg.effect = "none";
        seg.dropTransition = false;

        // Does this segment end on/near a drop beat?
        for (size_t d = 0; d < dropTimes.size(); ++d) {
            double dt = dropTimes[d];
            if (std::fabs(dt - static_cast<double>(sEnd)) < step * 0.5) {
                seg.dropTransition = true;
                if (effectsEnabled) {
                    double st = dropStrength[d];
                    if (st > 0.85) {
                        seg.effect = "flash";
                    } else if (st > 0.6) {
                        seg.effect = "zoom";
                    } else {
                        seg.effect = "shake";
                    }
                }
                seg.effectStrength = dropStrength[d];
                break;
            }
        }
        sessions.push_back(seg);
        depStart = static_cast<double>(seg.destEndMs);
    }

    std::ostringstream o;
    o << "{\"segments\":[";
    for (size_t i = 0; i < sessions.size(); ++i) {
        if (i) o << ",";
        const CutSegment& s = sessions[i];
        o << "{\"sourceStartMs\":" << s.sourceStartMs
          << ",\"sourceEndMs\":" << s.sourceEndMs
          << ",\"destStartMs\":" << s.destStartMs
          << ",\"destEndMs\":" << s.destEndMs
          << ",\"effect\":\"" << s.effect << "\""
          << ",\"effectStrength\":" << s.effectStrength
          << ",\"dropTransition\":" << (s.dropTransition ? "true" : "false") << "}";
    }
    long total = sessions.empty() ? 0 : static_cast<long>(sessions.back().destEndMs);
    o << "],\"totalDurationMs\":" << total << "}";
    return o.str();
}

}  // namespace phonk
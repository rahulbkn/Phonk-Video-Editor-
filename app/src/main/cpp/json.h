#pragma once
#include <string>
#include <utility>
#include <vector>

namespace phonk {

// Minimal JSON DOM used by the native timeline engine.
struct JValue {
    enum Type { NUL, BOOL, NUM, STR, ARR, OBJ };
    Type type = NUL;
    bool b = false;
    double num = 0.0;
    std::string str;
    std::vector<JValue> arr;
    std::vector<std::pair<std::string, JValue>> obj;

    static JValue parse(const std::string& text, bool* ok = nullptr);

    const JValue* find(const std::string& key) const;
    bool containsNumber(const std::string& key) const;
    double numOf(const std::string& key, double def = 0.0) const;
    bool boolOf(const std::string& key, bool def = false) const;
    std::string strOf(const std::string& key, const std::string& def = "") const;

    std::string stringify() const;
};

// Escape a raw string for JSON output.
std::string jsonEscape(const std::string& in);

}  // namespace phonk
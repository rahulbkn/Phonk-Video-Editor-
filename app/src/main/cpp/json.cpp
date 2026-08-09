#include "json.h"

#include <cctype>
#include <cmath>
#include <cstdio>
#include <sstream>

namespace phonk {

std::string jsonEscape(const std::string& in) {
    std::ostringstream o;
    for (char c : in) {
        switch (c) {
            case '"': o << "\\\""; break;
            case '\\': o << "\\\\"; break;
            case '\n': o << "\\n"; break;
            case '\r': o << "\\r"; break;
            case '\t': o << "\\t"; break;
            case '\b': o << "\\b"; break;
            case '\f': o << "\\f"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", (unsigned)c);
                    o << buf;
                } else {
                    o << c;
                }
        }
    }
    return o.str();
}

namespace {

void skipWs(const std::string& s, size_t& i) {
    while (i < s.size() && std::isspace(static_cast<unsigned char>(s[i]))) ++i;
}

bool parseString(const std::string& s, size_t& i, std::string& out) {
    if (i >= s.size() || s[i] != '"') return false;
    ++i;
    out.clear();
    while (i < s.size()) {
        char c = s[i];
        if (c == '"') {
            ++i;
            return true;
        }
        if (c == '\\') {
            ++i;
            if (i >= s.size()) return false;
            char e = s[i];
            switch (e) {
                case '"': out += '"'; break;
                case '\\': out += '\\'; break;
                case '/': out += '/'; break;
                case 'n': out += '\n'; break;
                case 't': out += '\t'; break;
                case 'r': out += '\r'; break;
                case 'b': out += '\b'; break;
                case 'f': out += '\f'; break;
                case 'u': {
                    if (i + 4 >= s.size()) return false;
                    unsigned v = 0;
                    for (int k = 0; k < 4; ++k) {
                        char h = s[i + 1 + k];
                        v <<= 4;
                        if (h >= '0' && h <= '9') v |= static_cast<unsigned>(h - '0');
                        else if (h >= 'a' && h <= 'f') v |= static_cast<unsigned>(h - 'a' + 10);
                        else if (h >= 'A' && h <= 'F') v |= static_cast<unsigned>(h - 'A' + 10);
                        else return false;
                    }
                    i += 4;
                    out += static_cast<char>(v & 0xFF);
                    break;
                }
                default: return false;
            }
            ++i;
        } else {
            out += c;
            ++i;
        }
    }
    return false;
}

JValue parseValue(const std::string& s, size_t& i, bool& ok) {
    skipWs(s, i);
    JValue v;
    if (i >= s.size()) {
        ok = false;
        return v;
    }
    char c = s[i];
    if (c == '{') {
        v.type = JValue::OBJ;
        ++i;
        skipWs(s, i);
        if (i < s.size() && s[i] == '}') {
            ++i;
            return v;
        }
        while (true) {
            skipWs(s, i);
            std::string key;
            if (!parseString(s, i, key)) {
                ok = false;
                return v;
            }
            skipWs(s, i);
            if (i >= s.size() || s[i] != ':') {
                ok = false;
                return v;
            }
            ++i;
            JValue val = parseValue(s, i, ok);
            if (!ok) return v;
            v.obj.emplace_back(key, val);
            skipWs(s, i);
            if (i < s.size() && s[i] == ',') {
                ++i;
                continue;
            }
            if (i < s.size() && s[i] == '}') {
                ++i;
                return v;
            }
            ok = false;
            return v;
        }
    }
    if (c == '[') {
        v.type = JValue::ARR;
        ++i;
        skipWs(s, i);
        if (i < s.size() && s[i] == ']') {
            ++i;
            return v;
        }
        while (true) {
            JValue val = parseValue(s, i, ok);
            if (!ok) return v;
            v.arr.push_back(val);
            skipWs(s, i);
            if (i < s.size() && s[i] == ',') {
                ++i;
                continue;
            }
            if (i < s.size() && s[i] == ']') {
                ++i;
                return v;
            }
            ok = false;
            return v;
        }
    }
    if (c == '"') {
        std::string str;
        if (!parseString(s, i, str)) {
            ok = false;
            return v;
        }
        v.type = JValue::STR;
        v.str = str;
        return v;
    }
    if (s.compare(i, 4, "true") == 0) {
        v.type = JValue::BOOL;
        v.b = true;
        i += 4;
        return v;
    }
    if (s.compare(i, 5, "false") == 0) {
        v.type = JValue::BOOL;
        v.b = false;
        i += 5;
        return v;
    }
    if (s.compare(i, 4, "null") == 0) {
        v.type = JValue::NUL;
        i += 4;
        return v;
    }
    // number
    size_t start = i;
    bool any = false;
    if (i < s.size() && (s[i] == '-' || s[i] == '+')) {
        ++i;
    }
    while (i < s.size() && (std::isdigit(static_cast<unsigned char>(s[i])) || s[i] == '.' ||
                            s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) {
        ++i;
        any = true;
    }
    // avoid consuming trailing '+'/'-' after digits incorrectly (rare in our data)
    while (i < s.size() && (s[i] == ',' || s[i] == '}' || s[i] == ']' || s[i] == ' ')) break;
    if (!any) {
        ok = false;
        return v;
    }
    v.type = JValue::NUM;
    v.num = std::strtod(s.c_str() + start, nullptr);
    return v;
}

}  // namespace

JValue JValue::parse(const std::string& text, bool* ok) {
    size_t i = 0;
    bool good = true;
    JValue v = parseValue(text, i, good);
    skipWs(text, i);
    if (i != text.size()) good = false;
    if (ok) *ok = good;
    return v;
}

const JValue* JValue::find(const std::string& key) const {
    for (const auto& kv : obj) {
        if (kv.first == key) return &kv.second;
    }
    return nullptr;
}

bool JValue::containsNumber(const std::string& key) const {
    const JValue* v = find(key);
    return v != nullptr && v->type == JValue::NUM;
}

double JValue::numOf(const std::string& key, double def) const {
    const JValue* v = find(key);
    return (v && v->type == JValue::NUM) ? v->num : def;
}

bool JValue::boolOf(const std::string& key, bool def) const {
    const JValue* v = find(key);
    return (v && v->type == JValue::BOOL) ? v->b : def;
}

std::string JValue::strOf(const std::string& key, const std::string& def) const {
    const JValue* v = find(key);
    return (v && v->type == JValue::STR) ? v->str : def;
}

std::string JValue::stringify() const {
    std::ostringstream o;
    switch (type) {
        case NUL: o << "null"; break;
        case BOOL: o << (b ? "true" : "false"); break;
        case NUM: o << num; break;
        case STR: o << '"' << jsonEscape(str) << '"'; break;
        case ARR: {
            o << '[';
            for (size_t i = 0; i < arr.size(); ++i) {
                if (i) o << ',';
                o << arr[i].stringify();
            }
            o << ']';
            break;
        }
        case OBJ: {
            o << '{';
            for (size_t i = 0; i < obj.size(); ++i) {
                if (i) o << ',';
                o << '"' << jsonEscape(obj[i].first) << "\":" << obj[i].second.stringify();
            }
            o << '}';
            break;
        }
    }
    return o.str();
}

}  // namespace phonk
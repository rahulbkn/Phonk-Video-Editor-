/*
 * Optional native crash signal handling (clearly separated from the Java/Kotlin
 * CrashHandler). Registers minimal async-signal-safe handlers for the classic
 * "hard" signals (SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE) which a JVM
 * Thread.UncaughtExceptionHandler can never see.
 *
 * The handler ONLY writes a tiny marker file (open/write/close, no allocation,
 * no Android APIs) and then delegates to the previously installed handler so
 * the OS terminates the process exactly as it would without us. On the next app
 * launch, CrashLogRepository.importNativePendingMarker() turns the marker into
 * a normal crash log entry.
 */

#include <jni.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <time.h>

namespace {

static char s_marker[384];
static volatile sig_atomic_t s_armed = 0;
static struct sigaction s_prev[6];

enum class SigIndex { SEGV = 0, ABRT, BUS, ILL, FPE };

static int sig_to_index(int sig) {
    switch (sig) {
        case SIGSEGV: return static_cast<int>(SigIndex::SEGV);
        case SIGABRT: return static_cast<int>(SigIndex::ABRT);
        case SIGBUS:  return static_cast<int>(SigIndex::BUS);
        case SIGILL:  return static_cast<int>(SigIndex::ILL);
        case SIGFPE:  return static_cast<int>(SigIndex::FPE);
        default:      return -1;
    }
}

static const char* sig_name(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGBUS:  return "SIGBUS";
        case SIGILL:  return "SIGILL";
        case SIGFPE:  return "SIGFPE";
        default:      return "SIG(?)";
    }
}

static void write_sto(int fd, const char* s) {
    if (s == nullptr) return;
    size_t n = strlen(s);
    while (n > 0) {
        ssize_t w = write(fd, s, n);
        if (w <= 0) return;
        s += w;
        n -= static_cast<size_t>(w);
    }
}

/* Hex formatting with a fixed stack buffer; no allocations, no locale. */
static void write_hex(int fd, unsigned long long v) {
    char buf[3 + 16];
    buf[0] = '0';
    buf[1] = 'x';
    for (int i = 15; i >= 0; --i) {
        buf[2 + i] = "0123456789abcdef"[(v >> (4 * i)) & 0xF];
    }
    buf[18] = 0;
    write_sto(fd, buf);
}

static void write_dec(int fd, long long value) {
    char buf[24];
    bool neg = value < 0;
    unsigned long long v = neg ? (unsigned long long)(-(value + 1)) + 1ULL
                               : (unsigned long long)value;
    int n = 0;
    if (v == 0) buf[n++] = '0';
    while (v > 0) {
        buf[n++] = (char)('0' + (v % 10));
        v /= 10;
    }
    if (neg) buf[n++] = '-';
    // buf holds digits reversed; write them back-to-front.
    while (n > 0) {
        char c = buf[--n];
        c = (char)(c == '\0' ? '0' : c);
        if (write(fd, &c, 1) != 1) return;
    }
}

static void crash_wrapper(int sig, siginfo_t* info, void* uc) {
    int idx = sig_to_index(sig);

    if (s_armed && idx >= 0 && s_marker[0] != '\0') {
        int fd = open(s_marker, O_WRONLY | O_CREAT | O_APPEND, 0640);
        if (fd >= 0) {
            write_sto(fd, "sig=");
            write_sto(fd, sig_name(sig));
            write_sto(fd, "\npid=");
            write_dec(fd, (long long)getpid());
            write_sto(fd, "\n");
            if (info && (sig == SIGSEGV || sig == SIGBUS)) {
                write_sto(fd, "addr=");
                write_hex(fd, reinterpret_cast<unsigned long long>(info->si_addr));
                write_sto(fd, "\n");
            }
            write_sto(fd, "code=");
            write_dec(fd, info ? (long long)info->si_code : 0);
            write_sto(fd, "\n");
            close(fd);
        }
    }

    /* Chain to the previously installed handler, or restore default and
     * re-raise, so the OS terminates the process for the same signal. */
    if (idx >= 0) {
        struct sigaction* prev = &s_prev[idx];
        if (prev->sa_handler == SIG_DFL || prev->sa_handler == SIG_IGN) {
            sigaction(sig, prev, nullptr);
            raise(sig);
        } else if (prev->sa_sigaction == nullptr) {
            prev->sa_handler(sig);
        } else {
            prev->sa_sigaction(sig, info, uc);
        }
    }
    /* Defensive: never return from a crash handler. */
    _exit(128 + sig);
}

static jboolean native_install_crash_handler(JNIEnv* env, jstring marker) {
    if (marker == nullptr) return JNI_FALSE;
    const char* c = env->GetStringUTFChars(marker, nullptr);
    if (!c) return JNI_FALSE;
    strncpy(s_marker, c, sizeof(s_marker) - 1);
    s_marker[sizeof(s_marker) - 1] = '\0';
    env->ReleaseStringUTFChars(marker, c);

    static const int sigs[] = { SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE };
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_wrapper;
    sa.sa_flags = SA_SIGINFO | SA_RESETHAND;
    sigemptyset(&sa.sa_mask);

    int installed = 0;
    for (size_t i = 0; i < sizeof(sigs) / sizeof(sigs[0]); ++i) {
        int idx = sig_to_index(sigs[i]);
        if (idx < 0) continue;
        if (sigaction(sigs[i], &sa, &s_prev[idx]) == 0) installed++;
    }
    s_armed = 1;
    return installed > 0 ? JNI_TRUE : JNI_FALSE;
}

}  // anonymous namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_phonk_editor_crash_CrashNativeHandler_nativeInstallCrashHandler(
    JNIEnv* env, jobject, jstring marker) {
    return native_install_crash_handler(env, marker);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_phonk_editor_crash_CrashNativeHandler_nativeIsCrashHandlerInstalled(
    JNIEnv*, jobject) {
    return s_armed ? JNI_TRUE : JNI_FALSE;
}
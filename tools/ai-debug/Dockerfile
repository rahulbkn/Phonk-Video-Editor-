# Render/Railway background worker image — runs the ai-debug worker
# (webhook receiver + GitHub polling) and drives OpenCode locally.
#
# Heavy image (Android SDK + NDK). First build takes several minutes; layer
# caching makes subsequent builds fast.

FROM eclipse-temurin:17-jdk-jammy

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    GRADLE_USER_HOME=/opt/gradle-cache \
    PATH="/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/root/.local/bin:${PATH}"

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl unzip wget python3 python3-pip python3-venv ffmpeg jq openssl \
    ca-certificates gnupg build-essential \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
      -o /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
      > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update && apt-get install -y gh && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    curl -fsSL -o /tmp/cmdline-tools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true && \
    sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" \
      "ndk;27.1.12297006" "cmake;4.4.2" >/dev/null

WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt

COPY ai_debug/ /app/ai_debug/
COPY config/ /app/config/
COPY prompts/ /app/prompts/

EXPOSE 8080

CMD ["python3", "-m", "ai_debug", "webhook", "--host", "0.0.0.0", "--port", "8080"]

# Vortex TV: native Android build on Render (no GitHub Actions).
# The build stage installs Android SDK and Gradle; the runtime only serves the resulting APK.
FROM eclipse-temurin:17-jdk-jammy AS android-build

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_VERSION=8.9

RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl unzip python3 && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    curl -fL --retry 4 --connect-timeout 30 \
      https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip \
      -o /tmp/android-tools.zip; \
    echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  /tmp/android-tools.zip" | sha256sum -c -; \
    unzip -q /tmp/android-tools.zip -d /tmp/android-install; \
    mkdir -p "$ANDROID_HOME/cmdline-tools"; \
    mv /tmp/android-install/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"; \
    rm /tmp/android-tools.zip

RUN set -eux; \
    (yes || true) | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/tmp/licenses.log; \
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
      "platforms;android-35" "build-tools;35.0.0" "platform-tools"

RUN set -eux; \
    curl -fL --retry 4 --connect-timeout 30 \
      "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
      -o /tmp/gradle.zip; \
    unzip -q /tmp/gradle.zip -d /opt; \
    rm /tmp/gradle.zip
ENV PATH="/opt/gradle-8.9/bin:${PATH}"

WORKDIR /project
COPY . .
RUN python3 -m unittest discover -s tests -p 'test_*.py'
RUN gradle --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" :app:assembleDebug
RUN python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk

FROM python:3.12-alpine
WORKDIR /srv
COPY --from=android-build /project/app/build/outputs/apk/debug/app-debug.apk /srv/VortexTV-Android-Debug.apk
RUN python3 -c "import hashlib,pathlib; p=pathlib.Path('/srv/VortexTV-Android-Debug.apk'); pathlib.Path('/srv/SHA256.txt').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')"
EXPOSE 10000
CMD ["sh", "-c", "python3 -m http.server ${PORT:-10000} --bind 0.0.0.0 --directory /srv"]

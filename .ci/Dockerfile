FROM gradle:8.2-jdk17

USER root

ENV CLI_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
    ANDROID_HOME="/root/Android/Sdk" \
    ANDROID_SDK_ROOT="/root/Android/Sdk" \
    ANDROID_VERSION=34 \
    ANDROID_BUILD_TOOLS_VERSION=33.0.0

RUN mkdir -p "$ANDROID_HOME" .android \
    && cd "$ANDROID_HOME" \
    && curl -o cli.zip $CLI_URL \
    && unzip cli.zip \
    && rm cli.zip \
    && mv cmdline-tools latest \
    && mkdir cmdline-tools \
    && mv latest cmdline-tools

ENV PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"

RUN yes | sdkmanager --licenses
RUN sdkmanager --update
RUN sdkmanager --install "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"
RUN sdkmanager --install "platforms;android-${ANDROID_VERSION}"
RUN sdkmanager --install "platform-tools"
RUN sdkmanager --install "ndk;26.1.10909125"

RUN apt update && apt install -y build-essential file apt-utils awscli

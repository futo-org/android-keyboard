FROM gradle:8.14.3-jdk21

USER root

ENV CLI_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip" \
    ANDROID_HOME="/root/Android/Sdk" \
    ANDROID_SDK_ROOT="/root/Android/Sdk" \
    ANDROID_VERSION=35 \
    ANDROID_BUILD_TOOLS_VERSION=35.0.0

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
RUN sdkmanager --install "ndk;28.2.13676358"

RUN apt update && apt install -y build-essential file apt-utils python3-requests

RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64-2.27.55.zip" -o "/tmp/awscliv2.zip" \
        && (echo "06c43658b5fd71a729455de9a1c09b011d565036513f5c7096af699f4eec755a  /tmp/awscliv2.zip" | sha256sum -c -) \
        && unzip /tmp/awscliv2.zip -d /tmp \
        && /tmp/aws/install \
        && rm -rf /tmp/aws
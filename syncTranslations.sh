#!/bin/sh
set -e

# Set up SSH
eval $(ssh-agent -s)
mkdir -p ~/.ssh
echo "gitlab.futo.org ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDGMPTo1yxEh6ZhavmqS0w2DMLAus1dKn0iT/mOTNHr5Vbzy/Bc4lKU5ciJ/IfkG8CHgzMLVu9HehwMpk5n+Jl6o0c7DmBg3En5tiDWgQ027AxHer84+dZQ5+IqUXp/xZksHyGY2RNUgVs311gHVhErAwtYjVsHIXXSVU0d4mY1b5Y7GHor8jj7OCgRIIyu7bDMBaH7a/QD6wbwFjWkoimN+dnOFphmekKGbdzb69io2nWUz7BqbpNoiFqaotqQgJj7C7ELtGWodAioBEB2WF2zVm9rmdAiXsY+NkUEu97EaoRCxzovgGIF8QtEGYrbEsze9M3W/nfJsMFTUxoCIzjJBsRnJYPprVWh9Y2fml8Vl7ajxFN8v3As7wGr8e2iHiVFfuqbAtYOpUt8HGzaomNxiXUHfnW6Zw6wrUZ6GbWgZOi3uRvJ8sz+dAVhIJ4zwecD/sSH9NRgReq26HRJFxj7PLeStJzTFz4WGMOHxFI6BRAn+A49/bnqVGMg+ZO8iOc=" > ~/.ssh/known_hosts
echo "gitlab.futo.org ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBA0shIgFt/aBHHT+VLw/nj0l4m4hx+9zf6p5ArK7+U87rVuk/4bA43OVzUsIHITQhp/FeRScw1C/R0K4KVov5Mk=" >> ~/.ssh/known_hosts
echo "gitlab.futo.org ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOC19F4dx6RhNQdOet2j7ip9LVTLjBBiD4HRFoKzZg4x" >> ~/.ssh/known_hosts
echo "$CI_SSH_PRIVATE_KEY_B64" | base64 --decode | ssh-add -

# Set up repo
git clone git@gitlab.futo.org:keyboard/keyboard-translations.git translations-repo

# For each stringset, rm existing files and copy new ones
rm translations-repo/core/values/strings*
for f in $(find java/res/values/ -name "strings*.xml"); do
  cp "$f" translations-repo/core/values/"$(basename "$f")";
done

rm translations-repo/devbuild/values/strings*
for f in $(find java/unstable/res/values/ -name "strings*.xml"); do
  cp "$f" translations-repo/devbuild/values/"$(basename "$f")";
done

rm translations-repo/voiceinput/values/strings*
for f in $(find voiceinput-shared/src/main/res/values/ -name "strings*.xml"); do
  cp "$f" translations-repo/voiceinput/values/"$(basename "$f")";
done

  # Ignored strings (hidden in Pontoon for now)
EXCLUDED_NAMES=$(find translations-repo/core-ign/values/ -name "strings*.xml")

rm translations-repo/core-ign/values/strings*

for f in $EXCLUDED_NAMES; do
  mv translations-repo/core/values/"$(basename "$f")" translations-repo/core-ign/values ||:;
done

# Commit and push
cd translations-repo
git config user.name "GitLab CI"
git config user.email "alex+ci@futo.org"
git add .
git commit -m "Update strings.xml from main repo on commit $(date +%Y-%m-%d:%H:%M:%S)" || echo "No changes to commit"
git push origin master
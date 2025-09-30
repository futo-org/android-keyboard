#!/bin/sh
aws configure set aws_access_key_id $KEYBOARD_R2_ACCESS_KEY_ID
aws configure set aws_secret_access_key $KEYBOARD_R2_SECRET_ACCESS_KEY
aws configure set region $KEYBOARD_R2_DEFAULT_REGION

aws s3 cp "$1" s3://$KEYBOARD_R2_BUCKET_NAME/nightly-$4.apk --endpoint=$KEYBOARD_R2_ENDPOINT_URL

echo "$2" > nightly_version_$4
echo "https://dl.keyboard.futo.org/nightly-$4.apk?v=$2" >> nightly_version_$4
echo "$3" >> nightly_version_$4

aws s3 cp nightly_version_$4 s3://$KEYBOARD_R2_BUCKET_NAME/nightly_version_$4 --endpoint=$KEYBOARD_R2_ENDPOINT_URL
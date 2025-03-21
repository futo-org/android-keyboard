#!/bin/sh
aws configure set aws_access_key_id $KEYBOARD_R2_ACCESS_KEY_ID
aws configure set aws_secret_access_key $KEYBOARD_R2_SECRET_ACCESS_KEY
aws configure set region $KEYBOARD_R2_DEFAULT_REGION

aws s3 cp "$1" s3://$KEYBOARD_R2_BUCKET_NAME/nightly.apk --endpoint=$KEYBOARD_R2_ENDPOINT_URL

echo "$2" > nightly_version
echo "https://dl.keyboard.futo.org/nightly.apk?v=$2" >> nightly_version
echo "$3" >> nightly_version

aws s3 cp nightly_version s3://$KEYBOARD_R2_BUCKET_NAME/nightly_version --endpoint=$KEYBOARD_R2_ENDPOINT_URL
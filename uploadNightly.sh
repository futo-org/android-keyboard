#!/bin/sh
aws configure set aws_access_key_id $KEYBOARD_R2_ACCESS_KEY_ID
aws configure set aws_secret_access_key $KEYBOARD_R2_SECRET_ACCESS_KEY
aws configure set region $KEYBOARD_R2_DEFAULT_REGION
aws s3 cp ./keyboard*.apk s3://$KEYBOARD_R2_BUCKET_NAME/nightly.apk --endpoint=$KEYBOARD_R2_ENDPOINT_URL
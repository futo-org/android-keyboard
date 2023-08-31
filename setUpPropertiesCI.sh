#!/bin/sh
echo ${KEYSTORE_FILE?Need keystore file} | base64 --decode > key.jks
echo storePassword=${KEYSTORE_PASSWORD?Need keystore password} > keystore.properties
echo keyPassword=${KEY_PASSWORD?Need key password} >> keystore.properties
echo keyAlias=${KEYSTORE_ALIAS?Need key alias} >> keystore.properties
echo storeFile=key.jks >> keystore.properties

echo ${CRASHREPORTING_FILE?Need crash reporting file} | base64 --decode > crashreporting.properties
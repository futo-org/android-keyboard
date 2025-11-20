import sys
import os
import shutil
from glob import glob
import requests
import hashlib
import tempfile
import shutil
import gzip

SOURCES = [
    {
        "url": "https://dl.keyboard.futo.org/jisho-v25.10-non-gpl.dict",
        "sha256": "72868f27299cc91924425bab1f58ecd67a957b01bae7507c9bdb4687ae150627",
        "locale": "ja",
        "file": "builtin_mozc_data.dict"
    }
]

def download_file(url, dest_path):
    headers = {}

    response = requests.get(url, headers=headers, stream=True)
    response.raise_for_status()
    with open(dest_path, "wb") as f:
        for chunk in response.iter_content(chunk_size=8192):
            f.write(chunk)

def verify_sha256(filepath, expected_checksum):
    sha256 = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            sha256.update(chunk)
    return sha256.hexdigest() == expected_checksum


if __name__ == "__main__":
    root_dir = sys.argv[1]

    for entry in SOURCES:
        raw_default_dir = root_dir + "/raw"
        raw_locale_dir = root_dir + "/raw-" + entry["locale"]

        os.makedirs(raw_default_dir, exist_ok=True)
        os.makedirs(raw_locale_dir, exist_ok=True)

        filename = entry["file"]

        fakefile = raw_default_dir + "/" + filename
        truefile = raw_locale_dir  + "/" + filename

        if os.path.isfile(truefile) and os.path.isfile(fakefile):
            continue

        url = entry["url"]
        checksum = entry["sha256"].lower()

        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = temp_dir + "/" + filename

            print(f"Downloading {url}")
            download_file(url, temp_path)

            if not verify_sha256(temp_path, checksum):
                raise Exception(f"{temp_path} does not match sha256 {checksum}")

            print(f"Copying {filename}...")
            shutil.copy2(temp_path, truefile)
        
        # touch fallback file so resource doesn't get removed for having no fallback
        open(fakefile, "a").close()
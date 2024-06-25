import os
import json
import xml.etree.ElementTree as ET

# Base directory containing the XML files
base_dir = '../make-keyboard-text/res'

# List of known prefixes
prefixes = ['morekeys_', 'keyspec_', 'label_', 'keylabel_', 'keyhintlabel_', 'additional_morekeys_']

# Function to convert XML to a dictionary
def xml_to_dict(xml_file):
    tree = ET.parse(xml_file)
    root = tree.getroot()

    locale_data = {prefix.rstrip('_'): {} for prefix in prefixes}
    locale_data['other'] = {}

    for string in root.findall('string'):
        name = string.get('name')
        value = string.text or ""

        value = value.replace("\\\\", "\\")
        if value.startswith('"') and value.endswith('"'):
             value = value.lstrip('"').rstrip('"')

        # Categorize based on prefixes
        categorized = False
        for prefix in prefixes:
            if name.startswith(prefix):
                if "morekeys" in prefix:
                     locale_data[prefix.rstrip('_')][name.replace(prefix, '')] = list(filter(lambda x: x, value.split(",")))
                else:
                     locale_data[prefix.rstrip('_')][name.replace(prefix, '')] = value
                categorized = True
                break

        # If no known prefix matches, categorize as "other"
        if not categorized:
            locale_data['other'][name] = value

    return locale_data

# Function to process all XML files and convert them to JSON
def process_locale_files(base_dir):
    for root, _, files in os.walk(base_dir):
        for file in files:
            if file.endswith('.xml'):
                xml_file_path = os.path.join(root, file)
                locale_code = root.split(os.sep)[-1].replace('values-', '')
                if locale_code == "values":
                    locale_code = "DEFAULT"

                if "-r" in locale_code:
                    locale_code = locale_code.replace("-r", "_")

                json_data = {"locale": locale_code} | xml_to_dict(xml_file_path)

                json_file_path = (f"locales/{locale_code}.json")
                with open(json_file_path, 'w', encoding='utf-8') as json_file:
                    json.dump(json_data, json_file, ensure_ascii=False, indent=4)

                print(f"Converted {xml_file_path} to {json_file_path}")

# Run the process
process_locale_files(base_dir)

# Keyboard Layout definitions

This repo contains the keyboard layout definitions for FUTO Keyboard.

See the [Layout Spec](LayoutSpec.md) for information on the YAML format.

## Custom Layouts

In FUTO Keyboard nightly, you can define custom layouts within the app for testing and development.

To activate developer settings, go to the "Help & Feedback" section and tap the version code a bunch
of times, this will activate the "Developer Options" section in the settings home menu.

Visit "Developer Settings" > "Custom layouts" to create custom layouts. They will be activated
in the layout switcher by default once saved.

If you're planning to contribute this layout, please make sure to test it with different settings
enabled to make sure it behaves as expected:
* Split keyboard in landscape
* Number row enabled / disabled
* Arrow keys enabled / disabled
* Long-press key settings in various orders
* (if you edited the bottom row) Action key and contextual key

## Contributing via Pull Request

After testing and verifying your layout works and is accurate, you can open a pull request to
contribute the layout.

The app will search for `*.yml` and `*.yaml` files here recursively. The directories not namespaced,
so please ensure there are no two files with the same name (i.e. place Lithuanian QWERTY in
`Lithuanian/lithuanian-qwerty.yaml`, not `Lithuanian/qwerty.yaml`).

Please also update [mapping.yaml](mapping.yaml) and insert your layout name under the relevant
languages.

If you're having issues with opening a pull request, you can also just open an issue and include
your YAML and request it to be added.

## Contribution Standards

Obscure layouts can serve to confuse users and bloat the layout list, so we are likely going to have
some standards for inclusion of layouts.

## Layout Limitations

If you're unable to express a layout under the current system, let us know by opening an issue.
Some limitations currently exist due to the non-triviality of implementing the necessary features
(e.g. Japanese flick keys are unsupported).
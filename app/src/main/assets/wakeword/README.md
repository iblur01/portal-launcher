openWakeWord models (Apache-2.0), from https://github.com/dscripka/openWakeWord releases.

`melspectrogram.onnx` and `embedding_model.onnx` sit at the assets root because the
openwakeword-android library resolves them by hardcoded name; only the wake-word classifiers
live here and are selected through `Prefs.voiceAssistantWakeWord`.

Adding a wake word: train it with the openWakeWord notebook, export ONNX, drop the file in this
directory, and add it to the picker in VoiceAssistantSettingsPage.

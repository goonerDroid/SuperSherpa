# Sherpa model assets

Place the downloaded Sherpa ONNX model files in:

`app/src/main/assets/sherpa-onnx-streaming-zipformer-en-2023-06-26/`

Expected files:

- `encoder-epoch-99-avg-1-chunk-16-left-128.onnx`
- `decoder-epoch-99-avg-1-chunk-16-left-128.onnx`
- `joiner-epoch-99-avg-1-chunk-16-left-128.onnx`
- `tokens.txt`

These assets are loaded directly by `SherpaEngine`.

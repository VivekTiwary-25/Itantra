# Vendored code used by the frontend experiment

`vendor/indic_numtowords/` is AI4Bharat indic-numtowords version 1.1.0, copied
from the existing experiment's pinned install. Original project:
https://github.com/AI4Bharat/indic-numtowords.

The original MIT license is retained at
`vendor/indic_numtowords-1.1.0.dist-info/licenses/LICENSE`; package metadata is
retained at `vendor/indic_numtowords-METADATA.txt`. Bytecode/cache files were
excluded. The library's license does not grant permissions for the separate
Indic-TTS model checkpoints or establish pronunciation quality.

The wrapper adds stricter vocabulary validation and narrowly scoped handling
around library output. Its source is `indic_frontend_repair.py`; details and
remaining limitations are in the frontend-repair report.

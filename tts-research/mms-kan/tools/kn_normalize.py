#!/usr/bin/env python3
"""Python mirror of KannadaTextNormalizer.kt, used only to produce desktop listening evidence
with the exact text the app would synthesize (no JVM needed for a quick WAV pack). The Kotlin
file is authoritative; this is kept in lockstep with it manually and must not drift."""
import json
import re
import unicodedata
from pathlib import Path

_TABLE = json.loads(
    Path(__file__).with_name("..").joinpath("kan_units0_999.json").resolve().read_text(encoding="utf-8")
)
UNITS = _TABLE[:100]
UNITS[83] = "ಎಂಬತ್ತ್ಮೂರು"
HUNDREDS = [""] + [_TABLE[h * 100] for h in range(1, 10)]
SCALES = [(8, "ಕೋಟಿ"), (6, "ಲಕ್ಷ"), (4, "ಸಾವಿರ")]
MAX_CARDINAL_DIGITS = 9
DIGIT_RUN = re.compile(r"[0-9೦-೯]+")


def to_ascii_digits(run: str) -> str:
    return "".join(str(ord(c) - ord("೦")) if "೦" <= c <= "೯" else c for c in run)


def verbalize(digits: str) -> str:
    if len(digits) > MAX_CARDINAL_DIGITS or (len(digits) > 1 and digits[0] == "0"):
        return " ".join(UNITS[int(d)] for d in digits)
    rest = digits.lstrip("0")
    if rest == "":
        return UNITS[0]
    words = []
    for minlen, scale in SCALES:
        if len(rest) >= minlen:
            head = rest[: len(rest) - (minlen - 1)]
            words.append(UNITS[int(head)])
            words.append(scale)
            rest = rest[len(head):].lstrip("0")
    if len(rest) == 3:
        words.append(HUNDREDS[int(rest[0])])
        rest = rest[1:].lstrip("0")
    if rest:
        words.append(UNITS[int(rest)])
    return " ".join(words)


def normalize(text: str) -> str:
    expanded = DIGIT_RUN.sub(lambda m: verbalize(to_ascii_digits(m.group(0))), text)
    return unicodedata.normalize("NFC", expanded)


if __name__ == "__main__":
    import sys

    for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
        if line.strip():
            print(normalize(line))

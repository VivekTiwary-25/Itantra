"""Strict, auditable text frontend for one Indic-TTS checkpoint.

The published Coqui tokenizer silently discards out-of-vocabulary characters.
This wrapper normalizes supported numbers and canonical Odia spellings, then
rejects any remaining OOV character before acoustic inference.
"""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "vendor"))
from indic_numtowords import num2words  # AI4Bharat 1.1.0, MIT


_DIGITS = {
    "ta": "௦௧௨௩௪௫௬௭௮௯",
    "te": "౦౧౨౩౪౫౬౭౮౯",
    "or": "୦୧୨୩୪୫୬୭୮୯",
}


class UnsupportedText(ValueError):
    """Text cannot be sent to this checkpoint without losing information."""


def _number_pattern(language_code: str) -> re.Pattern[str]:
    return re.compile("[0-9" + _DIGITS[language_code] + "]+")


def repair_text(text: str, language_code: str, tokenizer) -> tuple[str, list[str]]:
    if language_code not in _DIGITS:
        raise UnsupportedText(f"unsupported language code: {language_code}")
    warnings: list[str] = []
    output = text

    if language_code == "or":
        # Unicode-canonical equivalents of the precomposed letters in this
        # checkpoint's fixed vocabulary. NFC does not compose these two pairs.
        output = output.replace("ଡ଼", "ଡ଼").replace("ଢ଼", "ଢ଼")

    if language_code == "te" and "\u200c" in output:
        for match in re.finditer("\u200c", output):
            index = match.start()
            if not (
                index > 0
                and index + 1 < len(output)
                and output[index - 1] == "్"
                and "\u0c15" <= output[index + 1] <= "\u0c39"
            ):
                raise UnsupportedText(f"U+200C outside Telugu virama-consonant context at {index}")
        output = output.replace("\u200c", "")
        warnings.append("U+200C shaping control removed after Telugu virama; original written form retained in trace")

    def replace_number(match: re.Match[str]) -> str:
        number_text = match.group()
        if len(number_text) > 1 and unicodedata.decimal(number_text[0]) == 0:
            raise UnsupportedText(f"ambiguous leading-zero number: {number_text!r}")
        digits = "".join(str(unicodedata.decimal(char)) for char in number_text)
        try:
            words = num2words(int(digits), lang=language_code)
        except Exception as exc:
            raise UnsupportedText(f"cannot verbalize number {number_text!r}: {exc}") from exc
        if not isinstance(words, str) or not words.strip():
            raise UnsupportedText(f"number verbalization was empty: {number_text!r}")
        return words

    output = _number_pattern(language_code).sub(replace_number, output)
    cleaned = tokenizer.text_cleaner(output) if tokenizer.text_cleaner else output
    unsupported = [(char, f"U+{ord(char):04X}") for char in dict.fromkeys(cleaned)
                   if not _is_known(char, tokenizer)]
    if unsupported:
        raise UnsupportedText(f"checkpoint vocabulary lacks {unsupported!r} after frontend: {cleaned!r}")
    return output, warnings


def _is_known(char: str, tokenizer) -> bool:
    try:
        tokenizer.characters.char_to_id(char)
        return True
    except KeyError:
        return False


def trace_text(text: str, language_code: str, tokenizer) -> dict:
    original_cleaned = tokenizer.text_cleaner(text) if tokenizer.text_cleaner else text
    original_ids = [tokenizer.characters.char_to_id(char) for char in original_cleaned
                    if _is_known(char, tokenizer)]
    original_dropped = [f"U+{ord(char):04X}" for char in original_cleaned
                        if not _is_known(char, tokenizer)]
    result = {
        "original_input": text,
        "original_codepoints": [f"U+{ord(char):04X}" for char in text],
        "original_normalized": original_cleaned,
        "original_frontend_output": tokenizer.ids_to_text(original_ids),
        "original_token_ids": original_ids,
        "original_dropped_codepoints": original_dropped,
    }
    try:
        repaired, warnings = repair_text(text, language_code, tokenizer)
        repaired_cleaned = tokenizer.text_cleaner(repaired) if tokenizer.text_cleaner else repaired
        repaired_ids = tokenizer.text_to_ids(repaired)
        result.update({
            "repaired_input_to_model": repaired,
            "repaired_normalized": repaired_cleaned,
            "repaired_frontend_output": tokenizer.ids_to_text(repaired_ids),
            "repaired_token_ids": repaired_ids,
            "repaired_dropped_codepoints": [],
            "warnings": warnings,
            "error": None,
        })
    except UnsupportedText as exc:
        result.update({"error": str(exc), "warnings": []})
    return result

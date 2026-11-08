"""Regression checks against each checkpoint's actual fixed vocabulary."""

import argparse
import unittest

from TTS.config import load_config
from TTS.tts.utils.text.tokenizer import TTSTokenizer

from indic_frontend_repair import UnsupportedText, repair_text, trace_text


def make_tests(language_code, config_path):
    tokenizer, _ = TTSTokenizer.init_from_config(load_config(config_path))
    examples = {
        "ta": ("௧௨", "பன்னிரண்டு", "மருந்து"),
        "te": ("౧౨", "పన్నెండు", "మందులు"),
        "or": ("୧୨", "ବାର", "ଔଷଧ"),
    }
    native_digits, spoken_12, native_word = examples[language_code]

    class FrontendTests(unittest.TestCase):
        def test_native_and_ascii_number_reach_acoustic_ids(self):
            for digits in (native_digits, "12"):
                with self.subTest(digits=digits):
                    trace = trace_text(f"{native_word} {digits}", language_code, tokenizer)
                    self.assertIsNone(trace["error"])
                    self.assertIn(spoken_12, trace["repaired_frontend_output"])
                    self.assertEqual([], trace["repaired_dropped_codepoints"])
                    self.assertGreater(len(trace["repaired_token_ids"]), len(trace["original_token_ids"]))

        def test_mixed_script_letters_fail_instead_of_disappearing(self):
            with self.assertRaises(UnsupportedText):
                repair_text(f"{native_word} ABC", language_code, tokenizer)

        def test_leading_zero_is_not_reinterpreted(self):
            with self.assertRaises(UnsupportedText):
                repair_text(f"{native_word} 012", language_code, tokenizer)

        def test_existing_ten_inputs_have_no_repaired_oov(self):
            from pathlib import Path
            root = Path(__file__).resolve().parent.parent
            lines = (root / "tts-research" / "inputs" / f"{language_code}.txt").read_text(encoding="utf-8").splitlines()
            self.assertEqual(10, len(lines))
            for index, line in enumerate(lines, 1):
                with self.subTest(line=index):
                    self.assertIsNone(trace_text(line, language_code, tokenizer)["error"])

        def test_language_specific_handling(self):
            if language_code == "te":
                repaired, warnings = repair_text("\u0c26\u0c4d\u200c\u0c32", language_code, tokenizer)
                self.assertEqual("ద్ల", repaired)
                self.assertTrue(warnings)
                with self.assertRaises(UnsupportedText):
                    repair_text("\u0c2e\u200c", language_code, tokenizer)
            elif language_code == "or":
                decomposed, _ = repair_text("ଡ଼ ଢ଼", language_code, tokenizer)
                precomposed, _ = repair_text("ଡ଼ ଢ଼", language_code, tokenizer)
                self.assertEqual(precomposed, decomposed)
                self.assertEqual("ଡ଼ ଢ଼", decomposed)
                with self.assertRaises(UnsupportedText):
                    repair_text("କ଼", language_code, tokenizer)

    return FrontendTests


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--language-code", choices=("ta", "te", "or"), required=True)
    parser.add_argument("--config", required=True)
    args = parser.parse_args()
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(make_tests(args.language_code, args.config))
    raise SystemExit(not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful())

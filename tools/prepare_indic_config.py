"""Make a local speaker-file config without editing the official config."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, required=True)
    args = parser.parse_args()
    fastpitch = args.model_dir.resolve() / "fastpitch"
    source, output = fastpitch / "config.json", fastpitch / "config-local.json"
    speaker = fastpitch / "speakers.pth"
    if not speaker.is_file():
        parser.error(f"Missing {speaker}")
    data = json.loads(source.read_text(encoding="utf-8"))
    count = 0

    def replace(node):
        nonlocal count
        if isinstance(node, dict):
            for key, value in node.items():
                if key == "speakers_file":
                    node[key] = speaker.as_posix()
                    count += 1
                else:
                    replace(value)
        elif isinstance(node, list):
            for value in node:
                replace(value)

    replace(data)
    if count != 2:
        parser.error(f"Expected the two recorded speakers_file fields; found {count}. Inspect this config.")
    if output.exists():
        parser.error(f"{output} already exists; inspect it rather than overwrite it.")
    output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Created {output}; official config unchanged.")


if __name__ == "__main__":
    main()

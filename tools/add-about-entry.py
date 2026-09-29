#!/usr/bin/env python3
"""Append one About entry to tools/licenses.toml and print its catalog snippet."""
import argparse
import re
import sys
from pathlib import Path


def ask(prompt, default=""):
    hint = " [%s]" % default if default else ""
    value = input("%s%s: " % (prompt, hint)).strip()
    return value or default


def kt_escape(text):
    return text.replace("\\", "\\\\").replace('"', '\\"')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=".")
    ap.add_argument("--id", default=None)
    ap.add_argument("--spdx", default=None)
    ap.add_argument("--source", default=None)
    ap.add_argument("--text", action="append", default=[])
    ap.add_argument("--note", default=None)
    ap.add_argument("--override", default=None)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    repo = Path(args.repo)
    toml_path = repo / "tools/licenses.toml"
    existing = toml_path.read_text()

    dep_id = args.id or ask("Entry id (core.<name> or lib.<name>)")
    if not re.fullmatch(r"(core|lib)\.[a-z0-9_]+", dep_id):
        return "id must look like core.<name> or lib.<name>"
    if 'id = "%s"' % dep_id in existing:
        return "id %s already exists" % dep_id
    kind = "Core" if dep_id.startswith("core.") else "Library"

    spdx = args.spdx or ask("SPDX identifier")
    source = args.source
    if source is None:
        source = ask("Source dir, empty for none")
    if source and not (repo / source).is_dir():
        return "source dir not found: %s" % source
    texts = list(args.text)
    if not texts and not args.dry_run:
        while True:
            rel = ask("License text path, empty when done")
            if not rel:
                break
            texts.append(rel)
    for rel in texts:
        if not (repo / rel).is_file():
            return "license text not found: %s" % rel
    note = args.note
    if note is None and not texts and not args.dry_run:
        note = ask("Note (why no text ships)")
    override = args.override or (
        (ask("Pinned version, empty for git-derived") or None)
        if not args.dry_run
        else None
    )

    lines = ["", "[[dep]]", 'id = "%s"' % dep_id, 'spdx = "%s"' % spdx]
    if source:
        lines.append('source = "%s"' % source)
    if override:
        lines.append('override = "%s"' % override)
    if texts:
        lines.append("texts = [%s]" % ", ".join('"%s"' % t for t in texts))
    if note:
        lines.append('note = "%s"' % note)
    block = "\n".join(lines) + "\n"
    if args.dry_run:
        print(block, end="")
        return 0
    with open(toml_path, "a") as f:
        f.write(block)

    const = ask("DecoderNames const for the map line, empty to skip")
    name = "DecoderNames.%s" % const if const else '"%s"' % kt_escape(ask("Display name"))
    print(
        "\nAboutEntity(\n"
        '    id = "%s",\n'
        "    kind = AboutEntityKind.%s,\n"
        "    name = %s,\n"
        '    description = "%s",\n'
        '    author = "%s",\n'
        '    license = "%s",\n'
        ")," % (dep_id, kind, name, kt_escape(ask("Description")),
                kt_escape(ask("Author")), kt_escape(ask("License display string")))
    )
    if const:
        print('DecoderNames.%s to "%s",' % (const, dep_id))
    return 0


if __name__ == "__main__":
    sys.exit(main())

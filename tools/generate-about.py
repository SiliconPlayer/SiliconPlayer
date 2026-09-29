#!/usr/bin/env python3
"""Emit About artifacts from tools/licenses.toml and submodule SHAs."""
import argparse
import json
import re
import subprocess
import sys
import tomllib
from pathlib import Path

JAVA_PACKAGE = "com.flopster101.siliconplayer"
CHUNK = 30000


def git(args, cwd):
    try:
        out = subprocess.run(
            ["git"] + args, cwd=cwd, capture_output=True, text=True, check=False
        )
    except OSError:
        return None
    if out.returncode != 0:
        return None
    return out.stdout


def own_head(source):
    top = git(["rev-parse", "--show-toplevel"], source)
    if top is None or Path(top.strip()).resolve() != source.resolve():
        return None, None
    short = git(["rev-parse", "--short=8", "HEAD"], source)
    full = git(["rev-parse", "HEAD"], source)
    return (short.strip() if short else None, full.strip() if full else None)


def latest_tag(source, patterns):
    for pattern in patterns or ["*"]:
        listed = git(["tag", "--list", pattern, "--sort=-version:refname"], source)
        if listed is None:
            continue
        for line in listed.splitlines():
            if line.strip():
                return line.strip()
    return None


def java_escape(text):
    out = []
    for ch in text:
        o = ord(ch)
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif o < 0x20 or o == 0x7F:
            out.append("\\u%04x" % o)
        else:
            out.append(ch)
    return "".join(out)


def java_literal(text):
    parts = [
        '"%s"' % java_escape(text[i : i + CHUNK])
        for i in range(0, len(text), CHUNK)
    ]
    return " +\n            ".join(parts) if parts else '""'


def check_coverage(repo):
    catalog = (
        repo / "shared/src/main/kotlin/com/flopster101/siliconplayer/settings/AboutCatalog.kt"
    ).read_text()
    names_src = (
        repo / "shared/src/main/kotlin/com/flopster101/siliconplayer/DecoderNames.kt"
    ).read_text()
    const_vals = dict(re.findall(r'const val (\w+) = "([^"]+)"', names_src))
    mapped = {
        const_vals[c] for c in re.findall(r'DecoderNames\.(\w+) to "', catalog) if c in const_vals
    }
    aliases = {}
    for m in re.finditer(r'((?:"[^"]+",?\s*)+)-> DecoderNames\.(\w+)', names_src):
        for lit in re.findall(r'"([^"]+)"', m.group(1)):
            aliases[lit.lower()] = m.group(2)
    registered = set()
    for cpp in (repo / "app/src/main/cpp").rglob("*.cpp"):
        registered.update(
            re.findall(r'registerDecoder\("([^"]+)"', cpp.read_text(errors="replace"))
        )

    def canonical(name):
        if name in const_vals.values():
            return name
        hit = aliases.get(name.lower())
        return const_vals.get(hit) if hit else None

    unmapped = sorted(n for n in registered if canonical(n) not in mapped)
    covered = {canonical(n) for n in registered} - {None}
    stale = sorted(mapped - covered)
    for name in stale:
        print("mapped but never registered: %s" % name, file=sys.stderr)
    for name in unmapped:
        print("registered but unmapped: %s" % name)
    return 1 if unmapped else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True)
    ap.add_argument("--toml", default=None)
    ap.add_argument("--java-out", default=None)
    ap.add_argument("--meta-out", default=None)
    ap.add_argument("--check-coverage", action="store_true")
    ap.add_argument("--disable-tags-for", default="")
    ap.add_argument("--third-party", default=None)
    args = ap.parse_args()

    repo = Path(args.repo)
    if args.check_coverage:
        return check_coverage(repo)
    for flag in ("--toml", "--java-out", "--meta-out"):
        if getattr(args, flag.lstrip("-").replace("-", "_")) is None:
            ap.error("%s is required without --check-coverage" % flag)
    with open(args.toml, "rb") as f:
        config = tomllib.load(f)
    default_patterns = config.get("default_tag_patterns", ["*"])
    disabled = {s for s in args.disable_tags_for.split(",") if s}

    versions = {}
    texts = {}
    manifest = []
    for dep in config["dep"]:
        dep_id = dep["id"]
        source = repo / dep["source"] if dep.get("source") else None
        sha, full_sha = own_head(source) if source is not None else (None, None)
        version = dep.get("override")
        if version is None and sha is not None and dep_id not in disabled:
            tag = latest_tag(source, dep.get("tag_patterns", default_patterns))
            version = "%s-%s" % (tag, sha) if tag else sha
        elif version is None:
            version = sha
        if version is not None:
            versions[dep_id] = version
        entries = []
        for rel in dep.get("texts", []):
            src = repo / rel
            if not src.is_file():
                print("missing license text: %s" % rel, file=sys.stderr)
                return 1
            entries.append((rel, src.read_text(encoding="utf-8", errors="replace")))
        if entries:
            texts[dep_id] = entries
        manifest.append(
            {
                "id": dep_id,
                "spdx": dep["spdx"],
                "version": version,
                "source": dep.get("source") or None,
                "sha": full_sha,
                "texts": [rel for rel, _ in entries],
                "note": dep.get("note"),
            }
        )

    java_dir = Path(args.java_out) / JAVA_PACKAGE.replace(".", "/")
    java_dir.mkdir(parents=True, exist_ok=True)
    body = "\n".join(
        '        map.put("%s", "%s");' % (d, java_escape(v))
        for d, v in versions.items()
    )
    (java_dir / "GeneratedAboutVersions.java").write_text(
        "package %s;\n\nimport java.util.Collections;\nimport java.util.LinkedHashMap;\n"
        "import java.util.Map;\n\npublic final class GeneratedAboutVersions {\n"
        "    private static final Map<String, String> BY_ID;\n\n    static {\n"
        "        Map<String, String> map = new LinkedHashMap<>();\n%s\n"
        "        BY_ID = Collections.unmodifiableMap(map);\n    }\n\n"
        "    private GeneratedAboutVersions() {\n    }\n\n"
        "    public static String versionForId(String entityId) {\n"
        "        return BY_ID.get(entityId);\n    }\n}\n" % (JAVA_PACKAGE, body)
    )
    text_body = "\n".join(
        '        map.put("%s",\n            %s);' % (d, java_literal("\n".join(t for _, t in e)))
        for d, e in texts.items()
    )
    (java_dir / "GeneratedLicenseTexts.java").write_text(
        "package %s;\n\nimport java.util.Collections;\nimport java.util.LinkedHashMap;\n"
        "import java.util.Map;\n\npublic final class GeneratedLicenseTexts {\n"
        "    private static final Map<String, String> BY_ID;\n\n    static {\n"
        "        Map<String, String> map = new LinkedHashMap<>();\n%s\n"
        "        BY_ID = Collections.unmodifiableMap(map);\n    }\n\n"
        "    private GeneratedLicenseTexts() {\n    }\n\n"
        "    public static String textForId(String entityId) {\n"
        "        return BY_ID.get(entityId);\n    }\n}\n" % (JAVA_PACKAGE, text_body)
    )

    meta_dir = Path(args.meta_out)
    texts_dir = meta_dir / "texts"
    texts_dir.mkdir(parents=True, exist_ok=True)
    for stale in texts_dir.glob("*"):
        stale.unlink()
    for dep_id, entries in texts.items():
        for rel, content in entries:
            dest = texts_dir / ("%s__%s" % (dep_id, Path(rel).name))
            dest.write_text(content, encoding="utf-8")
    (meta_dir / "licenses.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )

    if args.third_party:
        lines = [
            "<!-- Generated by tools/generate-about.py; do not hand-edit. -->",
            "# Third-Party Licenses",
            "",
            "Exact upstream sources and license texts for the native libraries",
            "shipped in Silicon Player releases. License texts ride in-app",
            "(About > Audio cores / Libraries > View license text), in the",
            "AppImage under `usr/share/doc/siliconplayer/`, and in the Arch",
            "package under `/usr/share/licenses/siliconplayer/`.",
            "",
            "| ID | SPDX | Version | Source |",
            "| --- | --- | --- | --- |",
        ]
        for m in manifest:
            src = (
                "`%s` @ `%s`" % (m["source"], m["sha"][:12])
                if m["source"] and m["sha"]
                else (m["source"] or "in-tree")
            )
            lines.append(
                "| %s | %s | %s | %s |" % (m["id"], m["spdx"], m["version"] or "n/a", src)
            )
        for m in manifest:
            if m["note"]:
                lines += ["", "**%s**: %s" % (m["id"], m["note"])]
        lines += [""]
        Path(args.third_party).write_text("\n".join(lines), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())

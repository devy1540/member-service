#!/usr/bin/env python3
"""Export every collected event to a consistent, locally masked submission copy."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import tempfile


CATEGORIES = {
    "raw-session": "session.jsonl",
    "prompts": "prompts.jsonl",
    "responses": "responses.jsonl",
    "commands": "commands.jsonl",
    "tools": "tools.jsonl",
    "files": "files.jsonl",
    "collector": "collector.jsonl",
}
RULES = {
    "local_account_path": re.compile(r"/Users/(?!Shared(?:/|$))[^/\\\s\"'<>`]+"),
    "email": re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
    "provider_credential": re.compile(
        r"\b(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|"
        r"github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{12,}|AKIA[A-Z0-9]{16})\b"
    ),
    "jwt": re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
}
SECRET_KEYS = {"password", "authorization", "api_key", "apikey", "access_token", "refresh_token", "client_secret"}


class Masker:
    def __init__(self, extra_text=()):
        self.counts = Counter()
        self.rules = dict(RULES)
        self.rules["local_account_name"] = re.compile(
            r"(?<![A-Za-z0-9])" + re.escape(getpass.getuser()) + r"(?![A-Za-z0-9])"
        )
        for index, text in enumerate(extra_text):
            if not text:
                raise ValueError("마스킹할 문자열은 비어 있을 수 없습니다.")
            self.rules[f"explicit_text_{index + 1}"] = re.compile(re.escape(text), re.IGNORECASE)

    def apply(self, value, key=None):
        if isinstance(value, str):
            # Opaque data must remain valid; textual searches can match random bytes.
            if key == "encrypted_content" or value.startswith("data:"):
                return value
            if key and key.lower() in SECRET_KEYS and value and value != "***":
                self.counts["credential_field"] += 1
                return "***"
            for name, pattern in self.rules.items():
                replacement = "/Users/***" if name == "local_account_path" else "***"
                value, count = pattern.subn(replacement, value)
                self.counts[name] += count
            return value
        if isinstance(value, dict):
            masked = {}
            for original_key, item in value.items():
                masked_key = self.apply(original_key)
                if masked_key in masked:
                    raise ValueError("마스킹 후 JSON 키가 충돌하여 기록 손실을 막기 위해 중단합니다.")
                masked[masked_key] = self.apply(item, original_key)
            return masked
        if isinstance(value, list):
            return [self.apply(v, key) for v in value]
        return value


def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n"


def verify(output):
    manifest = json.loads((output / "manifest.json").read_text())
    for name, info in manifest["files"].items():
        path = output / name
        digest = hashlib.sha256()
        count = 0
        with path.open("rb") as stream:
            for line in stream:
                json.loads(line)
                digest.update(line)
                count += 1
        if digest.hexdigest() != info["sha256"] or count != info["events"]:
            raise ValueError(f"검증 실패: {name}")
    return manifest


def export(config_path, output, extra_text):
    config = json.loads(config_path.read_text())
    database = Path(config["output_dir"]) / "audit.sqlite3"
    workspace = Path(config["workspace"]).resolve()
    output = output.resolve()
    excluded = [Path(p).resolve() for p in config.get("exclude_paths", [])]
    if output.is_relative_to(workspace) and not any(output == p or output.is_relative_to(p) for p in excluded):
        raise ValueError("내보낼 폴더를 수집기의 exclude_paths에 먼저 추가해야 합니다.")
    output.mkdir(parents=True, exist_ok=True)
    masker = Masker(extra_text)
    manifest = {
        "version": 1,
        "exported_at_utc": datetime.now(timezone.utc).isoformat(),
        "session_id": config["session_id"],
        "content": "All collected events, with text masking; not an unedited original transcript.",
        "prior_masking": "Company identifiers were already masked in the collected logs.",
        "source": "session-logging/logs/audit.sqlite3",
        "ordering": "Original collector insertion order within each category; no event selection or omission.",
        "opaque_payloads": "Encrypted content and embedded data URIs are preserved without modification.",
        "excluded_auxiliary_storage": ["SQLite runtime index", "file-content snapshots"],
        "files": {},
    }
    # One read transaction prevents categories from being exported at different points in time.
    connection = sqlite3.connect(database.as_uri() + "?mode=ro", uri=True)
    try:
        connection.execute("PRAGMA query_only=ON")
        connection.execute("BEGIN")
        counts = dict(connection.execute("SELECT category,count(*) FROM events GROUP BY category"))
        if set(counts) - set(CATEGORIES):
            raise ValueError("알 수 없는 로그 종류가 있어 전체 내보내기를 중단합니다.")
        with tempfile.TemporaryDirectory(prefix=".export-", dir=output) as staging_name:
            staging = Path(staging_name)
            for category, name in CATEGORIES.items():
                digest = hashlib.sha256()
                count = 0
                with (staging / name).open("wb") as stream:
                    for (body,) in connection.execute(
                        "SELECT data FROM events WHERE category=? ORDER BY id", (category,)
                    ):
                        data = encode(masker.apply(json.loads(body))).encode("utf-8")
                        stream.write(data)
                        digest.update(data)
                        count += 1
                if count != counts.get(category, 0):
                    raise ValueError(f"이벤트 수가 일치하지 않습니다: {category}")
                manifest["files"][name] = {"category": category, "events": count, "sha256": digest.hexdigest()}
            manifest["masking_occurrences"] = {k: v for k, v in masker.counts.items() if v}
            (staging / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
            verify(staging)
            # Manifest is published last. --check detects an interrupted replacement.
            for name in [*CATEGORIES.values(), "manifest.json"]:
                (staging / name).replace(output / name)
    finally:
        connection.close()
    return verify(output)


def main():
    os.umask(0o077)
    base = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=base / "config.json")
    parser.add_argument("--output", type=Path, default=base.parent / "ai-logs")
    parser.add_argument("--redact-text", action="append", default=[], help="추가 마스킹할 문자열; 여러 번 지정 가능")
    parser.add_argument("--check", action="store_true", help="제출 파일의 JSON·개수·SHA-256만 검증")
    args = parser.parse_args()
    result = verify(args.output) if args.check else export(args.config, args.output, args.redact_text)
    print(json.dumps({"verified": True, "files": len(result["files"]),
                      "session_events": result["files"]["session.jsonl"]["events"],
                      "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()

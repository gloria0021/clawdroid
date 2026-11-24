#!/usr/bin/env python3
"""リリースのときだけ、日本語の文言（app/src/main/res/values）から各言語の strings.xml を作る。

ふだんの開発では日本語だけを書く。訳はここで作り、app/src/release/res に置く（Release のビルドだけが使う）。
既定の言語（端末の言語に訳が無いとき）は英語にするため、英語は values/、日本語は values-ja/ に書く。

前回訳したときの日本語を app/src/release/translations.json に残し、変わった・増えた文言だけを訳し直す。
訳は Claude Code（claude -p）に頼む。プロジェクトの指示を読ませないよう、空のフォルダで動かす。

  scripts/translate-strings.py            # 足りない訳を作る
  scripts/translate-strings.py --check    # 訳が最新か確かめるだけ（訳さない。古ければ 1 で終わる）
  scripts/translate-strings.py --dry-run  # 訳す件数を数えるだけ
  scripts/translate-strings.py --locales fr,de
  TRANSLATE_MODEL=opus scripts/translate-strings.py
"""
from __future__ import annotations

import argparse
import concurrent.futures
import html
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_FILES = [ROOT / "app/src/main/res/values/strings.xml", ROOT / "app/src/main/res/values/strings_slash.xml"]
OUT_RES = ROOT / "app/src/release/res"
RECORD = ROOT / "app/src/release/translations.json"

# 対応する言語（Android の修飾子 → 訳す先の言語の名前）。en は values/（既定）、ja は values-ja/ に書く。
# 段階 2・3 の言語はここに足す（specs/003-play-store-release/spec.md FR-051）。
LOCALES: dict[str, str] = {
    "en": "English",
    "zh-rCN": "Simplified Chinese (Mainland China)",
    "zh-rTW": "Traditional Chinese (Taiwan)",
    "ko": "Korean",
    "es": "Spanish (neutral, for Spain and Latin America)",
    "fr": "French",
    "de": "German",
    "pt-rBR": "Brazilian Portuguese",
}

# 複数形で言語ごとに要る数量（CLDR）。足りないと lint の MissingQuantity になる。余分は害が無い。
PLURAL_QUANTITIES: dict[str, list[str]] = {
    "en": ["one", "other"], "de": ["one", "other"], "nl": ["one", "other"], "sv": ["one", "other"],
    "zh-rCN": ["other"], "zh-rTW": ["other"], "ko": ["other"], "ja": ["other"],
    "id": ["other"], "vi": ["other"], "th": ["other"],
    "es": ["one", "many", "other"], "fr": ["one", "many", "other"], "pt-rBR": ["one", "many", "other"],
    "it": ["one", "many", "other"], "tr": ["one", "other"], "hi": ["one", "other"], "fa": ["one", "other"],
    "ru": ["one", "few", "many", "other"], "uk": ["one", "few", "many", "other"], "pl": ["one", "few", "many", "other"],
    "ar": ["zero", "one", "two", "few", "many", "other"],
}

SYSTEM_PROMPT = """You translate Android app UI strings for Clawdroid, a companion app for the OpenClaw AI agent Gateway.
The source language is Japanese. An English reference is given when it exists; keep its meaning and length class.

Rules:
- Reply with ONE JSON object only. No prose, no code fences.
- Keys are the resource names you were given. Values keep the same shape as the input: a string, an object of plural quantities, or an array with the same number of items.
- Keep every format placeholder exactly as written (%1$s, %2$d, %d, %s, %%). Do not add or drop any. You may reorder them in the sentence.
- Keep the two-character sequence \\n where it appears. Do not escape anything else; write plain text.
- Do not translate: OpenClaw, Clawdroid, Gateway, Tailscale, Claude, Codex, Markdown, HTML, URL, PTY, ANSI, cron, exec, sp, slash commands such as /loop, and text inside backticks.
- Use the standard platform terms of Android and Material Design in the target language (Settings, Cancel, Retry, Copy, Share...).
- UI text is short and plain. Buttons are verbs. Keep sentences concise. Match the register of a modern developer tool.
- Keys ending in _cd are accessibility labels for screen readers.
- chat_thinking_phrases is a list of playful status lines on a crab / lobster / ocean theme. Translate them freely as playful lines in the same theme; keep the item count and the trailing ellipsis.
- schedule_day_names are the abbreviated weekday names starting from Sunday.
- For plural objects, return exactly the quantities requested for the target language."""

STRING_RE = re.compile(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
PLURALS_RE = re.compile(r'<plurals\s+name="([^"]+)"([^>]*)>(.*?)</plurals>', re.S)
ARRAY_RE = re.compile(r'<string-array\s+name="([^"]+)"([^>]*)>(.*?)</string-array>', re.S)
QTY_RE = re.compile(r'<item\s+quantity="([^"]+)"\s*>(.*?)</item>', re.S)
ITEM_RE = re.compile(r'<item\s*>(.*?)</item>', re.S)
PLACEHOLDER_RE = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?([sdfxXcbeEgGoh%])")

# 言語によってわざと空にしている文言（接頭辞）。英語のスラッシュコマンドの説明は、空なら Gateway の英語の文を出す
EMPTY_BY_DESIGN: dict[str, tuple[str, ...]] = {"en": ("slash_cmd_",)}


def empty_by_design(locale: str, key: str) -> bool:
    return key.startswith(EMPTY_BY_DESIGN.get(locale, ())) if EMPTY_BY_DESIGN.get(locale) else False


# ---------- Android の文字列とふつうの文字の行き来


def unescape(raw: str) -> str:
    """resource の生の中身 → ふつうの文字（\\n はそのまま残す）。"""
    text = html.unescape(raw.strip())
    if len(text) >= 2 and text[0] == text[-1] == '"':
        text = text[1:-1]
    return re.sub(r"\\([\'\"@?])", r"\1", text)


def escape(text: str) -> str:
    """ふつうの文字 → resource の生の中身。"""
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = re.sub(r"(?<!\\)'", r"\\'", text)
    text = re.sub(r'(?<!\\)"', r'\\"', text)
    if text[:1] in ("@", "?"):
        text = "\\" + text
    return text


def parse(path: Path) -> dict[str, dict]:
    """strings.xml を {名前: {"type", "value"}} にする。翻訳しないもの（translatable="false"）は除く。"""
    if not path.exists():
        return {}
    src = re.sub(r"<!--.*?-->", "", path.read_text(encoding="utf-8"), flags=re.S)
    entries: dict[str, dict] = {}
    for name, attrs, body in STRING_RE.findall(src):
        if 'translatable="false"' not in attrs:
            entries[name] = {"type": "string", "value": unescape(body)}
    for name, attrs, body in PLURALS_RE.findall(src):
        if 'translatable="false"' not in attrs:
            entries[name] = {"type": "plurals", "value": {q: unescape(t) for q, t in QTY_RE.findall(body)}}
    for name, attrs, body in ARRAY_RE.findall(src):
        if 'translatable="false"' not in attrs:
            entries[name] = {"type": "array", "value": [unescape(t) for t in ITEM_RE.findall(body)]}
    return entries


def render(entries: dict[str, dict], note: str) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {note} -->", "<resources>"]
    for name in sorted(entries):
        e = entries[name]
        if e["type"] == "string":
            lines.append(f'    <string name="{name}">{escape(e["value"])}</string>')
        elif e["type"] == "plurals":
            lines.append(f'    <plurals name="{name}">')
            lines += [f'        <item quantity="{q}">{escape(t)}</item>' for q, t in e["value"].items()]
            lines.append("    </plurals>")
        else:
            lines.append(f'    <string-array name="{name}">')
            lines += [f"        <item>{escape(t)}</item>" for t in e["value"]]
            lines.append("    </string-array>")
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def locales_config() -> str:
    """Release のアプリ単位の言語の一覧（英語・日本語・そのほかの順）。"""
    tags = ["en", "ja"] + [l.replace("-r", "-") for l in LOCALES if l != "en"]
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- scripts/translate-strings.py が作ったもの。言語はスクリプトの LOCALES で足す -->",
             '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">']
    lines += [f'    <locale android:name="{t}" />' for t in tags]
    lines.append("</locale-config>")
    return "\n".join(lines) + "\n"


LOCALES_CONFIG = OUT_RES / "xml" / "locales_config.xml"


def out_path(locale: str) -> Path:
    folder = "values" if locale == "en" else f"values-{locale}"
    return OUT_RES / folder / "strings.xml"


def canonical(entry: dict) -> str:
    return json.dumps(entry, ensure_ascii=False, sort_keys=True)


# ---------- 訳


def placeholders(text: str) -> list[str]:
    """置き場所の番号と種類だけを比べる（%1$02d と %1$d は同じとみなす）。"""
    return sorted(f"{index}{kind}" for index, kind in PLACEHOLDER_RE.findall(text))


def validate(source: dict, value, locale: str) -> str | None:
    """訳が使えなければ理由を返す。"""
    kind = source["type"]
    if kind == "string":
        if not isinstance(value, str) or not value.strip():
            return "expected a non-empty string"
        if placeholders(value) != placeholders(source["value"]):
            return f"placeholders differ: {placeholders(source['value'])} vs {placeholders(value)}"
    elif kind == "plurals":
        need = PLURAL_QUANTITIES.get(locale, ["one", "other"])
        if not isinstance(value, dict) or any(not isinstance(value.get(q), str) for q in need):
            return f"expected an object with quantities {need}"
        want = placeholders(source["value"].get("other", ""))
        for q in need:
            # one の文では数を書かない言語があるので、other だけ数の置き場所を厳密に見る
            if q == "other" and placeholders(value[q]) != want:
                return f"placeholders differ in {q}"
    else:
        if not isinstance(value, list) or len(value) != len(source["value"]) or not all(isinstance(v, str) and v for v in value):
            return f"expected an array of {len(source['value'])} strings"
    return None


def ask_claude(payload: dict, language: str, model: str, workdir: str) -> dict:
    prompt = (
        f"Target language: {language}\n\n"
        "Translate each entry. Input JSON (ja = Japanese source, en = English reference, "
        "quantities = plural quantities to return):\n" + json.dumps(payload, ensure_ascii=False, indent=1)
    )
    cmd = ["claude", "-p", "--model", model, "--tools", "", "--output-format", "text",
           "--no-session-persistence", "--system-prompt", SYSTEM_PROMPT]
    result = subprocess.run(cmd, input=prompt, capture_output=True, text=True, cwd=workdir, timeout=600)
    if result.returncode != 0:
        raise RuntimeError(f"claude failed ({result.returncode}): {result.stderr.strip()[:300]}")
    out = result.stdout.strip()
    start, end = out.find("{"), out.rfind("}")
    if start < 0 or end < start:
        raise RuntimeError(f"no JSON in reply: {out[:200]}")
    return json.loads(out[start:end + 1])


def translate_locale(locale: str, todo: list[str], source: dict, english: dict, args, workdir: str) -> tuple[dict, list[str]]:
    language = LOCALES[locale]
    done: dict[str, object] = {}
    failed: list[str] = []
    for i in range(0, len(todo), args.batch):
        keys = todo[i:i + args.batch]
        pending = keys
        feedback: dict[str, str] = {}
        for attempt in range(2):
            payload = {}
            for k in pending:
                item: dict[str, object] = {"ja": source[k]["value"]}
                if locale != "en" and k in english:
                    item["en"] = english[k]["value"]
                if source[k]["type"] == "plurals":
                    item["quantities"] = PLURAL_QUANTITIES.get(locale, ["one", "other"])
                if k in feedback:
                    item["previous_error"] = feedback[k]
                payload[k] = item
            try:
                reply = ask_claude(payload, language, args.model, workdir)
            except (RuntimeError, json.JSONDecodeError, subprocess.TimeoutExpired) as e:
                print(f"  [{locale}] batch {i // args.batch + 1}: {e}", file=sys.stderr)
                reply = {}
            retry = []
            for k in pending:
                problem = validate(source[k], reply.get(k), locale) if k in reply else "missing in reply"
                if problem:
                    feedback[k] = problem
                    retry.append(k)
                else:
                    value = reply[k]
                    if source[k]["type"] == "plurals":
                        value = {q: value[q] for q in PLURAL_QUANTITIES.get(locale, ["one", "other"])}
                    done[k] = value
            pending = retry
            if not pending:
                break
        failed += pending
        print(f"  [{locale}] {min(i + args.batch, len(todo))}/{len(todo)}", file=sys.stderr)
    return done, failed


# ---------- 本体


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="訳が最新か確かめるだけ")
    ap.add_argument("--dry-run", action="store_true", help="訳す件数を数えるだけ")
    ap.add_argument("--locales", help="対象の言語（カンマ区切り。既定はすべて）")
    ap.add_argument("--model", default=os.environ.get("TRANSLATE_MODEL", "sonnet"))
    ap.add_argument("--batch", type=int, default=60)
    ap.add_argument("--jobs", type=int, default=4, help="同時に訳す言語の数")
    args = ap.parse_args()

    source: dict[str, dict] = {}
    for f in SOURCE_FILES:
        source.update(parse(f))
    locales = args.locales.split(",") if args.locales else list(LOCALES)
    unknown = [l for l in locales if l not in LOCALES]
    if unknown:
        print(f"知らない言語です: {unknown}（LOCALES に足してください）", file=sys.stderr)
        return 2

    record: dict[str, dict[str, str]] = json.loads(RECORD.read_text(encoding="utf-8")) if RECORD.exists() else {}
    current = {l: parse(out_path(l)) for l in LOCALES}
    plan: dict[str, list[str]] = {}
    empties: dict[str, list[str]] = {}
    for l in locales:
        rec = record.get(l, {})
        plan[l] = sorted(k for k in source if k not in current[l] or rec.get(k) != canonical(source[k]))
        # わざと空にする文言は訳さず、空のまま書く（訳す数には含めるが、claude には渡さない）
        empties[l] = [k for k in plan[l] if empty_by_design(l, k)]
        plan[l] = [k for k in plan[l] if k not in empties[l]]

    # 日本語は訳さずに写す（Release では values/ が英語になるため、日本語を values-ja/ に置く）
    ja_text = render(source, "scripts/translate-strings.py が app/src/main/res/values から写したもの。手で直さない")
    ja_path = OUT_RES / "values-ja" / "strings.xml"
    ja_stale = not ja_path.exists() or ja_path.read_text(encoding="utf-8") != ja_text
    config_stale = not LOCALES_CONFIG.exists() or LOCALES_CONFIG.read_text(encoding="utf-8") != locales_config()
    removed = {l: sorted(set(current[l]) - set(source)) for l in locales}

    total = sum(len(v) for v in plan.values()) + sum(len(v) for v in empties.values())
    for l in locales:
        extra = f"、消す {len(removed[l])} 件" if removed[l] else ""
        extra += f"、空にする {len(empties[l])} 件" if empties[l] else ""
        print(f"{l}: 訳す {len(plan[l])} 件{extra}")
    print(f"ja: {'写し直す' if ja_stale else '最新'}")
    print(f"言語の一覧: {'作り直す' if config_stale else '最新'}")

    if args.check:
        stale = total > 0 or ja_stale or config_stale or any(removed.values())
        print("訳は最新です" if not stale else "訳が古くなっています。scripts/translate-strings.py を実行してください")
        return 1 if stale else 0
    if args.dry_run:
        return 0

    ja_path.parent.mkdir(parents=True, exist_ok=True)
    ja_path.write_text(ja_text, encoding="utf-8")
    LOCALES_CONFIG.parent.mkdir(parents=True, exist_ok=True)
    LOCALES_CONFIG.write_text(locales_config(), encoding="utf-8")
    english = current["en"]
    failures: dict[str, list[str]] = {}
    note = "scripts/translate-strings.py が作ったもの。手で直さず、元の日本語を直して作り直す"

    def run(locale: str):
        with tempfile.TemporaryDirectory(prefix="clawdroid-translate-") as workdir:
            return locale, *translate_locale(locale, plan[locale], source, english, args, workdir)

    # 英語を先に作り、ほかの言語の参考にする
    order = [l for l in locales if l == "en"] + [l for l in locales if l != "en"]
    groups = [[l for l in order if l == "en"], [l for l in order if l != "en"]]
    for group in groups:
        if not group:
            continue
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
            for locale, done, failed in pool.map(run, group):
                entries = {k: v for k, v in current[locale].items() if k in source}
                rec = {k: v for k, v in record.get(locale, {}).items() if k in source}
                for k in empties[locale]:
                    done[k] = ""
                for k, value in done.items():
                    entries[k] = {"type": source[k]["type"], "value": value}
                    rec[k] = canonical(source[k])
                path = out_path(locale)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(render(entries, note), encoding="utf-8")
                record[locale] = dict(sorted(rec.items()))
                RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
                if failed:
                    failures[locale] = failed
                if locale == "en":
                    english = entries

    if failures:
        for l, keys in failures.items():
            print(f"訳せなかった文言 [{l}]: {', '.join(keys)}", file=sys.stderr)
        print("もう一度実行すると、訳せなかったものだけを訳し直します", file=sys.stderr)
        return 1
    print("訳を作りました")
    return 0


if __name__ == "__main__":
    sys.exit(main())

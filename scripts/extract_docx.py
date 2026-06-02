import zipfile
import re
import sys
import html
from pathlib import Path


def extract_docx(path: Path) -> str:
    with zipfile.ZipFile(path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    text = re.sub(r"</w:p>", "\n", xml)
    text = re.sub(r"<[^>]+>", "", text)
    for old, new in [("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&"), ("&quot;", '"')]:
        text = text.replace(old, new)
    text = re.sub(r"\n+", "\n", text).strip()
    return text


def text_to_html(text: str) -> str:
    paragraphs = [p.strip() for p in text.split("\n") if p.strip()]
    return "\n".join(f"<p>{html.escape(p)}</p>" for p in paragraphs)


if __name__ == "__main__":
    out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    files = sys.argv[2:] if out_dir else sys.argv[1:]

    for f in files:
        p = Path(f)
        if not p.exists():
            print(f"MISSING: {f}", file=sys.stderr)
            continue
        text = extract_docx(p)
        if out_dir:
            slug = p.stem.lower().replace(" ", "_")
            (out_dir / f"{slug}.html").write_text(text_to_html(text), encoding="utf-8")
            (out_dir / f"{slug}.txt").write_text(text, encoding="utf-8")
            print(f"Wrote {slug}.html ({len(text)} chars)")
        else:
            print("=" * 80)
            print(p.name)
            print("=" * 80)
            print(text[:3000])
            print(f"\n... TOTAL LENGTH: {len(text)} chars\n")

# -*- coding: utf-8 -*-
"""Build structured HTML privacy policy from PDF text (UTF-8)."""
from __future__ import annotations

import html
import re
from pathlib import Path

from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
PDF = ROOT / "public/legal/privacy.pdf"
OUT = ROOT / "templates/legal/content/privacy-policy-worldcashfit.html.twig"

TERM_NAMES = [
    "Политика",
    "Персональные данные",
    "Оператор",
    "Сайт",
    "Платформа WorldCashFit",
    "Программа WorldCashFit / Программа",
    "Мобильное приложение WorldCashFit / Приложение",
    "Партнер",
    "Клуб",
    "Пользователь Сайта",
    "Пользователь Приложения",
    "Пользователь Программы",
    "Клиент клуба",
    "Тренер",
    "Учетная запись",
    "Регистрационные данные",
    "Данные Партнера",
    "Сервис авторизации",
    "Файлы Cookie",
    "Обработка персональных данных",
    "Информационная система персональных данных",
    "Локализация",
    "Трансграничная передача персональных данных",
    "Правовые документы",
]


def extract_text() -> str:
    r = PdfReader(str(PDF))
    parts: list[str] = []
    for page in r.pages:
        t = page.extract_text() or ""
        t = re.sub(r"^ООО «Ворлдкэшбокс»\s*\n(?:WorldCashFit\s*\n)?", "", t)
        t = re.sub(r"\nООО «Ворлдкэшбокс»\s*(?:\nWorldCashFit\s*)?\n?", "\n", t)
        parts.append(t)
    raw = "\n".join(parts)
    # Join hyphenated line breaks from PDF extraction
    raw = re.sub(r"(\w)-\n(\w)", r"\1\2", raw)
    raw = re.sub(r"[ \t]+\n", "\n", raw)
    raw = re.sub(r"\n[ \t]+", "\n", raw)
    # Soft-wrap: join single newlines inside paragraphs, keep blank lines
    lines = [ln.strip() for ln in raw.splitlines()]
    joined: list[str] = []
    buf: list[str] = []
    for ln in lines:
        if not ln:
            if buf:
                joined.append(" ".join(buf))
                buf = []
            joined.append("")
            continue
        buf.append(ln)
    if buf:
        joined.append(" ".join(buf))
    text = "\n".join(joined)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"\s+([,.;:])", r"\1", text)
    text = re.sub(r"\s+–\s+", " – ", text)
    text = re.sub(r"\s+-\s+", "-", text)
    text = re.sub(r" {2,}", " ", text)
    return text.strip()


def e(s: str) -> str:
    return html.escape(s, quote=True)


def cleanup_text(s: str) -> str:
    s = re.sub(r"\s+", " ", s).strip()
    # PDF extraction artifacts
    fixes = [
        (r"настояще\s+й", "настоящей"),
        (r"резервного\s+к\s+опирования", "резервного копирования"),
        (r"не\s+обе\s+спечена", "не обеспечена"),
        (r"соотв\s+етствующим", "соответствующим"),
        (r"преде\s+лах", "пределах"),
        (r"соблю\s+дении", "соблюдении"),
        (r"б\s+езопасность", "безопасность"),
        (r"не\s+яв\s+ляются", "не являются"),
        (r"Оператор\s+п\s+олучает", "Оператор получает"),
        (r"такие\s+с\s+пособы", "такие способы"),
        (r"QR\s*-\s*", "QR-"),
        (r"веб\s*-\s*", "веб-"),
        (r"СМС\s*-\s*", "СМС-"),
        (r"push\s*-\s*", "push-"),
        (r"IP\s*-\s*", "IP-"),
        (r"HTTP\s*-\s*", "HTTP-"),
        (r"3\s*-\s*го", "3-го"),
        (r"152\s*-\s*ФЗ", "152-ФЗ"),
        (r"IPадрес", "IP-адрес"),
        (r"бухгалтерског\s+о,", "бухгалтерского,"),
    ]
    for pat, repl in fixes:
        s = re.sub(pat, repl, s, flags=re.I)
    return s


def split_clauses(body: str) -> list[str]:
    """Split section body into 1.1 / 4.1.1 style clauses and leftover intro."""
    body = body.strip()
    if not body:
        return []
    # Do not match inside longer numbers: avoid splitting 4.1.1 into 4.1 + 1.1
    pattern = re.compile(r"(?=(?<![.\d])(\d+\.\d+(?:\.\d+)*)\.\s)")
    parts = pattern.split(body)
    out: list[str] = []
    if parts and parts[0].strip():
        out.append(cleanup_text(parts[0]))
    i = 1
    while i + 1 < len(parts):
        num = parts[i]
        rest = parts[i + 1].strip()
        # Lookahead split keeps the number inside rest — strip duplicate.
        rest = re.sub(rf"^{re.escape(num)}\.\s*", "", rest)
        out.append(cleanup_text(f"{num}. {rest}"))
        i += 2
    return out if out else [cleanup_text(body)]


def format_clause_block(text: str) -> str:
    text = cleanup_text(text)
    if not text:
        return ""

    # Subsection title only: "4.1. Приложение"
    sm = re.match(r"^(\d+\.\d+\.)\s+([А-ЯЁA-Z].+)$", text)
    if sm and len(text) < 110 and not re.search(r"\d+\.\d+\.\d+", text):
        words = sm.group(2).split()
        if len(words) <= 12 and not sm.group(2).endswith((".", ";", ":")):
            return (
                f'<h3 class="doc-subheading"><span class="doc-num">{e(sm.group(1))}</span> '
                f"{e(sm.group(2))}</h3>"
            )
        if sm.group(2).endswith(":") is False and len(words) <= 10:
            # titles without trailing period are common
            if not re.search(r"[.!?]$", sm.group(2)) and len(text) < 90:
                return (
                    f'<h3 class="doc-subheading"><span class="doc-num">{e(sm.group(1))}</span> '
                    f"{e(sm.group(2))}</h3>"
                )

    if "▪" in text:
        chunks = re.split(r"(?=▪\s*)", text)
        html_parts: list[str] = []
        bullets: list[str] = []
        for ch in chunks:
            ch = cleanup_text(ch)
            if not ch:
                continue
            if ch.startswith("▪"):
                bullets.append(ch.lstrip("▪").strip())
            else:
                if bullets:
                    html_parts.append('<ul class="doc-list-bullets">')
                    html_parts.extend(f"<li>{e(b)}</li>" for b in bullets)
                    html_parts.append("</ul>")
                    bullets = []
                m = re.match(r"^(\d+(?:\.\d+)+\.)\s+(.*)$", ch, re.S)
                if m:
                    html_parts.append(
                        f'<p class="doc-clause"><span class="doc-num">{e(m.group(1))}</span> {e(m.group(2))}</p>'
                    )
                else:
                    html_parts.append(f'<p class="doc-p">{e(ch)}</p>')
        if bullets:
            html_parts.append('<ul class="doc-list-bullets">')
            html_parts.extend(f"<li>{e(b)}</li>" for b in bullets)
            html_parts.append("</ul>")
        return "\n".join(html_parts)

    m = re.match(r"^(\d+(?:\.\d+)+\.)\s+(.*)$", text, re.S)
    if m:
        body = m.group(2)
        # Title + following clause glued: "4.1. Приложение 4.1.1. Text"
        sub = re.match(r"^([А-ЯЁ][^.?!]{1,80}?)\s+(\d+\.\d+\.\d+\.\s+.*)$", body, re.S)
        if sub and len(sub.group(1).split()) <= 12:
            title = sub.group(1).strip()
            rest = sub.group(2)
            parts = [
                f'<h3 class="doc-subheading"><span class="doc-num">{e(m.group(1))}</span> {e(title)}</h3>'
            ]
            for piece in split_clauses(rest):
                parts.append(format_clause_block(piece))
            return "\n".join(parts)
        return (
            f'<p class="doc-clause"><span class="doc-num">{e(m.group(1))}</span> {e(body)}</p>'
        )
    return f'<p class="doc-p">{e(text)}</p>'


def extract_definitions(text: str) -> tuple[str, list[tuple[str, str]]]:
    """Return (text_without_defs_block_intro_kept, definitions)."""
    # Find definitions region: after intro until "1. Общие положения"
    m = re.search(
        r"(В целях единообразного.*?определений:\s*)(.*?)(?=\n1\.\s+Общие положения)",
        text,
        re.S,
    )
    if not m:
        return text, []
    intro = m.group(1).strip()
    block = m.group(2)
    # Remove "Термины и определения" label if present
    block = re.sub(r"^Термины и определения\s*", "", block.strip())

    defs: list[tuple[str, str]] = []
    # Flat string after PDF join — terms are separated by "Term –" without newlines.
    escaped = [re.escape(n) for n in sorted(TERM_NAMES, key=len, reverse=True)]
    term_re = re.compile(r"(" + "|".join(escaped) + r")\s*[–—-]\s*")
    matches = list(term_re.finditer(block))
    for i, match in enumerate(matches):
        name = match.group(1)
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(block)
        body = block[start:end].strip()
        body = re.sub(r"\s+", " ", body)
        body = re.sub(r"^Термины и определения\s*", "", body)
        defs.append((name, body))

    before = text[: m.start()]
    after = text[m.end() :]
    rebuilt = before + intro + "\n\n__DEFINITIONS__\n\n" + after
    return rebuilt, defs


def main() -> None:
    text = extract_text()
    text, defs = extract_definitions(text)

    # Cover bits
    title = "Политика обработки и защиты персональных данных платформы WorldCashFit"
    operator = (
        "Общество с ограниченной ответственностью «Ворлдкэшбокс» "
        "(ИНН 2543082240; ОГРН 1152543020629)"
    )
    email = "vl@worldcashbox.ru"
    edition = "12 мая 2026 г."
    city = "г. Владивосток, 2026 год"

    sections_order = [
        ("terms", "Термины и определения"),
        ("s1", "1. Общие положения"),
        ("s2", "2. Принципы обработки персональных данных"),
        ("s3", "3. Категории субъектов, цели, состав и сроки обработки персональных данных"),
        ("s4", "4. Особенности обработки персональных данных в платформе WorldCashFit"),
        ("s5", "5. Основания обработки персональных данных"),
        ("s6", "6. Передача персональных данных"),
        ("s7", "7. Файлы Cookie, аналитика, уведомления и внешние сервисы"),
        ("s8", "8. Хранение и защита персональных данных"),
        ("s9", "9. Права субъекта персональных данных"),
        ("s10", "10. Заключительные положения"),
        ("s11", "11. Реквизиты Оператора"),
    ]

    # Split numbered sections from "1. Общие"
    body = text
    # Drop cover/toc noise before definitions marker or first section
    if "__DEFINITIONS__" in body:
        body = body.split("__DEFINITIONS__", 1)[1]
    # Also remove leftover TOC if still present before section 1
    body = re.sub(
        r"^.*?((?=1\.\s+Общие положения)|(?=__DEFINITIONS__))",
        "",
        body,
        count=1,
        flags=re.S,
    )

    section_starts = [
        (r"1\.\s+Общие положения", "s1", "1. Общие положения"),
        (r"2\.\s+Принципы обработки персональных данных", "s2", "2. Принципы обработки персональных данных"),
        (
            r"3\.\s+Категории субъектов, цели, состав и сроки обработки персональных данных",
            "s3",
            "3. Категории субъектов, цели, состав и сроки обработки персональных данных",
        ),
        (
            r"4\.\s+Особенности обработки персональных данных в платформе WorldCashFit",
            "s4",
            "4. Особенности обработки персональных данных в платформе WorldCashFit",
        ),
        (r"5\.\s+Основания обработки персональных данных", "s5", "5. Основания обработки персональных данных"),
        (r"6\.\s+Передача персональных данных", "s6", "6. Передача персональных данных"),
        (
            r"7\.\s+Файлы Cookie, аналитика, уведомления и внешние сервисы",
            "s7",
            "7. Файлы Cookie, аналитика, уведомления и внешние сервисы",
        ),
        (r"8\.\s+Хранение и защита персональных данных", "s8", "8. Хранение и защита персональных данных"),
        (r"9\.\s+Права субъекта персональных данных", "s9", "9. Права субъекта персональных данных"),
        (r"10\.\s+Заключительные положения", "s10", "10. Заключительные положения"),
        (r"11\.\s+Реквизиты Оператора", "s11", "11. Реквизиты Оператора"),
    ]

    # Find positions in full text (with definitions placeholder already applied)
    work = text if "__DEFINITIONS__" in text else ("__DEFINITIONS__\n\n" + body)
    # Prefer working from original text with marker
    work = text

    # Section headings also appear in the TOC — search only in the body after definitions.
    anchor = work.find("__DEFINITIONS__")
    search_base = work[anchor:] if anchor >= 0 else work
    base_offset = anchor if anchor >= 0 else 0

    positions: list[tuple[int, str, str]] = []
    for pat, sid, title_s in section_starts:
        for m in re.finditer(pat, search_base):
            after = search_base[m.end() : m.end() + 120]
            # Real body sections are followed by clause numbers (1.1 / 11. is requisites).
            if sid == "s11" or re.search(r"\d+\.\d+\.", after):
                positions.append((base_offset + m.start(), sid, title_s))
                break
    positions.sort()
    print("positions", [(sid, pos) for pos, sid, _ in positions])

    chunks: dict[str, str] = {}
    for i, (pos, sid, title_s) in enumerate(positions):
        end = positions[i + 1][0] if i + 1 < len(positions) else len(work)
        content = work[pos:end]
        # Strip only the section heading once (do not use [^\n]+ — text is flat).
        content = re.sub(r"^" + re.escape(title_s) + r"\s*", "", content, count=1).strip()
        if content.startswith(title_s.split(". ", 1)[-1][:20]):
            # fallback if spacing differs
            content = re.sub(r"^\d+\.\s+.{10,120}?(?=\d+\.\d+\.)", "", content, count=1).strip()
        chunks[sid] = content
        print(sid, "chars", len(content))

    # Intro before definitions
    intro_m = re.search(
        r"(В целях единообразного.*?определений:)",
        extract_text(),
        re.S,
    )
    intro = intro_m.group(1) if intro_m else (
        "В целях единообразного и однозначного толкования условий настоящей Политики, "
        "следует определить следующие значения ряда терминов и определений:"
    )
    intro = cleanup_text(intro)

    out: list[str] = []
    out.append('<div class="doc-cover">')
    out.append(f'<p class="doc-subtitle">{e(title)}</p>')
    out.append('<div class="doc-rule"></div>')
    out.append('<dl class="doc-meta">')
    out.append(f"<dt>Оператор</dt><dd>{e(operator)}</dd>")
    out.append(
        f'<dt>Электронная почта</dt><dd><a href="mailto:{e(email)}">{e(email)}</a></dd>'
    )
    out.append(
        '<dt>Веб-адрес официальной публикации</dt>'
        '<dd><a href="https://worldcashfit.ru/privacy/">https://worldcashfit.ru/privacy/</a></dd>'
    )
    out.append(f"<dt>Текущая редакция</dt><dd>{e(edition)}</dd>")
    out.append("</dl>")
    out.append(f'<p class="doc-city">{e(city)}</p>')
    out.append("</div>")

    out.append('<nav class="doc-toc" aria-label="Оглавление">')
    out.append('<h2 class="doc-section-title">Оглавление</h2>')
    out.append('<div class="doc-rule"></div>')
    out.append("<ol>")
    for sid, stitle in sections_order:
        label = stitle
        # strip leading number for ol
        label = re.sub(r"^\d+\.\s+", "", label)
        if sid == "terms":
            label = "Термины и определения"
        out.append(f'<li><a href="#{sid}">{e(label)}</a></li>')
    out.append("</ol>")
    out.append("</nav>")

    # Definitions
    out.append('<section class="doc-section" id="terms">')
    out.append('<h2 class="doc-section-title">Термины и определения</h2>')
    out.append('<div class="doc-rule"></div>')
    out.append(f'<p class="doc-p">{e(intro)}</p>')
    out.append('<dl class="doc-footnotes">')
    for name, body_def in defs:
        out.append(f"<dt>{e(name)}</dt>")
        out.append(f"<dd>{e(body_def)}</dd>")
    out.append("</dl>")
    out.append("</section>")

    for sid, stitle in sections_order:
        if sid == "terms":
            continue
        content = chunks.get(sid, "")
        out.append(f'<section class="doc-section" id="{sid}">')
        out.append(f'<h2 class="doc-section-title">{e(stitle)}</h2>')
        out.append('<div class="doc-rule"></div>')

        if sid == "s11":
            # requisites as definition list
            out.append('<dl class="doc-meta">')
            pairs = [
                ("Полное наименование", "Общество с ограниченной ответственностью «Ворлдкэшбокс»"),
                ("Юридический адрес", "690014, Приморский край, г. Владивосток, ул. Толстого, д. 32 А, офис 308"),
                ("ИНН/КПП", "2543082240 / 253601001"),
                ("ОГРН", "1152543020629"),
                ("Телефон", "8 994 010 72 72"),
                ("Электронная почта", "vl@worldcashbox.ru"),
            ]
            for k, v in pairs:
                if k == "Электронная почта":
                    out.append(f'<dt>{e(k)}</dt><dd><a href="mailto:{e(v)}">{e(v)}</a></dd>')
                elif k == "Телефон":
                    out.append(f'<dt>{e(k)}</dt><dd><a href="tel:+79940107272">{e(v)}</a></dd>')
                else:
                    out.append(f"<dt>{e(k)}</dt><dd>{e(v)}</dd>")
            out.append("</dl>")
        else:
            for piece in split_clauses(content):
                out.append(format_clause_block(piece))

        out.append("</section>")

    OUT.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"wrote {OUT} defs={len(defs)} sections={len(chunks)}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Gera a pagina publica da politica de privacidade a partir de docs/privacidade.md.

docs/privacidade.md e a fonte unica: o app embute esse arquivo no build e esta pagina e o mesmo texto
em HTML. A saida, docs/privacidade/index.html, e publicada pelo GitHub Pages da pasta docs/
deste repositorio em https://veronezzi.github.io/cola-eleitoral/privacidade/ (passo a passo em docs/PUBLICACAO.md).
Nao edite o HTML a mao: mude o Markdown e rode este script.

Uso:
  python3 scripts/render-privacy-page.py            # grava docs/privacidade/index.html
  python3 scripts/render-privacy-page.py --check    # sai com 1 se o HTML estiver desatualizado
  python3 scripts/render-privacy-page.py --output ARQUIVO

So usa a biblioteca padrao. Aceita apenas o subconjunto de Markdown que o app tambem mostra:
"# " so no titulo (linha 1); secoes "## "; paragrafos (linhas seguidas; linha em branco separa
blocos); listas "- " com continuacao indentada por 2 espacos, sem aninhar; **negrito**; `codigo`;
links no formato <https://...>; e-mails em texto puro (viram mailto:). Tabela, citacao, HTML,
[texto](url), italico, lista numerada ou aninhada fazem o script falhar (saida 2), para o texto nao
sair diferente no app e na web.

A pagina e autocontida: CSS embutido (liberado pela Content-Security-Policy por hash), nenhum script,
fonte, imagem ou recurso externo, so links ancora (#...) e absolutos, entao funciona na raiz do site.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import html
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs/privacidade.md"
OUTPUT = ROOT / "docs/privacidade/index.html"

MONTHS = {
    "janeiro": 1, "fevereiro": 2, "março": 3, "abril": 4, "maio": 5, "junho": 6,
    "julho": 7, "agosto": 8, "setembro": 9, "outubro": 10, "novembro": 11, "dezembro": 12,
}

CSS = """
  :root {
    --bg: #ffffff;
    --text: #1d2327;
    --muted: #4a565d;
    --border: #c9d1d6;
    --surface: #f2f5f7;
    --link: #0b5394;
    --focus: #b35900;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #121518;
      --text: #e8ecef;
      --muted: #b3bec5;
      --border: #3b464d;
      --surface: #1c2125;
      --link: #8ab8f0;
      --focus: #ffb366;
    }
  }
  *, *::before, *::after { box-sizing: border-box; }
  html { -webkit-text-size-adjust: 100%; text-size-adjust: 100%; }
  body {
    margin: 0;
    background: var(--bg);
    color: var(--text);
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", sans-serif;
    font-size: 1.0625rem;
    line-height: 1.6;
    overflow-wrap: break-word;
  }
  .skip {
    position: absolute;
    left: 1rem;
    top: -4rem;
    padding: .5rem 1rem;
    background: var(--surface);
    color: var(--text);
    border: 2px solid var(--focus);
    z-index: 1;
  }
  .skip:focus { top: 1rem; }
  header, main, footer { max-width: 46rem; margin: 0 auto; padding: 0 1rem; }
  header { padding-top: 2rem; }
  h1 { font-size: 1.75rem; line-height: 1.25; margin: 0 0 .5rem; }
  h2 { font-size: 1.3rem; line-height: 1.3; margin: 2.25rem 0 .75rem; }
  p, ul, ol { margin: 0 0 1rem; }
  ul, ol { padding-left: 1.5rem; }
  li { margin-bottom: .5rem; }
  .meta { color: var(--muted); margin-bottom: 1.5rem; }
  a { color: var(--link); text-underline-offset: .15em; }
  a:focus-visible { outline: 3px solid var(--focus); outline-offset: 2px; }
  code { font-family: ui-monospace, "Cascadia Mono", "Roboto Mono", monospace; font-size: .92em; overflow-wrap: anywhere; }
  .summary {
    background: var(--surface);
    border-left: 4px solid var(--border);
    padding: .25rem 1.25rem .25rem;
    margin: 0 0 1.5rem;
  }
  .summary h2 { margin-top: 1rem; }
  nav ol { columns: 2 16rem; column-gap: 2rem; }
  nav li { break-inside: avoid; }
  footer { color: var(--muted); padding-top: 1rem; padding-bottom: 3rem; border-top: 1px solid var(--border); margin-top: 2.5rem; }
  @media print {
    .skip, nav, footer { display: none; }
    body { font-size: 11pt; }
  }
"""


class MarkdownError(Exception):
    """A construct outside the subset shared with the app."""


@dataclass
class Block:
    kind: str  # h1, h2, p, ul
    line: int
    text: str = ""
    items: list[list[str]] = field(default_factory=list)


# ------------------------------------------------------------------------------------- blocks

UNSUPPORTED_LINE = [
    (re.compile(r"^\s*>"), "citacao (>)"),
    (re.compile(r"^\s*\|"), "tabela (|)"),
    (re.compile(r"^\s*<(?!https://)"), "HTML"),
    (re.compile(r"^\s*(```|~~~)"), "bloco de codigo"),
    (re.compile(r"^\s*([*+]|\d+[.)])\s"), "lista com * + ou numerada"),
    (re.compile(r"^\s*(-{3,}|\*{3,}|_{3,})\s*$"), "linha horizontal"),
]


def parse_blocks(markdown: str) -> list[Block]:
    blocks: list[Block] = []
    current: Block | None = None

    def flush() -> None:
        nonlocal current
        if current is not None:
            blocks.append(current)
            current = None

    for number, raw in enumerate(markdown.splitlines(), start=1):
        def fail(problem: str) -> MarkdownError:
            return MarkdownError(f"docs/privacidade.md:{number}: {problem}")

        if "\t" in raw:
            raise fail("tabulacao; use espacos")
        if raw != raw.rstrip():
            raise fail("espaco no fim da linha (no Markdown, dois espacos viram quebra de linha)")
        line = raw
        if not line:
            flush()
            continue
        for pattern, name in UNSUPPORTED_LINE:
            if pattern.match(line):
                raise fail(f"{name} nao faz parte do subconjunto aceito pelo app")
        if line.startswith("#"):
            flush()
            match = re.fullmatch(r"(#{1,2}) (\S.*)", line)
            if not match:
                raise fail("so ha titulo '# ' e secoes '## '")
            level = len(match.group(1))
            if level == 1 and (blocks or number != 1):
                raise fail("'# ' so na primeira linha (titulo)")
            blocks.append(Block(kind=f"h{level}", line=number, text=match.group(2)))
            continue
        if line.startswith("- "):
            if current is not None and current.kind == "p":
                raise fail("lista colada num paragrafo: deixe uma linha em branco antes")
            if current is None:
                current = Block(kind="ul", line=number)
            current.items.append([line[2:].strip()])
            continue
        if line.startswith(" "):
            if current is None or current.kind != "ul" or not line.startswith("  ") or line[2] == " ":
                raise fail("indentacao fora de um item de lista (use 2 espacos so na continuacao do item)")
            if line.strip().startswith("- "):
                raise fail("lista aninhada nao faz parte do subconjunto")
            current.items[-1].append(line.strip())
            continue
        if current is not None and current.kind == "ul":
            raise fail("paragrafo colado numa lista: deixe uma linha em branco antes")
        if current is None:
            current = Block(kind="p", line=number)
        current.text = f"{current.text} {line.strip()}".strip()
    flush()
    if not blocks or blocks[0].kind != "h1":
        raise MarkdownError("docs/privacidade.md:1: falta o titulo '# ' na primeira linha")
    return blocks


# ------------------------------------------------------------------------------------- inline

TOKEN = re.compile(
    r"`(?P<code>[^`]+)`"
    r"|<(?P<url>https://[^<>\s]+)>"
    r"|\*\*(?P<bold>.+?)\*\*"
    r"|(?P<email>[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+)"
)
LEFTOVER = re.compile(r"[<`*_\\]|\]\(")


def inline(text: str, line: int) -> str:
    out: list[str] = []
    position = 0

    def plain(segment: str) -> str:
        bad = LEFTOVER.search(segment)
        if bad:
            raise MarkdownError(
                f"docs/privacidade.md:{line}: '{bad.group(0)}' fora de `codigo`, **negrito** ou "
                "<https://...> (italico, HTML, [texto](url) e escapes nao fazem parte do subconjunto)"
            )
        return html.escape(segment, quote=False)

    for match in TOKEN.finditer(text):
        out.append(plain(text[position:match.start()]))
        if match.group("code") is not None:
            out.append(f"<code>{html.escape(match.group('code'), quote=False)}</code>")
        elif match.group("url") is not None:
            url = match.group("url")
            out.append(f'<a href="{html.escape(url)}">{html.escape(url, quote=False)}</a>')
        elif match.group("bold") is not None:
            out.append(f"<strong>{inline(match.group('bold'), line)}</strong>")
        else:
            email = match.group("email")
            out.append(f'<a href="mailto:{html.escape(email)}">{html.escape(email, quote=False)}</a>')
        position = match.end()
    out.append(plain(text[position:]))
    return "".join(out)


def plain_text(text: str) -> str:
    """Markdown inline -> plain text (for <title> and the meta description)."""
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"<(https://[^<>\s]+)>", r"\1", text)
    return text.replace("**", "")


def with_time(escaped: str) -> str:
    """Wraps the first "4 de outubro de 2026" in <time datetime="2026-10-04">."""
    pattern = r"(\d{1,2}) de (" + "|".join(MONTHS) + r") de (\d{4})"

    def replace(match: re.Match[str]) -> str:
        day, month, year = int(match.group(1)), MONTHS[match.group(2)], match.group(3)
        return f'<time datetime="{year}-{month:02d}-{day:02d}">{match.group(0)}</time>'

    return re.sub(pattern, replace, escaped, count=1)


def slug(text: str, used: set[str]) -> str:
    base = re.sub(r"^\d+\.\s*", "", text)
    base = unicodedata.normalize("NFKD", base).encode("ascii", "ignore").decode()
    base = re.sub(r"[^a-z0-9]+", "-", base.lower()).strip("-") or "secao"
    candidate, counter = base, 2
    while candidate in used:
        candidate, counter = f"{base}-{counter}", counter + 1
    used.add(candidate)
    return candidate


# --------------------------------------------------------------------------------------- page

def render_block(block: Block, indent: str) -> str:
    if block.kind == "p":
        return f"{indent}<p>{inline(block.text, block.line)}</p>"
    items = "\n".join(
        f"{indent}  <li>{inline(' '.join(item), block.line)}</li>" for item in block.items
    )
    return f"{indent}<ul>\n{items}\n{indent}</ul>"


def render(markdown: str) -> str:
    blocks = parse_blocks(markdown)
    title = blocks[0].text
    body = blocks[1:]

    meta_html = ""
    if body and body[0].kind == "p" and re.fullmatch(r"\*\*[^*]+\*\*", body[0].text):
        meta_html = f'  <p class="meta">{with_time(inline(body[0].text[2:-2], body[0].line))}</p>\n'
        body = body[1:]

    intro: list[Block] = []
    sections: list[tuple[Block, list[Block]]] = []
    for block in body:
        if block.kind == "h2":
            sections.append((block, []))
        elif block.kind == "h1":
            raise MarkdownError(f"docs/privacidade.md:{block.line}: so um titulo '# '")
        elif sections:
            sections[-1][1].append(block)
        else:
            intro.append(block)

    first_paragraph = next((b.text for b in intro if b.kind == "p"), title)
    description = re.split(r"(?<=[.!?])\s", plain_text(first_paragraph), maxsplit=1)[0]

    used: set[str] = {"conteudo", "topo", "sumario"}
    numbered: list[tuple[str, str]] = []
    rendered_sections: list[str] = []
    summary_done = False
    for heading, content in sections:
        anchor = slug(heading.text, used)
        number = re.match(r"(\d+)\.\s+(.*)", heading.text)
        if number:
            if int(number.group(1)) != len(numbered) + 1:
                raise MarkdownError(
                    f"docs/privacidade.md:{heading.line}: secoes numeradas fora de ordem "
                    f"(esperado {len(numbered) + 1}.)"
                )
            numbered.append((anchor, number.group(2)))
        css_class = ' class="summary"' if heading.text == "Resumo" and not summary_done else ""
        summary_done = summary_done or bool(css_class)
        parts = [f'  <section{css_class} aria-labelledby="{anchor}">']
        parts.append(f'    <h2 id="{anchor}">{inline(heading.text, heading.line)}</h2>')
        parts.extend(render_block(block, "    ") for block in content)
        parts.append("  </section>")
        rendered_sections.append("\n".join(parts))

    toc = ""
    if numbered:
        entries = "\n".join(
            f'      <li><a href="#{anchor}">{inline(text, 0)}</a></li>' for anchor, text in numbered
        )
        toc = (
            '  <nav aria-labelledby="sumario">\n'
            '    <h2 id="sumario">Sumário</h2>\n'
            f"    <ol>\n{entries}\n    </ol>\n"
            "  </nav>"
        )
    # The table of contents goes right after the summary (or before the first section).
    main_parts = [render_block(block, "  ") for block in intro]
    insert_at = 1 if summary_done and sections and sections[0][0].text == "Resumo" else 0
    for index, section in enumerate(rendered_sections):
        if index == insert_at and toc:
            main_parts.append(toc)
        main_parts.append(section)
    if toc and insert_at >= len(rendered_sections):
        main_parts.append(toc)

    style_hash = base64.b64encode(hashlib.sha256(CSS.encode("utf-8")).digest()).decode("ascii")
    csp = f"default-src 'none'; style-src 'sha256-{style_hash}'; base-uri 'none'; form-action 'none'"
    main_html = "\n\n".join(main_parts)
    return (
        "<!doctype html>\n"
        '<html lang="pt-BR">\n'
        "<head>\n"
        '<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        f'<meta http-equiv="Content-Security-Policy" content="{csp}">\n'
        '<meta name="referrer" content="no-referrer">\n'
        '<meta name="color-scheme" content="light dark">\n'
        f"<title>{html.escape(plain_text(title), quote=False)}</title>\n"
        f'<meta name="description" content="{html.escape(description)}">\n'
        "<!-- Gerado por scripts/render-privacy-page.py a partir de docs/privacidade.md (repositório do"
        " app). Não edite à mão. -->\n"
        f"<style>{CSS}</style>\n"
        "</head>\n"
        "<body>\n"
        '<a class="skip" href="#conteudo">Ir para o conteúdo</a>\n'
        "\n"
        '<header id="topo">\n'
        f"  <h1>{inline(title, 1)}</h1>\n"
        f"{meta_html}"
        "</header>\n"
        "\n"
        '<main id="conteudo">\n'
        f"{main_html}\n"
        "</main>\n"
        "\n"
        "<footer>\n"
        '  <p><a href="#topo">Voltar ao início</a></p>\n'
        "</footer>\n"
        "</body>\n"
        "</html>\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="so confere se o HTML esta atualizado")
    parser.add_argument("--output", type=Path, default=OUTPUT, help="arquivo de saida")
    args = parser.parse_args()

    try:
        page = render(SOURCE.read_text(encoding="utf-8"))
    except MarkdownError as error:
        print(error, file=sys.stderr)
        return 2

    output: Path = args.output
    shown = output.relative_to(ROOT) if output.is_relative_to(ROOT) else output
    if args.check:
        current = output.read_text(encoding="utf-8") if output.exists() else None
        if current != page:
            print(
                f"{shown} esta desatualizado em relacao a docs/privacidade.md: "
                "rode python3 scripts/render-privacy-page.py",
                file=sys.stderr,
            )
            return 1
        print(f"{shown} confere com docs/privacidade.md")
        return 0
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(page, encoding="utf-8", newline="\n")
    print(f"{shown}: {len(page.encode('utf-8'))} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())

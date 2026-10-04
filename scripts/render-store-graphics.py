#!/usr/bin/env python3
"""Gera os gráficos da ficha do Google Play a partir do ícone adaptativo do app.

Saídas (em fastlane/metadata/android/pt-BR/images/):
  icon.png            512 x 512, PNG de 32 bits (RGBA), sem máscara nem sombra: o Google Play
                      aplica o arredondamento. Mostra a área visível do ícone adaptativo (os 72 dp
                      centrais das camadas de 108 dp), como num launcher com máscara quadrada.
  featureGraphic.png  1024 x 500, PNG de 24 bits (RGB, sem alfa): nome do app, uma frase e o
                      desenho de uma cola com caixas de dígitos vazias (nenhum número ou nome).

As camadas vêm de app/src/main/res/drawable/ic_launcher_{background,foreground}.xml e as cores de
res/values/ic_launcher_colors.xml; o nome do app vem de res/values/strings.xml (app_name). Mudou o
ícone ou o nome? Rode de novo e confira com scripts/check-store-metadata.sh.

Uso:
  pip install pillow cairosvg
  python3 scripts/render-store-graphics.py [--font-regular TTF --font-bold TTF] [--preview DIR]

As imagens versionadas foram geradas com Roboto (Google Fonts, licença SIL OFL 1.1). Sem
--font-*, o script procura Roboto pelo fontconfig e cai para DejaVu Sans.
--preview DIR grava também prévias que não vão para a loja: o ícone com máscara redonda e em
48 px, e a camada monocromática como ícone temático do Android 13+.
"""

from __future__ import annotations

import argparse
import io
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

try:
    import cairosvg
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover - mensagem para quem roda sem as dependências
    sys.exit("Faltam dependencias: pip install pillow cairosvg")

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
OUT = ROOT / "fastlane/metadata/android/pt-BR/images"
ANDROID = "{http://schemas.android.com/apk/res/android}"

DEFAULT_TAGLINE = "Monte sua cola eleitoral\npara levar no papel"


# --------------------------------------------------------------------------- VectorDrawable -> SVG

def load_colors() -> dict[str, str]:
    """Reads @color values used by the launcher layers (only ic_launcher_colors.xml)."""
    colors: dict[str, str] = {}
    tree = ET.parse(RES / "values/ic_launcher_colors.xml")
    for node in tree.getroot().iter("color"):
        colors[node.attrib["name"]] = (node.text or "").strip()
    return colors


def parse_color(value: str, colors: dict[str, str]) -> tuple[str, float]:
    """Android color (#RGB, #ARGB, #RRGGBB, #AARRGGBB or @color/x) -> (SVG rgb(), alpha 0..1)."""
    if value.startswith("@color/"):
        name = value.removeprefix("@color/")
        if name not in colors:
            raise SystemExit(f"Cor {value} nao esta em values/ic_launcher_colors.xml")
        value = colors[name]
    hexdigits = value.lstrip("#")
    if len(hexdigits) in (3, 4):
        hexdigits = "".join(ch * 2 for ch in hexdigits)
    if len(hexdigits) == 6:
        hexdigits = "FF" + hexdigits
    if len(hexdigits) != 8 or not re.fullmatch(r"[0-9a-fA-F]{8}", hexdigits):
        raise SystemExit(f"Cor invalida: {value}")
    a, r, g, b = (int(hexdigits[i:i + 2], 16) for i in range(0, 8, 2))
    return f"rgb({r},{g},{b})", a / 255


def vector_to_svg(path: Path, colors: dict[str, str]) -> tuple[float, float, str]:
    """Converts a VectorDrawable (path + group; no clip-path, no gradients) to SVG markup."""
    root = ET.parse(path).getroot()
    if root.tag != "vector":
        raise SystemExit(f"{path} nao e um <vector>")
    width = float(root.attrib[ANDROID + "viewportWidth"])
    height = float(root.attrib[ANDROID + "viewportHeight"])

    def attr(node: ET.Element, name: str, default: str | None = None) -> str | None:
        return node.attrib.get(ANDROID + name, default)

    def convert(node: ET.Element) -> str:
        if node.tag == "path":
            style = []
            fill = attr(node, "fillColor")
            if fill:
                rgb, alpha = parse_color(fill, colors)
                alpha *= float(attr(node, "fillAlpha", "1"))
                style.append(f'fill="{rgb}" fill-opacity="{alpha:.4f}"')
            else:
                style.append('fill="none"')
            if attr(node, "fillType", "nonZero") == "evenOdd":
                style.append('fill-rule="evenodd"')
            stroke = attr(node, "strokeColor")
            stroke_width = float(attr(node, "strokeWidth", "0"))
            if stroke and stroke_width > 0:
                rgb, alpha = parse_color(stroke, colors)
                alpha *= float(attr(node, "strokeAlpha", "1"))
                style.append(
                    f'stroke="{rgb}" stroke-opacity="{alpha:.4f}" stroke-width="{stroke_width}" '
                    f'stroke-linecap="{attr(node, "strokeLineCap", "butt")}" '
                    f'stroke-linejoin="{attr(node, "strokeLineJoin", "miter")}" '
                    f'stroke-miterlimit="{attr(node, "strokeMiterLimit", "4")}"'
                )
            return f'<path d="{attr(node, "pathData")}" {" ".join(style)}/>'
        if node.tag == "group":
            px, py = float(attr(node, "pivotX", "0")), float(attr(node, "pivotY", "0"))
            tx, ty = float(attr(node, "translateX", "0")), float(attr(node, "translateY", "0"))
            sx, sy = float(attr(node, "scaleX", "1")), float(attr(node, "scaleY", "1"))
            rotation = float(attr(node, "rotation", "0"))
            # Same order as android.graphics.drawable.VectorDrawable.VGroup.updateLocalMatrix().
            transform = (
                f"translate({px + tx},{py + ty}) rotate({rotation}) scale({sx},{sy}) translate({-px},{-py})"
            )
            children = "".join(convert(child) for child in node)
            return f'<g transform="{transform}">{children}</g>'
        raise SystemExit(f"{path}: elemento <{node.tag}> nao suportado pelo conversor")

    return width, height, "".join(convert(child) for child in root)


def launcher_layers() -> tuple[str, str, str]:
    colors = load_colors()
    _, _, background = vector_to_svg(RES / "drawable/ic_launcher_background.xml", colors)
    _, _, foreground = vector_to_svg(RES / "drawable/ic_launcher_foreground.xml", colors)
    _, _, monochrome = vector_to_svg(RES / "drawable/ic_launcher_monochrome.xml", colors)
    return background, foreground, monochrome


def svg_to_image(svg: str, width: int, height: int) -> Image.Image:
    png = cairosvg.svg2png(bytestring=svg.encode("utf-8"), output_width=width, output_height=height)
    return Image.open(io.BytesIO(png)).convert("RGBA")


# --------------------------------------------------------------------------------------- outputs

def render_icon(size: int = 512) -> Image.Image:
    background, foreground, _ = launcher_layers()
    # 72 dp centrais das camadas de 108 dp: a área que um launcher mostra.
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
        f"{background}{foreground}</svg>"
    )
    return svg_to_image(svg, size, size)


def cola_illustration_svg(colors: dict[str, str]) -> str:
    """A paper "cola" with the 2026 general-election rows (empty digit boxes) and the check seal."""
    paper, _ = parse_color("@color/ic_launcher_paper", colors)
    field, _ = parse_color("@color/ic_launcher_field", colors)
    ink, _ = parse_color("@color/ic_launcher_ink", colors)
    on_ink, _ = parse_color("@color/ic_launcher_on_ink", colors)
    # Urna 2026: Dep. federal (4), Dep. estadual/distrital (5), Senador x2 (3), Governador (2),
    # Presidente (2). Os rótulos são barras: nenhum texto, número ou nome.
    rows = [4, 5, 3, 3, 2, 2]
    sheet_w, sheet_h = 272, 350
    margin, box_w, box_h, gap, row_step = 26, 22, 28, 5, 42
    parts = [
        f'<rect x="5" y="9" width="{sheet_w}" height="{sheet_h}" rx="16" fill="black" fill-opacity="0.22"/>',
        f'<rect x="0" y="0" width="{sheet_w}" height="{sheet_h}" rx="16" fill="{paper}"/>',
        f'<rect x="{margin}" y="26" width="150" height="14" rx="7" fill="{ink}" fill-opacity="0.85"/>',
    ]
    y = 62
    for digits in rows:
        parts.append(f'<rect x="{margin}" y="{y + 9}" width="64" height="10" rx="5" fill="{field}"/>')
        x = sheet_w - margin - digits * box_w - (digits - 1) * gap
        for _ in range(digits):
            parts.append(
                f'<rect x="{x}" y="{y}" width="{box_w}" height="{box_h}" rx="4" fill="none" '
                f'stroke="{ink}" stroke-opacity="0.55" stroke-width="2.5"/>'
            )
            x += box_w + gap
        y += row_step
    # Selo no canto inferior direito, abaixo da última linha (que termina em y = 300).
    seal_x, seal_y = sheet_w - 6, sheet_h - 6
    parts.append(f'<circle cx="{seal_x}" cy="{seal_y}" r="36" fill="{paper}"/>')
    parts.append(f'<circle cx="{seal_x}" cy="{seal_y}" r="30" fill="{ink}"/>')
    parts.append(
        f'<path d="M{seal_x - 13},{seal_y + 1} l9,9 l17,-18" fill="none" stroke="{on_ink}" '
        f'stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>'
    )
    return "".join(parts)


def find_font(weight: str, explicit: str | None) -> str:
    if explicit:
        return explicit
    if shutil.which("fc-match"):
        result = subprocess.run(
            ["fc-match", "-f", "%{family}|%{file}", f"Roboto:weight={weight}"],
            capture_output=True, text=True, check=False,
        )
        family, _, file = result.stdout.partition("|")
        if family.startswith("Roboto") and file:
            return file
    fallback = "DejaVuSans-Bold.ttf" if weight == "bold" else "DejaVuSans.ttf"
    for base in ("/usr/share/fonts/truetype/dejavu", "/Library/Fonts", "C:/Windows/Fonts"):
        candidate = Path(base) / fallback
        if candidate.exists():
            print(f"Aviso: Roboto nao encontrada, usando {candidate}", file=sys.stderr)
            return str(candidate)
    raise SystemExit("Nenhuma fonte encontrada; passe --font-regular e --font-bold")


def app_name() -> str:
    tree = ET.parse(RES / "values/strings.xml")
    for node in tree.getroot().iter("string"):
        if node.attrib.get("name") == "app_name":
            return "".join(node.itertext()).strip()
    raise SystemExit("app_name nao encontrado em values/strings.xml")


def wrap(text: str, font: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    """Greedy word wrap; "\\n" in the text forces a line break."""
    lines: list[str] = []
    for paragraph in text.split("\n"):
        current = ""
        for word in paragraph.split():
            candidate = f"{current} {word}".strip()
            if font.getlength(candidate) <= max_width or not current:
                current = candidate
            else:
                lines.append(current)
                current = word
        if current:
            lines.append(current)
    return lines


def render_feature_graphic(tagline: str, regular: str, bold: str) -> Image.Image:
    colors = load_colors()
    background, _ = parse_color("@color/ic_launcher_background", colors)
    width, height = 1024, 500
    sheet = cola_illustration_svg(colors)
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}"><rect width="{width}" height="{height}" fill="{background}"/>'
        f'<g transform="translate(672,64) rotate(3 136 175)">{sheet}</g></svg>'
    )
    image = svg_to_image(svg, width, height).convert("RGB")
    draw = ImageDraw.Draw(image)
    on_background = (255, 255, 255)
    secondary = (0xEC, 0xEF, 0xF1)
    title_font = ImageFont.truetype(bold, 76)
    tagline_font = ImageFont.truetype(regular, 38)
    left, max_text = 72, 540
    title = app_name()
    title_lines = wrap(title, title_font, max_text)
    tagline_lines = wrap(tagline, tagline_font, max_text)
    title_h, tagline_h, spacing = 90, 50, 26
    block = len(title_lines) * title_h + spacing + len(tagline_lines) * tagline_h
    y = (height - block) // 2
    for line in title_lines:
        draw.text((left, y), line, font=title_font, fill=on_background)
        y += title_h
    y += spacing
    for line in tagline_lines:
        draw.text((left, y), line, font=tagline_font, fill=secondary)
        y += tagline_h
    return image


def render_previews(directory: Path) -> None:
    """Launcher-like previews (round mask, 48 dp) and a themed-icon simulation; not for the store."""
    directory.mkdir(parents=True, exist_ok=True)
    background, foreground, monochrome = launcher_layers()

    def masked(content: str) -> str:
        return (
            '<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108">'
            '<defs><clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath></defs>'
            f'<g clip-path="url(#m)">{content}</g></svg>'
        )

    round_icon = svg_to_image(masked(background + foreground), 432, 432)
    round_icon.save(directory / "launcher-round-432.png")
    round_icon.resize((144, 144), Image.LANCZOS).save(directory / "launcher-round-48dp-xxxhdpi.png")
    # Ícone temático: o alfa da camada monocromática pintado com uma cor de tema sobre fundo claro.
    glyph_alpha = svg_to_image(masked(monochrome), 432, 432).getchannel("A")
    themed = svg_to_image(masked('<rect width="108" height="108" fill="#D6E8DA"/>'), 432, 432)
    themed.paste(Image.new("RGBA", themed.size, (0x1D, 0x35, 0x22, 255)), (0, 0), glyph_alpha)
    themed.save(directory / "themed-icon-preview.png")


def save_png(image: Image.Image, path: Path, mode: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.convert(mode).save(path, format="PNG", optimize=True)
    print(f"{path.relative_to(ROOT)}: {image.width}x{image.height} {mode}, {path.stat().st_size} bytes")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--font-regular", help="TTF regular (padrao: Roboto pelo fontconfig)")
    parser.add_argument("--font-bold", help="TTF negrito (padrao: Roboto Bold pelo fontconfig)")
    parser.add_argument(
        "--tagline", default=DEFAULT_TAGLINE, help='frase da imagem de destaque ("\\n" quebra a linha)'
    )
    parser.add_argument("--preview", type=Path, help="diretorio para previas (nao versionar)")
    args = parser.parse_args()

    save_png(render_icon(), OUT / "icon.png", "RGBA")
    regular = find_font("regular", args.font_regular)
    bold = find_font("bold", args.font_bold)
    tagline = args.tagline.replace("\\n", "\n")
    save_png(render_feature_graphic(tagline, regular, bold), OUT / "featureGraphic.png", "RGB")
    if args.preview:
        render_previews(args.preview)
        print(f"Previas em {args.preview}")


if __name__ == "__main__":
    main()

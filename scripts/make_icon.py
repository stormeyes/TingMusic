#!/usr/bin/env python3
"""Generate the TingMusic app icon as a 1024x1024 PNG.

Run: python3 scripts/make_icon.py
Output: src-tauri/icons/icon.png (and resized variants for Tauri bundles).
"""

from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path

S = 1024                 # full canvas
INSET = 100              # transparent padding around the squircle so it
                         # matches other macOS dock icons (~80% tile)
TILE = S - 2 * INSET     # 824 — the visible squircle size
ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src-tauri" / "icons"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def squircle_mask(canvas, tile, inset, radius_ratio=0.2237):
    """Inset macOS-style rounded square mask centered on the canvas."""
    r = int(tile * radius_ratio)
    mask = Image.new("L", (canvas, canvas), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle(
        (inset, inset, canvas - inset - 1, canvas - inset - 1),
        radius=r,
        fill=255,
    )
    return mask


def vertical_gradient(size, top_rgb, bot_rgb):
    """Smooth vertical gradient as a paste-able RGBA image."""
    img = Image.new("RGB", (1, size))
    p = img.load()
    for y in range(size):
        t = y / (size - 1)
        p[0, y] = (
            int(top_rgb[0] + (bot_rgb[0] - top_rgb[0]) * t),
            int(top_rgb[1] + (bot_rgb[1] - top_rgb[1]) * t),
            int(top_rgb[2] + (bot_rgb[2] - top_rgb[2]) * t),
        )
    return img.resize((size, size))


def draw_vinyl(canvas, cx, cy, outer_r):
    """Draw a stylized vinyl record at (cx, cy) with given outer radius."""
    # Disc body — slight radial darkening at the rim
    d = ImageDraw.Draw(canvas)

    # Soft outer halo (subtle glow)
    halo = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hd.ellipse(
        (cx - outer_r - 24, cy - outer_r - 24, cx + outer_r + 24, cy + outer_r + 24),
        fill=(0, 0, 0, 90),
    )
    halo = halo.filter(ImageFilter.GaussianBlur(20))
    canvas.alpha_composite(halo)

    # Disc body
    d.ellipse(
        (cx - outer_r, cy - outer_r, cx + outer_r, cy + outer_r),
        fill=(8, 8, 10, 255),
    )

    # Specular highlight (upper-left)
    spec = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(spec)
    spec_r = int(outer_r * 0.55)
    sx = cx - int(outer_r * 0.30)
    sy = cy - int(outer_r * 0.30)
    sd.ellipse(
        (sx - spec_r, sy - spec_r, sx + spec_r, sy + spec_r),
        fill=(255, 255, 255, 60),
    )
    spec = spec.filter(ImageFilter.GaussianBlur(40))
    canvas.alpha_composite(spec)

    # Concentric grooves
    groove_color = (255, 255, 255, 22)
    for ratio in (0.95, 0.82, 0.69, 0.56, 0.43):
        gr = int(outer_r * ratio)
        d.ellipse(
            (cx - gr, cy - gr, cx + gr, cy + gr),
            outline=groove_color,
            width=max(1, int(outer_r * 0.005)),
        )

    # Red label
    label_r = int(outer_r * 0.30)
    d.ellipse(
        (cx - label_r, cy - label_r, cx + label_r, cy + label_r),
        fill=(211, 58, 49, 255),
    )

    # Subtle highlight on the label (top-left)
    hl = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    hd2 = ImageDraw.Draw(hl)
    hl_r = int(label_r * 0.6)
    hx = cx - int(label_r * 0.25)
    hy = cy - int(label_r * 0.30)
    hd2.ellipse(
        (hx - hl_r, hy - hl_r, hx + hl_r, hy + hl_r),
        fill=(255, 255, 255, 55),
    )
    hl = hl.filter(ImageFilter.GaussianBlur(30))
    canvas.alpha_composite(hl)

    # Spindle hole
    hole_r = int(outer_r * 0.04)
    d.ellipse(
        (cx - hole_r, cy - hole_r, cx + hole_r, cy + hole_r),
        fill=(8, 8, 10, 255),
    )


def make_icon(size=S):
    # Background plate (will be masked)
    bg = vertical_gradient(size, (60, 60, 70), (24, 22, 30)).convert("RGBA")

    # Add a subtle vignette
    vignette = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    vd = ImageDraw.Draw(vignette)
    vd.ellipse((-size * 0.2, -size * 0.2, size * 1.2, size * 1.2),
               fill=(0, 0, 0, 0))
    vd.rectangle((0, 0, size, size), fill=(0, 0, 0, 60))
    vd = ImageDraw.Draw(vignette)
    # Punch a brighter circle in the upper-left for highlight
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse(
        (int(-size * 0.2), int(-size * 0.3), int(size * 0.7), int(size * 0.4)),
        fill=(255, 255, 255, 28),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    bg.alpha_composite(glow)

    # Vinyl sits inside the inset tile; size it relative to the tile so the
    # disc-to-padding ratio stays consistent.
    cx, cy = size // 2, size // 2
    outer_r = int(TILE * 0.32)
    draw_vinyl(bg, cx, cy, outer_r)

    # Apply inset rounded-square mask
    mask = squircle_mask(size, TILE, INSET)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(bg, mask=mask)
    return out


def main():
    icon = make_icon(S)
    main_path = OUT_DIR / "icon.png"
    icon.save(main_path, "PNG")
    print(f"wrote {main_path} ({S}x{S})")

    # Common Tauri bundle sizes (best-effort; cargo tauri icon will generate
    # the platform-specific .icns / .ico from these later).
    for size in (32, 64, 128, 256, 512):
        scaled = icon.resize((size, size), Image.LANCZOS)
        p = OUT_DIR / f"{size}x{size}.png"
        scaled.save(p, "PNG")
        print(f"wrote {p}")


if __name__ == "__main__":
    main()

"""Create encoded-sRGB float32 probes and desktop C ABI references."""
from pathlib import Path
import os
import argparse, json, subprocess
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
DEFAULT_SOURCE = HERE.parent / "film-practice/inputs/kodim01.png"
PARAMS = {
    "film_stock": "kodak_portra_400", "print_stock": "kodak_supra_endura",
    "input_color_space": "sRGB", "input_cctf_decoding": True,
    "output_color_space": "sRGB", "output_cctf_encoding": True,
    "auto_exposure": False, "exposure_compensation_ev": 0.0,
    "print_exposure": 1.0, "film_format_mm": 35.0,
    "grain_active": False, "glare_active": False, "halation_active": False,
    "scene_latitude_active": False,
}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    ap.add_argument("--out", type=Path, default=HERE / "samples")
    ap.add_argument("--desktop-driver", type=Path, default=HERE.parent / "engine-practice/build-msvc/spk_render_fixture.exe")
    ap.add_argument("--resources", type=Path, default=HERE.parent / "film-practice/resources")
    a = ap.parse_args(); a.source = a.source.resolve(); a.out.mkdir(parents=True, exist_ok=True)
    if not a.source.exists(): raise SystemExit(f"source not found: {a.source}")
    rgb = np.asarray(Image.open(a.source).convert("RGB"), dtype=np.float32) / 255.0
    for long_side in (1024, 2048):
        scale = long_side / max(rgb.shape[:2]); h, w = max(1, round(rgb.shape[0]*scale)), max(1, round(rgb.shape[1]*scale))
        # Resize encoded sRGB values for a speed probe; color parity uses the original size.
        image = Image.fromarray(np.rint(rgb * 255).astype(np.uint8)).resize((w, h), Image.Resampling.LANCZOS)
        arr = np.asarray(image, dtype=np.float32) / 255.0
        stem = f"kodim01_{long_side}"
        inp, params, out, report = [a.out / f"{stem}.{ext}" for ext in ("f32", "json", "rgba16", "report.json")]
        arr.tofile(inp); params.write_text(json.dumps(PARAMS, indent=2) + "\n", encoding="utf-8")
        if a.desktop_driver.exists() and a.resources.exists():
            # The existing Windows fixture rejects non-UTF-8 paths. Map the
            # workspace to an ASCII drive for this reference-only invocation.
            drive = "N:"
            mapped = False
            if os.name == "nt":
                subprocess.run(["subst", drive, str(HERE.parents[1])], check=True, stdout=subprocess.DEVNULL)
                mapped = True
            try:
                def short(p):
                    return str(Path(drive + "/") / p.relative_to(HERE.parents[1])) if mapped else str(p)
                cmd = [short(a.desktop_driver), short(a.resources), short(inp), str(w), str(h), short(params), short(out), short(report)]
                subprocess.run(cmd, check=True)
            finally:
                if mapped: subprocess.run(["subst", drive, "/D"], check=True, stdout=subprocess.DEVNULL)
        print(f"{stem}: {w}x{h}, encoded sRGB float32; color comparison should use original-size input")

if __name__ == "__main__": main()

# Pouch Studio

Interactive 3D preview of the 16 × 12-inch black laptop pouch. Drag to rotate, scroll or pinch to zoom, and use Front, Back, Side, Detail, and 3D view controls.

All model textures and viewer dependencies are included. GitHub Pages serves the main branch root. No build step is required to view the site.

## Revised model

The revised character sheet controls design placement. The two pouch photographs guide textile grain, edge folds, stitching, and zipper construction; the user's final correction places stripes on the front only and the horizontal seam on the back only.

- Body: 16 × 12 inches, approximately 1.5 inches deep at its padded center (406.4 × 304.8 × 38.1 mm), tapering at the bound edges.
- Tricolor band: 1 inch combined width, three equal cyan/orange/royal-blue stripes, on the right of the front face only.
- Front crest: 3 × 2.3-inch artwork area, extracted from the supplied measurement reference without measurement arrows.
- Front text: 4.5 inches wide, set on two lines. Text is recreated with Arial (or Liberation Sans on Linux).
- Horizontal back construction seam, fine stitching, silver slider and open pull loop, dark zipper coils and woven pull extension.

Stripe inset (0.7 inch), crest inset (0.65 inch), seam height, folds, and hardware are visual estimates; this is a closed-pouch design preview, not a manufacturing pattern or interior simulation.

## Rebuild the model

Source references are preserved in `references/`. `scripts/build_model.py` creates geometry, image-derived fabric and artwork textures, a micro-normal map, and the self-contained `Laptop_Pouch.glb`. The script uses meters and fixed sampling for reproducible geometry. It needs Python 3.11+, NumPy, Pillow, and Arial on Windows or Liberation Sans on Linux. Fonts are loaded from the system and are not redistributed.

```sh
python -m pip install -r scripts/requirements.txt
python scripts/build_model.py
python scripts/validate_model.py
python -m http.server 8765
```

Open http://localhost:8765. Three.js is distributed under the MIT license in `vendor/THREE-LICENSE.txt`.

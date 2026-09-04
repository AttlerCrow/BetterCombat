// Turns a Blender render into the 32x32 icon the wheel blits.
//
//   node downscale.js <inDir> <outDir> [size]
//
// Two things the naive version gets wrong. Averaging straight RGBA drags the transparent
// background's colour into every edge pixel, so the silhouette gets a pale halo - the average has to
// be alpha-weighted. And the renderer's own outline is about two pixels wide, which at 8:1 survives
// as roughly a quarter of one pixel: invisible. So the outline is redrawn here, at icon scale, where
// it is what keeps a grey figure legible on the blue of a selected slot.

const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./png');

const inDir = process.argv[2];
const outDir = process.argv[3];
const SIZE = Number(process.argv[4] || 32);

const OUTLINE = [22, 20, 20];

fs.mkdirSync(outDir, { recursive: true });

function box(img, size) {
    const out = new Uint8Array(size * size * 4);
    const sx = img.width / size;
    const sy = img.height / size;
    for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
            let r = 0, g = 0, b = 0, a = 0, n = 0;
            const x0 = Math.floor(x * sx), x1 = Math.max(x0 + 1, Math.floor((x + 1) * sx));
            const y0 = Math.floor(y * sy), y1 = Math.max(y0 + 1, Math.floor((y + 1) * sy));
            for (let yy = y0; yy < y1; yy++) {
                for (let xx = x0; xx < x1; xx++) {
                    const i = (yy * img.width + xx) * 4;
                    const alpha = img.pixels[i + 3] / 255;
                    // Weight colour by coverage, or the transparent background bleeds into the edge.
                    r += img.pixels[i] * alpha;
                    g += img.pixels[i + 1] * alpha;
                    b += img.pixels[i + 2] * alpha;
                    a += alpha;
                    n++;
                }
            }
            const o = (y * size + x) * 4;
            if (a > 0) {
                out[o] = Math.round(r / a);
                out[o + 1] = Math.round(g / a);
                out[o + 2] = Math.round(b / a);
            }
            out[o + 3] = Math.round((a / n) * 255);
        }
    }
    return out;
}

/**
 * Mild unsharp mask on colour only.
 *
 * <p>An 8:1 box filter is a blur by definition, and at 32px the creases it softens are the only
 * thing telling an arm from the torso behind it. Alpha is left alone - sharpening coverage would
 * chew the silhouette's edge into speckle.
 */
function sharpen(pixels, size, amount = 0.55) {
    const before = pixels.slice();
    const at = (x, y, c) => {
        const cx = Math.min(size - 1, Math.max(0, x));
        const cy = Math.min(size - 1, Math.max(0, y));
        return before[(cy * size + cx) * 4 + c];
    };
    for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
            const i = (y * size + x) * 4;
            if (before[i + 3] < 8) continue;
            for (let c = 0; c < 3; c++) {
                const blur = (at(x - 1, y, c) + at(x + 1, y, c) + at(x, y - 1, c) + at(x, y + 1, c)) / 4;
                const value = at(x, y, c) + (at(x, y, c) - blur) * amount;
                pixels[i + c] = Math.max(0, Math.min(255, Math.round(value)));
            }
        }
    }
}

/** A hard rim just outside the figure, so it never dissolves into a light background. */
function rim(pixels, size) {
    const before = pixels.slice();
    const alphaAt = (x, y) => {
        if (x < 0 || y < 0 || x >= size || y >= size) return 0;
        return before[(y * size + x) * 4 + 3];
    };
    for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
            const i = (y * size + x) * 4;
            if (alphaAt(x, y) >= 110) continue;
            let neighbour = 0;
            for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1], [-1, -1], [1, -1], [-1, 1], [1, 1]]) {
                neighbour = Math.max(neighbour, alphaAt(x + dx, y + dy));
            }
            if (neighbour < 110) continue;
            // Keep whatever coverage the edge already had, so the rim follows the anti-aliasing
            // instead of squaring the shape off.
            const a = Math.max(before[i + 3], 200);
            pixels[i] = OUTLINE[0];
            pixels[i + 1] = OUTLINE[1];
            pixels[i + 2] = OUTLINE[2];
            pixels[i + 3] = a;
        }
    }
}

for (const file of fs.readdirSync(inDir).filter(f => f.endsWith('.png'))) {
    const img = decode(path.join(inDir, file));
    const pixels = box(img, SIZE);
    sharpen(pixels, SIZE);
    rim(pixels, SIZE);
    fs.writeFileSync(path.join(outDir, file), encode({ width: SIZE, height: SIZE, pixels }));
    console.log('icon', file);
}

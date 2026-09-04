// Tiles PNGs onto the two backgrounds a wheel slot actually uses, so a candidate can be judged the
// way a player sees it rather than as a file on white.
//
//   node sheet.js <dir> <out.png> [cellScale] [cols]

const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./png');

const dir = process.argv[2];
const outFile = process.argv[3];
const cell = Number(process.argv[4] || 128);
const cols = Number(process.argv[5] || 6);
const GAP = 8;
const LABEL = 12;

const files = fs.readdirSync(dir).filter(f => f.endsWith('.png')).sort();
const rows = Math.ceil(files.length / cols) * 2;
const W = cols * (cell + GAP) + GAP;
const H = rows * (cell + GAP + LABEL) + GAP;
const out = new Uint8Array(W * H * 4);

function set(x, y, r, g, b) {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const i = (y * W + x) * 4;
    out[i] = r; out[i + 1] = g; out[i + 2] = b; out[i + 3] = 255;
}

for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) set(x, y, 38, 38, 44);

// 3x5 digits and a few letters, enough to label a cell with its index.
const GLYPHS = {
    '0': ['###', '# #', '# #', '# #', '###'], '1': ['..#', '..#', '..#', '..#', '..#'],
    '2': ['###', '..#', '###', '#..', '###'], '3': ['###', '..#', '###', '..#', '###'],
    '4': ['# #', '# #', '###', '..#', '..#'], '5': ['###', '#..', '###', '..#', '###'],
    '6': ['###', '#..', '###', '# #', '###'], '7': ['###', '..#', '..#', '..#', '..#'],
    '8': ['###', '# #', '###', '# #', '###'], '9': ['###', '# #', '###', '..#', '###'],
    '_': ['...', '...', '...', '...', '###'], '.': ['...', '...', '...', '...', '.#.'],
};

function label(text, x, y) {
    let cx = x;
    for (const ch of text.toLowerCase()) {
        const glyph = GLYPHS[ch];
        if (glyph) {
            glyph.forEach((row, dy) => [...row].forEach((c, dx) => {
                if (c === '#') { set(cx + dx * 2, y + dy * 2, 220, 220, 220); set(cx + dx * 2 + 1, y + dy * 2, 220, 220, 220);
                                 set(cx + dx * 2, y + dy * 2 + 1, 220, 220, 220); set(cx + dx * 2 + 1, y + dy * 2 + 1, 220, 220, 220); }
            }));
        }
        cx += 8;
    }
}

function blend(x, y, r, g, b, a) {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const i = (y * W + x) * 4;
    const t = a / 255;
    out[i] = Math.round(out[i] * (1 - t) + r * t);
    out[i + 1] = Math.round(out[i + 1] * (1 - t) + g * t);
    out[i + 2] = Math.round(out[i + 2] * (1 - t) + b * t);
    out[i + 3] = 255;
}

files.forEach((file, index) => {
    const img = decode(path.join(dir, file));
    const scale = cell / img.width;
    [0, 1].forEach(variant => {
        const col = index % cols;
        const row = Math.floor(index / cols) * 2 + variant;
        const ox = GAP + col * (cell + GAP);
        const oy = GAP + row * (cell + GAP + LABEL);
        const plate = variant === 0 ? [10, 10, 12] : [47, 111, 237];
        for (let y = 0; y < cell; y++) for (let x = 0; x < cell; x++) set(ox + x, oy + y, plate[0], plate[1], plate[2]);
        for (let y = 0; y < cell; y++) {
            for (let x = 0; x < cell; x++) {
                const sx = Math.floor(x / scale), sy = Math.floor(y / scale);
                const i = (sy * img.width + sx) * 4;
                const a = img.pixels[i + 3];
                if (a === 0) continue;
                blend(ox + x, oy + y, img.pixels[i], img.pixels[i + 1], img.pixels[i + 2], a);
            }
        }
        if (variant === 1) label(file.replace('.png', ''), ox, oy + cell + 2);
    });
});

fs.writeFileSync(outFile, encode({ width: W, height: H, pixels: out }));
console.log('sheet', W + 'x' + H, files.join(' '));

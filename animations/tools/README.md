# Emote icons

The wheel and the editor blit a 32×32 PNG per emote from
`common/src/main/resources/assets/nightfantasy/textures/gui/emote/<id>.png`. The server never names
those files — `EmoteRegistry` defaults an emote's icon to `nightfantasy:emote/<id>` — so **shipping
the PNG named after the emote is the whole wiring**. A file that is not there falls back to
`unknown.png` rather than to the missing-texture chequerboard, which is why an emote can be added to
config before its icon exists.

The icons are renders of the pose itself, taken from the same `.blend` that produced the animation.
That is the point: the icon cannot drift from what the emote actually looks like.

## Regenerating

1. In Blender, run `render_emote_icons.py` (text editor, or through the blender MCP). It opens each
   `emote_<id>.blend`, poses it at one chosen frame, and writes a 256px render to `out/`.
2. Shrink them to the shipped icons:

   ```
   node icons_downscale.js out ../../common/src/main/resources/assets/nightfantasy/textures/gui/emote 32
   ```

3. Look at them the way a player will, on both slot backgrounds, before believing they are done:

   ```
   node icons_sheet.js ../../common/src/main/resources/assets/nightfantasy/textures/gui/emote sheet.png 128 6
   ```

`unknown.png` is drawn by hand, not rendered — it stands for "no icon", so there is no pose to shoot.

## Adding an emote

Add a row to `PLAN` in `render_emote_icons.py`. Choosing the frame is the only real work: it has to
be a moment that reads with no motion and no label — the held part of a loop, or the extreme of a
gesture. The frame an animation ends on is almost always wrong, because most of them return to rest,
and a rest pose is just a figure standing there.

## Why the renders look the way they do

Each of these was a wrong version first.

- **The camera is level.** A downward tilt looks better in a viewport but rotates the ground in
  frame, and every seated pose then reads as a figure toppling sideways.
- **Seated poses are shot in profile, upright ones three-quarter.** Straight-on, a sit is a stack of
  boxes: the legs point at the camera and vanish.
- **`ref_block` is shown only for poses that sit on something.** Its top face is flush with the
  floor, so including it under a ground sit makes that sit look like a ledge sit.
- **Creases and shadows are pushed past what looks right at full size.** An 8:1 downscale is a blur;
  whatever separates an arm from the torso has to survive it, and at 256px it looks overdone.
- **The outline is redrawn at icon scale, not taken from the render.** Workbench's own outline is
  about two pixels wide, which after the downscale is a quarter of one — invisible. Without a real
  rim the grey figure dissolves into the blue of a selected slot.

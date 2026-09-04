# Renders the wheel/editor icon for every emote, straight from the .blend the animation lives in.
#
# Run inside Blender (text editor, or through the blender MCP). It writes 256px PNGs to
# `animations/tools/out/`; `icons_downscale.js` then turns those into the 32x32 files the mod ships
# in `assets/nightfantasy/textures/gui/emote/`.
#
# Adding an emote means adding one row to PLAN below. The three columns that matter:
#
#   frame   a moment where the pose reads on its own. The held part of a loop, or the extreme of a
#           gesture - never the rest pose the animation returns to, which is just a figure standing.
#   camera  SIDE for anything seated or lying (a front view foreshortens the legs into a blob),
#           QUARTER for anything upright.
#   block   whether to show `ref_block`. Only for poses that sit ON something: with the block, a
#           ground sit reads as a ledge sit, which is the opposite of helpful.
#
# Both cameras are level on purpose. Tilting down looks nicer in a viewport but rotates the ground in
# frame, and a seated pose then reads as a figure toppling over.

import bpy
import os
from mathutils import Vector

HERE = os.path.dirname(bpy.data.filepath) or os.getcwd()
ANIM = os.path.abspath(os.path.join(HERE, ".."))
OUT = os.path.join(os.path.dirname(__file__) if "__file__" in dir() else HERE, "out")

SIDE = (-1.0, 0.06, 0.0)      # profile, a touch of front so the face is not a flat plane
QUARTER = (-0.62, 0.78, 0.0)  # three-quarter, from the character's front-right

# emote, frame, camera, show the block it sits on
PLAN = [
    ("wave",       33,  QUARTER, False),
    ("wave_cute",  29,  QUARTER, False),
    ("conga",      55,  SIDE,    False),
    ("conga_arms", 62,  QUARTER, False),
    ("sit",        129, SIDE,    False),
    ("sit_ledge",  135, SIDE,    True),
    ("sit_throne", 135, SIDE,    True),
    ("sit_tree",   136, SIDE,    False),
    ("squat",      132, SIDE,    False),
    ("afk_sleep",  101, SIDE,    False),
]


def prepare(show_block, res=256):
    """Workbench, because the icon wants a readable shape rather than a lit render."""
    sc = bpy.context.scene
    sc.render.engine = 'BLENDER_WORKBENCH'
    sh = sc.display.shading
    sh.light = 'STUDIO'
    sh.color_type = 'OBJECT'
    sh.show_object_outline = True
    sh.object_outline_color = (0.05, 0.04, 0.04)
    # Creases pushed hard, plus a shadow pass. At 32px this is the whole difference between an
    # elbow reading as an elbow and melting into the torso behind it.
    sh.show_cavity = True
    sh.cavity_type = 'BOTH'
    sh.curvature_ridge_factor = 2.0
    sh.curvature_valley_factor = 2.0
    sh.cavity_ridge_factor = 1.5
    sh.cavity_valley_factor = 2.0
    sh.show_shadows = True
    sh.shadow_intensity = 0.35
    sc.display.render_aa = '32'
    sc.render.film_transparent = True
    sc.render.resolution_x = res
    sc.render.resolution_y = res
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA'

    for o in bpy.data.objects:
        if o.type == 'MESH':
            o.color = (0.82, 0.82, 0.84, 1.0)
    # The practice swords belong to the animation, not to the emote being advertised.
    for name in ("wooden_sword_left", "wooden_sword_right"):
        if name in bpy.data.objects:
            bpy.data.objects[name].hide_render = True
    for c in bpy.data.collections:
        if c.name == "reference":
            for o in c.objects:
                keep = show_block and o.name == "ref_block"
                o.hide_render = not keep
                if keep:
                    o.display_type = 'SOLID'
                    o.color = (0.42, 0.43, 0.48, 1.0)   # scenery, so it reads as behind the figure
    return sc


def frame_camera(sc, direction, margin=1.06):
    """Zooms to whatever the pose actually occupies, so every icon fills its 32 pixels."""
    cam = bpy.data.objects["Camera"]
    cam.data.type = 'ORTHO'
    cam.rotation_mode = 'XYZ'
    cam.rotation_euler = Vector(direction).to_track_quat('-Z', 'Y').to_euler()
    sc.camera = cam

    forward = Vector(direction).normalized()
    right = forward.cross(Vector((0, 0, 1))).normalized()
    up = right.cross(forward).normalized()

    dg = bpy.context.evaluated_depsgraph_get()
    pts = []
    for o in bpy.data.objects:
        if o.type == 'MESH' and not o.hide_render:
            ev = o.evaluated_get(dg)
            pts += [ev.matrix_world @ Vector(c) for c in ev.bound_box]

    us = [p.dot(right) for p in pts]
    vs = [p.dot(up) for p in pts]
    ds = [p.dot(forward) for p in pts]
    centre = (right * ((min(us) + max(us)) / 2)
              + up * ((min(vs) + max(vs)) / 2)
              + forward * ((min(ds) + max(ds)) / 2))
    cam.data.ortho_scale = max(max(us) - min(us), max(vs) - min(vs)) * margin
    cam.location = centre - forward * 60.0
    cam.data.clip_start = 0.1
    cam.data.clip_end = 250.0


def main():
    os.makedirs(OUT, exist_ok=True)
    for emote, frame, direction, block in PLAN:
        bpy.ops.wm.open_mainfile(filepath=os.path.join(ANIM, "emote_%s.blend" % emote))
        sc = prepare(block)
        sc.frame_set(frame)
        bpy.context.view_layer.update()
        frame_camera(sc, direction)
        sc.render.filepath = os.path.join(OUT, "%s.png" % emote)
        bpy.ops.render.render(write_still=True)
        print("rendered", emote, "frame", frame)
    print("done ->", OUT)


main()

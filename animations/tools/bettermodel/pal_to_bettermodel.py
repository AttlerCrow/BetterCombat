"""
Puts Better Combat / PlayerAnimationLib emotes on a BetterModel player built from pal_player.bbmodel.

Reads the per-tick poses dumped from the emotes' .blend sources (dumps/<name>.json: each rig object's
rotation matrix and location, frame by frame) and writes them as Blockbench animations on the model's
bones of the same rig: every tick a keyframe, linear.

Why from the .blend and not the exported JSON: the exporter calibrates and flips signs per part so the
game shows what Blender shows; the Blender pose is the pose. Converting it:
  - Blender rig space: Z up, the figure faces +Y, +X is its right. Blockbench: Y up, faces -Z, +X is
    its right. P maps one onto the other; a rotation R becomes P R P^T.
  - Blockbench 5 animation rotations mean what a bone's rotation means (BetterModel reads both the same
    way, ModelMeta.BLOCKBENCH_5), Z-Y-X euler in degrees: R = Rz Ry Rx.
  - Positions: a rig unit is 4 pixels, and BetterModel's player is drawn at 0.9375.
  - Angles are unwrapped tick to tick so nothing turns the long way round between two keys.
Loops run from tick 1 to the last and close on tick 1's pose (the last equals tick 0, so the step
round the loop is the step 0 -> 1); one-shots start at tick 1 and hold their last pose.

  python pal_to_bettermodel.py <model.bbmodel> <out.bbmodel> [name=animation ...]
With no names, every dump is added under its own name. Dumps hold, per tick, each rig object's
transform against its parent (see REST).
"""
import json
import math
import sys
import uuid
from pathlib import Path

HERE = Path(__file__).parent
DUMPS = HERE / "dumps"
UNIT = 4 * 0.9375
P = [[1, 0, 0], [0, 0, 1], [0, -1, 0]]
BONE = {
    "torso": "torso", "torso_bend": "torso_bend", "head": "ph_head",
    "rightArm": "prsa_right_arm", "rightArm_bend": "prfa_right_arm_bend",
    "leftArm": "plsa_left_arm", "leftArm_bend": "plfa_left_arm_bend",
    "rightLeg": "prl_right_leg", "rightLeg_bend": "prfl_right_leg_bend",
    "leftLeg": "pll_left_leg", "leftLeg_bend": "plfl_left_leg_bend",
}
# Each rig object's rest transform against its parent, dumped from animation_template.blend
# (parent.matrix_world^-1 @ matrix_world: the rig is parented with inverse matrices, so an object's
# location alone is not where it sits). Motion is measured from here.
REST = {k: v["loc"] for k, v in json.load(open(DUMPS / "_rest.json")).items()}
ONCE = {"paraglider_open", "paraglider_dash", "paraglider_dash_left", "paraglider_dash_right"}


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def transpose(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def euler_zyx(m):
    """(x, y, z) degrees with m = Rz(z) Ry(y) Rx(x)."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(m[2][0]) < 0.99999:
        a, c = math.atan2(m[2][1], m[2][2]), math.atan2(m[1][0], m[0][0])
    else:
        a, c = math.atan2(-m[1][2], m[1][1]), 0.0
    return [math.degrees(v) for v in (a, b, c)]


def unwrap(prev, cur):
    return [c + 360 * round((p - c) / 360) for p, c in zip(prev, cur)] if prev else cur


def keyframe(channel, t, v):
    return {"channel": channel, "data_points": [{"x": f"{v[0]:.4f}", "y": f"{v[1]:.4f}", "z": f"{v[2]:.4f}"}],
            "uuid": str(uuid.uuid4()), "time": round(t, 4), "color": -1, "interpolation": "linear",
            "bezier_linked": True, "bezier_left_time": [-0.1, -0.1, -0.1], "bezier_left_value": [0, 0, 0],
            "bezier_right_time": [0.1, 0.1, 0.1], "bezier_right_value": [0, 0, 0]}


def animation(dump, name, bones):
    frames = dump["frames"]
    end = dump["end"]
    once = dump["name"] in ONCE or dump["loop"] != "true"
    ticks = list(range(1, end + 1))
    times = [(f - 1) / 20 for f in ticks]
    if not once:
        ticks.append(1)
        times.append(end / 20)
    animators = {}
    for rig, bone in BONE.items():
        rots, locs, prev = [], [], None
        for f, t in zip(ticks, times):
            pose = frames[str(f)][rig]
            r = mul(mul(P, pose["rot"]), transpose(P))
            e = unwrap(prev, euler_zyx(r))
            prev = e
            rots.append(keyframe("rotation", t, e))
            d = [pose["loc"][i] - REST[rig][i] for i in range(3)]
            bb = [sum(P[i][k] * d[k] for k in range(3)) * UNIT for i in range(3)]
            locs.append(keyframe("position", t, bb))
        keys = rots
        if any(abs(float(k["data_points"][0][a])) > 1e-4 for k in locs for a in "xyz"):
            keys = rots + locs
        animators[bones[bone]] = {"name": bone, "type": "bone", "keyframes": keys}
    # A one-shot holds its last pose, so the body does not drop to rest before the next one starts.
    return {"uuid": str(uuid.uuid4()), "name": name, "loop": "hold" if once else "loop", "override": False,
            "length": round(times[-1] if once else end / 20, 4), "snapping": 20, "selected": False,
            "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
            "animators": animators}


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    pairs = [a.split("=", 1) for a in sys.argv[3:]] or [[p.stem, p.stem] for p in sorted(DUMPS.glob("*.json")) if not p.stem.startswith("_")]
    bb = json.load(open(src, encoding="utf-8"))
    bones = {g["name"]: g["uuid"] for g in bb["groups"]}
    missing = [b for b in BONE.values() if b not in bones]
    assert not missing, f"not a pal_player model, missing bones: {missing}"
    names = {a["name"] for a in bb.get("animations", [])}
    for dump_name, anim_name in pairs:
        dump = json.load(open(DUMPS / f"{dump_name}.json"))
        bb.setdefault("animations", [])
        bb["animations"] = [a for a in bb["animations"] if a["name"] != anim_name]
        bb["animations"].append(animation(dump, anim_name, bones))
        print(("replaced " if anim_name in names else "added ") + anim_name)
    out.write_text(json.dumps(bb), encoding="utf-8")


if __name__ == "__main__":
    main()

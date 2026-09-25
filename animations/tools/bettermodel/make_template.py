"""
A BetterModel player built like the Better Combat / PlayerAnimationLib rig with bends
(animations/animation_template.blend), so its animations play on it unchanged: pal_player.bbmodel.

The rig's bones, pivots and parenting, named after it, carrying BetterModel's player skin pieces:

  torso               hips (the rig's root)      hip, waist and chest slices, rigid; the legs
    torso_bend        6 px above the hips        the head and both arms
      ph_head         neck
      prsa_right_arm  shoulder, 2 px below the top of the arm      (fork piece: turns about the shoulder)
        prfa_right_arm_bend  elbow, 1 px out and 4 px down         forearm, and the held item under it
      plsa_left_arm / plfa_left_arm_bend                            mirrored
    prl_right_leg     hip joint                  leg
      prfl_right_leg_bend  knee, 6 px down       shin
    pll_left_leg / plfl_left_leg_bend                               mirrored

BetterModel draws its player at 0.9375, so a rig unit (4 px) is 3.75 Blockbench units here. The torso
is not split at torso_bend: the skin slices are BetterModel's three 4 px ones, and the rig never
bends the torso (a cape cannot follow a bend). prsa/plsa need the NightFantasy BetterModel fork
(3.5.0-nf.1 or later).

Built from a BetterModel player model for its skin texture and reference cubes.
  python make_template.py <source player .bbmodel> [out]
"""
import copy
import json
import sys
import uuid
from pathlib import Path

HERE = Path(__file__).parent
S = 0.9375                      # BetterModel's player scale
PX = S                          # one skin pixel in Blockbench units

# name: (origin, parent, BetterModel source bone whose cubes it carries, cube shift x)
BONES = {
    "torso": ((0, 12 * S, 0), None, None, 0),
    "phip_hip": ((0, 11.25, 0), "torso", "phip_hip", 0),
    "pw_waist": ((0, 15, 0), "phip_hip", "pw_waist", 0),
    "pc_chest": ((0, 18.75, 0), "pw_waist", "pc_chest", 0),
    "cape_cape": ((0, 22.75, 2), "pc_chest", "cape_cape", 0),
    "torso_bend": ((0, 18 * S, 0), "torso", None, 0),
    "ph_head": ((0, 24 * S, 0), "torso_bend", "h_ph_head", 0),
    "prsa_right_arm": ((5 * S, 22 * S, 0), "torso_bend", "pra_right_arm", 0),
    "prfa_right_arm_bend": ((6 * S, 18 * S, 0), "prsa_right_arm", "prfa_right_forearm", 0),
    "pri_right_item": ((5.625, 10.875, 0), "prfa_right_arm_bend", "pri_right_item", 0),
    "plsa_left_arm": ((-5 * S, 22 * S, 0), "torso_bend", "pla_left_arm", 0),
    "plfa_left_arm_bend": ((-6 * S, 18 * S, 0), "plsa_left_arm", "plfa_left_forearm", 0),
    "pli_left_item": ((-5.625, 10.875, 0), "plfa_left_arm_bend", "pli_left_item", 0),
    "prl_right_leg": ((2 * S, 12 * S, 0), "torso", "prl_right_leg", 0),
    "prfl_right_leg_bend": ((2 * S, 6 * S, 0), "prl_right_leg", "prfl_right_foreleg", 0),
    "pll_left_leg": ((-2 * S, 12 * S, 0), "torso", "pll_left_leg", 0),
    "plfl_left_leg_bend": ((-2 * S, 6 * S, 0), "pll_left_leg", "plfl_left_foreleg", 0),
}
# BetterModel's own bones kept as they are, at the root.
KEEP_AT_ROOT = ("shadow", "tag_name")


def main():
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "pal_player.bbmodel"
    bb = json.load(open(src, encoding="utf-8"))
    groups = {g["uuid"]: g for g in bb["groups"]}
    by_name = {g["name"]: g for g in bb["groups"]}
    els = {e["uuid"]: e for e in bb["elements"]}

    cubes_of = {}

    def collect(node):
        if isinstance(node, dict):
            name = groups[node["uuid"]]["name"]
            cubes_of[name] = [c for c in node.get("children", []) if isinstance(c, str)]
            for c in node.get("children", []):
                collect(c)
    for n in bb["outliner"]:
        collect(n)

    new_groups, nodes, keep_cubes = [], {}, set()
    for name, (origin, parent, source, shift) in BONES.items():
        base = copy.deepcopy(by_name[source]) if source else copy.deepcopy(by_name["pc_chest"])
        base.update({"uuid": str(uuid.uuid4()), "name": name, "origin": [round(v, 4) for v in origin]})
        if not source:
            base["rotation"] = [0, 0, 0]
        new_groups.append(base)
        children = []
        for cid in cubes_of.get(source, []) if source else []:
            e = els[cid]
            e["from"][0] = round(e["from"][0] + shift, 4)
            e["to"][0] = round(e["to"][0] + shift, 4)
            if "origin" in e:
                e["origin"][0] = round(e["origin"][0] + shift, 4)
            children.append(cid)
            keep_cubes.add(cid)
        nodes[name] = {"uuid": base["uuid"], "isOpen": True, "children": children}
    for name, (_, parent, _, _) in BONES.items():
        if parent:
            nodes[parent]["children"].append(nodes[name])

    root_src = next(g for g in bb["groups"] if g["name"] == "player_root")
    root = copy.deepcopy(root_src)
    root.update({"uuid": str(uuid.uuid4()), "origin": [0, 0, 0], "rotation": [0, 0, 0]})
    new_groups.append(root)
    root_node = {"uuid": root["uuid"], "isOpen": True, "children": [nodes["torso"]]}
    for keep in KEEP_AT_ROOT:
        g = copy.deepcopy(by_name[keep])
        new_groups.append(g)
        kids = cubes_of.get(keep, [])
        keep_cubes.update(kids)
        root_node["children"].append({"uuid": g["uuid"], "isOpen": False, "children": kids})

    bb["groups"] = new_groups
    bb["outliner"] = [root_node]
    bb["elements"] = [e for e in bb["elements"] if e["uuid"] in keep_cubes]
    bb["animations"] = []
    bb["name"] = out.stem
    # Only the skin texture is used by the player cubes.
    used = {f.get("texture") for e in bb["elements"] for f in e["faces"].values()} - {None}
    bb["textures"] = [t for i, t in enumerate(bb["textures"]) if i in used or str(i) in {str(u) for u in used}]
    out.write_text(json.dumps(bb), encoding="utf-8")
    print("wrote", out, len(new_groups), "bones,", len(bb["elements"]), "cubes")


if __name__ == "__main__":
    main()

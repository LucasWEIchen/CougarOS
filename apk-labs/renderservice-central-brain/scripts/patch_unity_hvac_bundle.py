#!/usr/bin/env python3
"""Patch the RenderService launcher bundle with Unity-native HVAC states."""

from __future__ import annotations

import argparse
import copy
import json
import shutil
import tempfile
from pathlib import Path

import UnityPy


DRIVER_BASE_GO = "27"
PASSENGER_BASE_GO = "27 (1)"
TMP_SOURCE_GO = "Degrees"
DRIVER_DECREASE_BUTTON_GO = "polygon 1"
DRIVER_INCREASE_BUTTON_GO = "polygon 2"
PASSENGER_DECREASE_BUTTON_GO = "polygon 1 (1)"
PASSENGER_INCREASE_BUTTON_GO = "polygon 2 (1)"

WARM_STATE_IDS = {
    "driver": {
        "go": 9000000000000011001,
        "rect": 9000000000000011002,
        "canvas": 9000000000000011003,
        "text": 9000000000000011004,
    },
    "passenger": {
        "go": 9000000000000011011,
        "rect": 9000000000000011012,
        "canvas": 9000000000000011013,
        "text": 9000000000000011014,
    },
}


def pptr(path_id: int) -> dict:
    return {"m_FileID": 0, "m_PathID": int(path_id)}


def path_id(value: dict) -> int:
    return int(value.get("m_PathID", 0))


class BundleContext:
    def __init__(self, env: UnityPy.Environment):
        self.env = env
        self.objects = {obj.path_id: obj for obj in env.objects}
        self.cache: dict[int, dict] = {}
        self.assets_file = env.objects[0].assets_file

    def refresh(self) -> None:
        self.objects = {obj.path_id: obj for obj in self.env.objects}

    def tree(self, object_path_id: int) -> dict:
        if object_path_id not in self.cache:
            self.cache[object_path_id] = self.objects[object_path_id].read_typetree()
        return self.cache[object_path_id]

    def save(self, object_path_id: int, tree: dict) -> None:
        self.objects[object_path_id].save_typetree(tree)
        self.cache[object_path_id] = tree

    def clone_or_update(
        self,
        source_path_id: int,
        target_path_id: int,
        tree: dict,
    ) -> None:
        if target_path_id in self.objects:
            target = self.objects[target_path_id]
        else:
            target = copy.copy(self.objects[source_path_id])
            target.path_id = target_path_id
            self.assets_file.objects[target_path_id] = target
            self.refresh()
        target.save_typetree(tree)
        self.cache[target_path_id] = tree


def find_game_object(ctx: BundleContext, name: str) -> tuple[int, dict]:
    for obj in ctx.env.objects:
        if obj.type.name != "GameObject":
            continue
        tree = ctx.tree(obj.path_id)
        if tree.get("m_Name") == name:
            return obj.path_id, tree
    raise RuntimeError(f"Unity GameObject not found: {name}")


def components(ctx: BundleContext, game_object_tree: dict) -> dict[str, tuple[int, dict]]:
    result: dict[str, tuple[int, dict]] = {}
    for component in game_object_tree.get("m_Component", []):
        component_id = path_id(component["component"])
        component_obj = ctx.objects[component_id]
        component_tree = ctx.tree(component_id)
        type_name = component_obj.type.name
        if type_name == "MonoBehaviour":
            script_id = path_id(component_tree.get("m_Script", {}))
            script_tree = ctx.tree(script_id)
            script_name = ".".join(
                value
                for value in (
                    script_tree.get("m_Namespace", ""),
                    script_tree.get("m_ClassName", ""),
                )
                if value
            )
            result[script_name] = (component_id, component_tree)
        else:
            result[type_name] = (component_id, component_tree)
    return result


def active_call(target_go_id: int, active: bool) -> dict:
    return {
        "m_Target": pptr(target_go_id),
        "m_TargetAssemblyTypeName": "UnityEngine.GameObject, UnityEngine.CoreModule",
        "m_MethodName": "SetActive",
        "m_Mode": 6,
        "m_Arguments": {
            "m_ObjectArgument": pptr(0),
            "m_ObjectArgumentAssemblyTypeName": "UnityEngine.Object, UnityEngine",
            "m_IntArgument": 0,
            "m_FloatArgument": 0.0,
            "m_StringArgument": "",
            "m_BoolArgument": 1 if active else 0,
        },
        "m_CallState": 2,
    }


def set_button_state_calls(
    button_tree: dict,
    visible_go_id: int,
    hidden_go_id: int,
) -> None:
    button_tree["m_OnClick"] = {
        "m_PersistentCalls": {
            "m_Calls": [
                active_call(visible_go_id, True),
                active_call(hidden_go_id, False),
            ]
        }
    }


def ensure_child(
    ctx: BundleContext,
    parent_rect_id: int,
    base_rect_id: int,
    child_rect_id: int,
) -> None:
    parent_tree = ctx.tree(parent_rect_id)
    children = [
        child
        for child in parent_tree.get("m_Children", [])
        if path_id(child) != child_rect_id
    ]
    insertion = len(children)
    for index, child in enumerate(children):
        if path_id(child) == base_rect_id:
            insertion = index + 1
            break
    children.insert(insertion, pptr(child_rect_id))
    parent_tree["m_Children"] = children
    ctx.save(parent_rect_id, parent_tree)


def create_warm_state(
    ctx: BundleContext,
    zone: str,
    base_go_id: int,
    base_go_tree: dict,
    tmp_source_id: int,
    tmp_source_tree: dict,
) -> int:
    ids = WARM_STATE_IDS[zone]
    base_components = components(ctx, base_go_tree)
    base_rect_id, base_rect_tree = base_components["RectTransform"]
    base_canvas_id, base_canvas_tree = base_components["CanvasRenderer"]

    warm_go_tree = copy.deepcopy(base_go_tree)
    warm_go_tree["m_Name"] = f"CentralBrain_{zone}_temperature_28_0"
    warm_go_tree["m_IsActive"] = False
    warm_go_tree["m_Component"] = [
        {"component": pptr(ids["rect"])},
        {"component": pptr(ids["canvas"])},
        {"component": pptr(ids["text"])},
    ]

    warm_rect_tree = copy.deepcopy(base_rect_tree)
    warm_rect_tree["m_GameObject"] = pptr(ids["go"])
    warm_rect_tree["m_Children"] = []
    warm_rect_tree["m_SizeDelta"] = {"x": 118.0, "y": 34.0}

    warm_canvas_tree = copy.deepcopy(base_canvas_tree)
    warm_canvas_tree["m_GameObject"] = pptr(ids["go"])

    warm_text_tree = copy.deepcopy(tmp_source_tree)
    warm_text_tree["m_GameObject"] = pptr(ids["go"])
    warm_text_tree["m_text"] = "28.0°C"
    warm_text_tree["m_fontSize"] = 28.0
    warm_text_tree["m_fontSizeBase"] = 28.0
    warm_text_tree["m_enableAutoSizing"] = 0
    warm_text_tree["m_raycastTarget"] = 0
    warm_text_tree["m_HorizontalAlignment"] = 2
    warm_text_tree["m_VerticalAlignment"] = 512
    warm_text_tree["m_Color"] = {
        "r": 1.0,
        "g": 1.0,
        "b": 1.0,
        "a": 1.0,
    }

    ctx.clone_or_update(base_go_id, ids["go"], warm_go_tree)
    ctx.clone_or_update(base_rect_id, ids["rect"], warm_rect_tree)
    ctx.clone_or_update(base_canvas_id, ids["canvas"], warm_canvas_tree)
    ctx.clone_or_update(tmp_source_id, ids["text"], warm_text_tree)
    ensure_child(
        ctx,
        path_id(base_rect_tree["m_Father"]),
        base_rect_id,
        ids["rect"],
    )
    return ids["go"]


def patch_bundle(input_path: Path, output_path: Path) -> dict:
    env = UnityPy.load(str(input_path))
    ctx = BundleContext(env)

    driver_base_id, driver_base_tree = find_game_object(ctx, DRIVER_BASE_GO)
    passenger_base_id, passenger_base_tree = find_game_object(
        ctx,
        PASSENGER_BASE_GO,
    )
    _, tmp_source_go_tree = find_game_object(ctx, TMP_SOURCE_GO)
    tmp_source_id, tmp_source_tree = components(ctx, tmp_source_go_tree)[
        "TMPro.TextMeshProUGUI"
    ]

    driver_warm_id = create_warm_state(
        ctx,
        "driver",
        driver_base_id,
        driver_base_tree,
        tmp_source_id,
        tmp_source_tree,
    )
    passenger_warm_id = create_warm_state(
        ctx,
        "passenger",
        passenger_base_id,
        passenger_base_tree,
        tmp_source_id,
        tmp_source_tree,
    )

    button_specs = [
        (DRIVER_DECREASE_BUTTON_GO, driver_base_id, driver_warm_id),
        (DRIVER_INCREASE_BUTTON_GO, driver_warm_id, driver_base_id),
        (PASSENGER_DECREASE_BUTTON_GO, passenger_base_id, passenger_warm_id),
        (PASSENGER_INCREASE_BUTTON_GO, passenger_warm_id, passenger_base_id),
    ]
    button_results = {}
    for name, visible_id, hidden_id in button_specs:
        _, button_go_tree = find_game_object(ctx, name)
        button_id, button_tree = components(ctx, button_go_tree)[
            "UnityEngine.UI.Button"
        ]
        set_button_state_calls(button_tree, visible_id, hidden_id)
        ctx.save(button_id, button_tree)
        button_results[name] = {
            "buttonPathId": button_id,
            "visiblePathId": visible_id,
            "hiddenPathId": hidden_id,
        }

    driver_base_tree["m_IsActive"] = True
    passenger_base_tree["m_IsActive"] = True
    ctx.save(driver_base_id, driver_base_tree)
    ctx.save(passenger_base_id, passenger_base_tree)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="central-brain-unity-hvac-") as tmp:
        env.save(pack="lz4", out_path=tmp)
        emitted = Path(tmp) / input_path.name
        if not emitted.is_file():
            raise RuntimeError("UnityPy did not emit the patched launcher bundle")
        shutil.copy2(emitted, output_path)

    verified_env = UnityPy.load(str(output_path))
    verified_ctx = BundleContext(verified_env)
    verified = {}
    for zone, ids in WARM_STATE_IDS.items():
        warm_tree = verified_ctx.tree(ids["go"])
        text_tree = verified_ctx.tree(ids["text"])
        verified[zone] = {
            "gameObject": warm_tree.get("m_Name"),
            "active": bool(warm_tree.get("m_IsActive")),
            "text": text_tree.get("m_text"),
        }
    return {
        "input": str(input_path),
        "output": str(output_path),
        "states": verified,
        "buttons": button_results,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = patch_bundle(args.input, args.output)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

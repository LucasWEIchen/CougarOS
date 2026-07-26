#!/usr/bin/env python3
"""Patch the RenderService launcher bundle for dynamic HVAC and orbit input."""

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
INPUT_RECOGNIZERS_GO = "LauncherInputRecognizers"
PAN_RECOGNIZER_CLASS = "DevKit.InputManager.Gestures.InputSystemPanRecognizer"

DYNAMIC_STATE_IDS = {
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
THIN_MATERIAL_ID = 9000000000000011021
TEMPERATURE_OBJECT_NAMES = {
    "driver": "CentralBrainDriverTemperature",
    "passenger": "CentralBrainPassengerTemperature",
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


def create_thin_temperature_material(
    ctx: BundleContext,
    source_text_tree: dict,
) -> int:
    source_material_id = path_id(source_text_tree["m_sharedMaterial"])
    material_tree = copy.deepcopy(ctx.tree(source_material_id))
    material_tree["m_Name"] = "CentralBrain Temperature Thin Material"
    floats = material_tree["m_SavedProperties"]["m_Floats"]
    material_tree["m_SavedProperties"]["m_Floats"] = [
        (key, -0.18 if key == "_FaceDilate" else value)
        for key, value in floats
    ]
    ctx.clone_or_update(
        source_material_id,
        THIN_MATERIAL_ID,
        material_tree,
    )
    return THIN_MATERIAL_ID


def create_dynamic_temperature(
    ctx: BundleContext,
    zone: str,
    base_go_id: int,
    base_go_tree: dict,
    tmp_source_id: int,
    tmp_source_tree: dict,
    thin_material_id: int,
) -> int:
    ids = DYNAMIC_STATE_IDS[zone]
    base_components = components(ctx, base_go_tree)
    base_rect_id, base_rect_tree = base_components["RectTransform"]
    base_canvas_id, base_canvas_tree = base_components["CanvasRenderer"]

    dynamic_go_tree = copy.deepcopy(base_go_tree)
    dynamic_go_tree["m_Name"] = TEMPERATURE_OBJECT_NAMES[zone]
    dynamic_go_tree["m_IsActive"] = True
    dynamic_go_tree["m_Component"] = [
        {"component": pptr(ids["rect"])},
        {"component": pptr(ids["canvas"])},
        {"component": pptr(ids["text"])},
    ]

    dynamic_rect_tree = copy.deepcopy(base_rect_tree)
    dynamic_rect_tree["m_GameObject"] = pptr(ids["go"])
    dynamic_rect_tree["m_Children"] = []
    dynamic_rect_tree["m_SizeDelta"] = {"x": 118.0, "y": 34.0}

    dynamic_canvas_tree = copy.deepcopy(base_canvas_tree)
    dynamic_canvas_tree["m_GameObject"] = pptr(ids["go"])

    dynamic_text_tree = copy.deepcopy(tmp_source_tree)
    dynamic_text_tree["m_GameObject"] = pptr(ids["go"])
    dynamic_text_tree["m_text"] = "26.5°C"
    dynamic_text_tree["m_fontSize"] = 28.0
    dynamic_text_tree["m_fontSizeBase"] = 28.0
    dynamic_text_tree["m_fontWeight"] = 300
    dynamic_text_tree["m_enableAutoSizing"] = 0
    dynamic_text_tree["m_raycastTarget"] = 0
    dynamic_text_tree["m_HorizontalAlignment"] = 2
    dynamic_text_tree["m_VerticalAlignment"] = 512
    dynamic_text_tree["m_sharedMaterial"] = pptr(thin_material_id)
    dynamic_text_tree["m_Color"] = {
        "r": 1.0,
        "g": 1.0,
        "b": 1.0,
        "a": 1.0,
    }

    ctx.clone_or_update(base_go_id, ids["go"], dynamic_go_tree)
    ctx.clone_or_update(base_rect_id, ids["rect"], dynamic_rect_tree)
    ctx.clone_or_update(base_canvas_id, ids["canvas"], dynamic_canvas_tree)
    ctx.clone_or_update(tmp_source_id, ids["text"], dynamic_text_tree)
    ensure_child(
        ctx,
        path_id(base_rect_tree["m_Father"]),
        base_rect_id,
        ids["rect"],
    )
    return ids["go"]


def verify_vendor_pan_recognizer(ctx: BundleContext) -> dict:
    _, recognizers_go_tree = find_game_object(ctx, INPUT_RECOGNIZERS_GO)
    component_id, component_tree = components(ctx, recognizers_go_tree)[
        PAN_RECOGNIZER_CLASS
    ]
    expected = {
        "_targetInputDisplay": 2,
        "_eventSystemRaycastCheck": 1,
        "useFingerPolling": 0,
    }
    actual = {key: component_tree[key] for key in expected}
    if actual != expected:
        raise RuntimeError(
            f"Unexpected vendor Pan recognizer contract: {actual}"
        )
    return {
        "componentPathId": component_id,
        "targetInputDisplay": component_tree["_targetInputDisplay"],
        "eventSystemRaycastCheck": bool(
            component_tree["_eventSystemRaycastCheck"]
        ),
        "useFingerPolling": bool(component_tree["useFingerPolling"]),
        "vendorConfigurationPreserved": True,
    }


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
    thin_material_id = create_thin_temperature_material(ctx, tmp_source_tree)

    create_dynamic_temperature(
        ctx,
        "driver",
        driver_base_id,
        driver_base_tree,
        tmp_source_id,
        tmp_source_tree,
        thin_material_id,
    )
    create_dynamic_temperature(
        ctx,
        "passenger",
        passenger_base_id,
        passenger_base_tree,
        tmp_source_id,
        tmp_source_tree,
        thin_material_id,
    )

    driver_base_tree["m_IsActive"] = False
    passenger_base_tree["m_IsActive"] = False
    ctx.save(driver_base_id, driver_base_tree)
    ctx.save(passenger_base_id, passenger_base_tree)
    pan_result = verify_vendor_pan_recognizer(ctx)

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
    for zone, ids in DYNAMIC_STATE_IDS.items():
        dynamic_tree = verified_ctx.tree(ids["go"])
        text_tree = verified_ctx.tree(ids["text"])
        verified[zone] = {
            "gameObject": dynamic_tree.get("m_Name"),
            "active": bool(dynamic_tree.get("m_IsActive")),
            "text": text_tree.get("m_text"),
            "fontSize": text_tree.get("m_fontSize"),
            "fontWeight": text_tree.get("m_fontWeight"),
        }
    _, verified_recognizer_go = find_game_object(
        verified_ctx,
        INPUT_RECOGNIZERS_GO,
    )
    _, verified_pan_tree = components(
        verified_ctx,
        verified_recognizer_go,
    )[PAN_RECOGNIZER_CLASS]
    return {
        "input": str(input_path),
        "output": str(output_path),
        "states": verified,
        "panRecognizer": {
            **pan_result,
            "targetInputDisplay": verified_pan_tree["_targetInputDisplay"],
            "eventSystemRaycastCheck": bool(
                verified_pan_tree["_eventSystemRaycastCheck"]
            ),
            "useFingerPolling": bool(
                verified_pan_tree["useFingerPolling"]
            ),
        },
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

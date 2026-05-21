"""
Phase 21 — Export MaskablePPO actor weights to JSON for on-device Kotlin inference.

Usage:
    uv run --with sb3-contrib --with stable-baselines3 --with torch ^
        python training/export_weights.py ^
        --model best_model.zip ^
        --output app/src/main/assets/tribe_policy.json
"""
from __future__ import annotations

import argparse
import json
import os


def main() -> None:
    parser = argparse.ArgumentParser(description="Export MaskablePPO actor weights to JSON")
    parser.add_argument("--model",  default="best_model.zip",
                        help="Path to the .zip checkpoint (default: best_model.zip)")
    parser.add_argument("--output", default="app/src/main/assets/tribe_policy.json",
                        help="Destination JSON path (default: app/src/main/assets/tribe_policy.json)")
    args = parser.parse_args()

    from sb3_contrib import MaskablePPO  # noqa: PLC0415

    print(f"Loading {args.model} …")
    model = MaskablePPO.load(args.model)

    layers = []
    for module in model.policy.mlp_extractor.policy_net:
        if hasattr(module, "weight"):
            layers.append({
                "w": module.weight.detach().cpu().numpy().tolist(),
                "b": module.bias.detach().cpu().numpy().tolist(),
            })
    an = model.policy.action_net
    layers.append({
        "w": an.weight.detach().cpu().numpy().tolist(),
        "b": an.bias.detach().cpu().numpy().tolist(),
    })

    payload = {"layers": layers}

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w") as f:
        json.dump(payload, f, separators=(",", ":"))

    shapes = [f"({len(l['w'])}x{len(l['w'][0])})" for l in layers]
    print(f"Exported {len(layers)} layers: {' -> '.join(shapes)}")
    print(f"Written: {args.output}  ({os.path.getsize(args.output) // 1024} KB)")


if __name__ == "__main__":
    main()

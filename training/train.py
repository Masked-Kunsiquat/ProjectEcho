"""
ProjectEcho Phase 20 — PPO training entry point.

Usage (local):
    uv run python train.py --timesteps 1000000 --envs 8

Usage (Kaggle / Colab — fewer parallel envs to fit free-tier RAM):
    uv run python train.py --timesteps 500000 --envs 4 --output-dir /kaggle/working/checkpoints

Flags:
  --timesteps     Total env steps to train for (default 1 000 000)
  --envs          Number of parallel SubprocVecEnv workers (default 8)
  --seed          Master RNG seed (default 42)
  --output-dir    Directory for checkpoints and TensorBoard logs
  --resume        Path to a saved model to resume from (optional)
  --win-rate      Win-rate threshold to stop early (default 0.60)
  --dummy-vec     Use DummyVecEnv instead of SubprocVecEnv (single-process fallback)
"""
from __future__ import annotations

import argparse
import os
import sys


def make_env(seed: int, max_ticks: int = 50):
    """Factory for SubprocVecEnv — must be picklable (no closure over complex objects)."""
    def _init():
        # Import inside the worker to avoid pickling the module
        from env_wrappers import LeagueEnv
        env = LeagueEnv(num_tribes=4, max_ticks=max_ticks, domain_randomize=True)
        env.reset(seed=seed)
        return env
    return _init


def build_vec_env(n_envs: int, seed: int, use_dummy: bool):
    from env_wrappers import LeagueEnv
    if use_dummy or n_envs == 1:
        from stable_baselines3.common.vec_env import DummyVecEnv
        return DummyVecEnv([make_env(seed + i) for i in range(n_envs)])
    from stable_baselines3.common.vec_env import SubprocVecEnv
    return SubprocVecEnv([make_env(seed + i) for i in range(n_envs)])


def main(args: argparse.Namespace) -> None:
    from stable_baselines3.common.vec_env import VecMonitor
    from stable_baselines3.common.utils import get_linear_fn
    from stable_baselines3.common.callbacks import CheckpointCallback
    from sb3_contrib import MaskablePPO
    from callbacks import HallOfFameCallback, CurriculumCallback, HeuristicEvalCallback

    os.makedirs(args.output_dir, exist_ok=True)

    vec_env = VecMonitor(build_vec_env(args.envs, args.seed, args.dummy_vec))

    if args.resume and os.path.exists(args.resume):
        print(f"Resuming from {args.resume}")
        model = MaskablePPO.load(args.resume, env=vec_env)
    else:
        # Entropy decays linearly from 0.1 → 0.01 over the full run.
        # High early entropy forces exploration; decay lets the policy commit.
        ent_schedule = get_linear_fn(start=0.1, end=0.01, end_fraction=1.0)

        model = MaskablePPO(
            "MlpPolicy",
            vec_env,
            learning_rate   = 3e-4,
            n_steps         = 512,        # steps per env per rollout
            batch_size      = 64,
            n_epochs        = 10,
            gamma           = 0.99,
            gae_lambda      = 0.95,
            clip_range      = 0.2,
            ent_coef        = ent_schedule,
            verbose         = 1,
            seed            = args.seed,
            tensorboard_log = os.path.join(args.output_dir, "tb"),
            policy_kwargs   = dict(net_arch=[128, 128]),
        )

    callbacks = [
        # Hall of Fame: snapshot weights every 5 000 steps → push to all workers
        HallOfFameCallback(snapshot_every=5_000, verbose=1),

        # Curriculum: episodes grow from 50 → 100 → 200 ticks as policy matures
        CurriculumCallback(verbose=1),

        # Periodic checkpoint every 50 000 steps — rollback safety net
        CheckpointCallback(
            save_freq     = 50_000,
            save_path     = args.output_dir,
            name_prefix   = "ckpt",
            verbose       = 0,
        ),

        # Periodic heuristic eval; saves best model and stops at win_rate threshold
        HeuristicEvalCallback(
            eval_freq          = 25_000,
            n_eval_episodes    = 20,
            win_rate_threshold = args.win_rate,
            output_dir         = args.output_dir,
            verbose            = 1,
        ),
    ]

    print(f"Training for {args.timesteps:,} steps across {args.envs} envs …")
    model.learn(
        total_timesteps = args.timesteps,
        callback        = callbacks,
        progress_bar    = True,
    )

    final_path = os.path.join(args.output_dir, "final_model")
    model.save(final_path)
    print(f"Saved final model → {final_path}.zip")


if __name__ == "__main__":
    # Required for SubprocVecEnv on Windows / some Jupyter kernels
    import multiprocessing
    multiprocessing.freeze_support()

    parser = argparse.ArgumentParser(description="ProjectEcho Phase 20 PPO trainer")
    parser.add_argument("--timesteps",  type=int,   default=1_000_000)
    parser.add_argument("--envs",       type=int,   default=8)
    parser.add_argument("--seed",       type=int,   default=42)
    parser.add_argument("--output-dir", type=str,   default="./checkpoints")
    parser.add_argument("--resume",     type=str,   default=None,
                        help="Path to a .zip checkpoint to resume from")
    parser.add_argument("--win-rate",   type=float, default=0.60,
                        help="Heuristic win-rate threshold to stop training early")
    parser.add_argument("--dummy-vec",  action="store_true",
                        help="Use DummyVecEnv (single-process; for debugging)")
    main(parser.parse_args())

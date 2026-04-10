"""
One-time model downloader.
Run: python download_models.py

Downloads all required weights to the weights/ directory.
Total size: ~15-20 GB. Requires ~25 GB free disk space during download.
"""
import os
import sys
import urllib.request

from huggingface_hub import hf_hub_download, snapshot_download

WEIGHTS_DIR = os.path.join(os.path.dirname(__file__), "weights")


def ensure_dir(path: str):
    os.makedirs(path, exist_ok=True)


def download_file(url: str, dest: str, label: str):
    if os.path.exists(dest):
        print(f"  [skip] {label} already exists")
        return
    print(f"  [download] {label} ...")
    ensure_dir(os.path.dirname(dest))

    def progress(block, block_size, total):
        downloaded = block * block_size
        if total > 0:
            pct = min(downloaded / total * 100, 100)
            sys.stdout.write(f"\r    {pct:.1f}%  ({downloaded // 1_000_000} MB / {total // 1_000_000} MB)")
            sys.stdout.flush()

    urllib.request.urlretrieve(url, dest, reporthook=progress)
    print()  # newline after progress


def download_hf(repo_id: str, filename: str, dest_dir: str, label: str):
    dest = os.path.join(dest_dir, os.path.basename(filename))
    if os.path.exists(dest):
        print(f"  [skip] {label} already exists")
        return dest
    print(f"  [download] {label} ...")
    ensure_dir(dest_dir)
    path = hf_hub_download(repo_id=repo_id, filename=filename, local_dir=dest_dir)
    print(f"  [done] saved to {path}")
    return path


def main():
    print("=" * 60)
    print("  Beauty Camera — AI Model Downloader")
    print("=" * 60)

    # ── 1. SDXL base model (cached by diffusers/HuggingFace hub) ─────────────
    print("\n[1/5] Stable Diffusion XL (cached via diffusers — ~7 GB)")
    print("  Will be auto-downloaded on first server start via HuggingFace hub.")
    print("  Set HF_HOME env var to control cache location.")

    # ── 2. IP-Adapter FaceID weights ─────────────────────────────────────────
    print("\n[2/5] IP-Adapter FaceID weights")
    ip_dir = os.path.join(WEIGHTS_DIR, "ip_adapter")

    download_hf(
        repo_id="h94/IP-Adapter-FaceID",
        filename="ip-adapter-faceid-plusv2_sdxl.bin",
        dest_dir=ip_dir,
        label="IP-Adapter FaceID Plus v2 SDXL (~1 GB)",
    )
    download_hf(
        repo_id="h94/IP-Adapter-FaceID",
        filename="ip-adapter-faceid-portrait-v11_sd15.bin",
        dest_dir=ip_dir,
        label="IP-Adapter FaceID Portrait v11 SD1.5 (~300 MB)",
    )

    # ── 3. ControlNet weights ─────────────────────────────────────────────────
    print("\n[3/5] ControlNet weights (SD1.5)")
    cn_dir = os.path.join(WEIGHTS_DIR, "controlnet")

    download_hf(
        repo_id="lllyasviel/ControlNet-v1-1",
        filename="control_v11p_sd15_openpose.pth",
        dest_dir=cn_dir,
        label="ControlNet OpenPose (~1.5 GB)",
    )
    download_hf(
        repo_id="lllyasviel/ControlNet-v1-1",
        filename="control_v11f1p_sd15_depth.pth",
        dest_dir=cn_dir,
        label="ControlNet Depth (~1.5 GB)",
    )

    # ── 4. GFPGAN face restoration ────────────────────────────────────────────
    print("\n[4/5] GFPGAN v1.4 face restoration (~350 MB)")
    gfpgan_dir = os.path.join(WEIGHTS_DIR, "gfpgan")
    ensure_dir(gfpgan_dir)
    download_file(
        url="https://github.com/TencentARC/GFPGAN/releases/download/v1.3.4/GFPGANv1.4.pth",
        dest=os.path.join(gfpgan_dir, "GFPGANv1.4.pth"),
        label="GFPGANv1.4.pth",
    )

    # ── 5. Real-ESRGAN upscaler ───────────────────────────────────────────────
    print("\n[5/5] Real-ESRGAN x4plus (~70 MB)")
    esrgan_dir = os.path.join(WEIGHTS_DIR, "realesrgan")
    ensure_dir(esrgan_dir)
    download_file(
        url="https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth",
        dest=os.path.join(esrgan_dir, "RealESRGAN_x4plus.pth"),
        label="RealESRGAN_x4plus.pth",
    )

    print("\n" + "=" * 60)
    print("  All downloads complete!")
    print("  InsightFace buffalo_l downloads automatically on first use.")
    print("  Run: python main.py  to start the server.")
    print("=" * 60)


if __name__ == "__main__":
    main()

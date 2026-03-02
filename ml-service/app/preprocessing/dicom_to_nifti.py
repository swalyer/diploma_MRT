from pathlib import Path
import shutil


def to_nifti(input_key: str, artifacts_root: str) -> str:
    in_path = Path(artifacts_root) / input_key
    if str(in_path).endswith(('.nii', '.nii.gz')):
        return input_key
    out_key = f"{input_key}.converted.nii.gz"
    out_path = Path(artifacts_root) / out_key
    out_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(in_path, out_path)
    return out_key

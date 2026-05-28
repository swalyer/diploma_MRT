from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file='.env', env_prefix='ML_')

    mode: str = 'mock'
    artifacts_root: str = './storage'
    models_config_path: str = './ml-service/config/models.real.example.yml'
    totalsegmentator_task: str = 'total'
    allow_mri_experimental: bool = True

    # Weights provenance (NFR-2): manifest is the single source of truth and is
    # SHA256-verified before any checkpoint is wired into an adapter.
    weights_manifest_path: str = './ml-service/config/weights_manifest.yml'
    weights_cache_dir: str = './storage/weights'
    nnunet_results_dir: str = './storage/weights/nnUNet_results'

    # Compute placement: 'auto' picks CUDA when available, else CPU.
    device: str = 'auto'

    # nnU-Net lesion models per modality. A direct results folder takes priority
    # (the post-training local path); otherwise the manifest weights key is used.
    nnunet_folds: str = '0'
    nnunet_checkpoint: str = 'checkpoint_final.pth'
    nnunet_mri_model_dir: str = ''
    nnunet_ct_model_dir: str = ''
    nnunet_mri_weights_key: str = 'nnunet_atlas_mri_lesion'
    nnunet_ct_weights_key: str = ''


settings = Settings()

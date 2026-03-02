from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file='.env', env_prefix='ML_')

    mode: str = 'mock'
    artifacts_root: str = './storage/ml'
    models_config_path: str = './ml-service/config/models.real.example.yml'
    totalsegmentator_task: str = 'total'
    allow_mri_experimental: bool = True


settings = Settings()

from dataclasses import dataclass
from pathlib import Path
import yaml


@dataclass
class ModelConfig:
    name: str
    enabled: bool
    command: str | None = None
    weights: str | None = None


class ModelRegistry:
    def __init__(self, config_path: str):
        self._config_path = Path(config_path)
        self._raw = self._load()

    def _load(self) -> dict:
        if not self._config_path.exists():
            return {}
        return yaml.safe_load(self._config_path.read_text()) or {}

    def get(self, key: str) -> ModelConfig:
        node = self._raw.get(key, {})
        return ModelConfig(
            name=node.get('name', key),
            enabled=bool(node.get('enabled', False)),
            command=node.get('command'),
            weights=node.get('weights'),
        )

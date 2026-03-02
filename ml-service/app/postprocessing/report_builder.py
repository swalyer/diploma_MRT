import json


WARNING = 'Внимание: результат является системой поддержки принятия решений и не заменяет заключение врача-рентгенолога.'


def build_report(modality: str, mode: str, lesion_count: int, experimental: bool = False) -> tuple[str, str]:
    if experimental:
        text = f"{modality}: сегментация печени выполнена, анализ очагов недоступен в экспериментальном режиме MRI. {WARNING}"
    elif lesion_count > 0:
        text = f"{modality}: обнаружены подозрительные очаги печени (n={lesion_count}). {WARNING}"
    else:
        text = f"{modality}: подозрительных очагов не обнаружено. {WARNING}"
    payload = {'modality': modality, 'mode': mode, 'lesionCount': lesion_count, 'experimental': experimental}
    return text, json.dumps(payload, ensure_ascii=False)

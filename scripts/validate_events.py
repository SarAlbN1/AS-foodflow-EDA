#!/usr/bin/env python3
"""Valida los ejemplos de eventos contra los esquemas JSON de contracts/events/v1.

No es código compartido entre servicios: es una herramienta de verificación del
contrato (docs/wiki/03-contratos/eventos.md). Ningún servicio la importa.

Convención de los ejemplos:
    examples/validos/<slug>.json            debe VALIDAR contra <slug>.schema.json
    examples/invalidos/<slug>__<motivo>.json debe FALLAR  contra <slug>.schema.json

Además del esquema, se comprueba una regla que JSON Schema no puede expresar:
    aggregateId == payload.orderId   (ADR-04: aggregateId es siempre el orderId)

Uso: bash scripts/validate-events.sh
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from jsonschema import Draft202012Validator, FormatChecker
    from referencing import Registry, Resource
except ImportError:
    sys.exit(
        "Falta la dependencia 'jsonschema'.\n"
        "Ejecuta 'bash scripts/validate-events.sh', que prepara el entorno por ti."
    )

RAIZ = Path(__file__).resolve().parent.parent
CONTRATOS = RAIZ / "contracts" / "events" / "v1"
VALIDOS = CONTRATOS / "examples" / "validos"
INVALIDOS = CONTRATOS / "examples" / "invalidos"

VERDE, ROJO, GRIS, FIN = "\033[32m", "\033[31m", "\033[90m", "\033[0m"

EVENTOS_ESPERADOS = {
    "order-created": "OrderCreated",
    "order-status-changed": "OrderStatusChanged",
    "payment-approved": "PaymentApproved",
    "payment-rejected": "PaymentRejected",
    "notification-sent": "NotificationSent",
    "notification-failed": "NotificationFailed",
}


def cargar(ruta: Path) -> dict:
    with ruta.open(encoding="utf-8") as fh:
        return json.load(fh)


def construir_registro() -> Registry:
    """Registra todos los esquemas por su $id para resolver los $ref entre archivos."""
    recursos = []
    for ruta in sorted(CONTRATOS.glob("*.schema.json")):
        esquema = cargar(ruta)
        recursos.append((esquema["$id"], Resource.from_contents(esquema)))
    return Registry().with_resources(recursos)


def errores_de_esquema(validador, documento) -> list[str]:
    return [
        f"{'/'.join(str(p) for p in e.absolute_path) or '(raíz)'}: {e.message}"
        for e in sorted(validador.iter_errors(documento), key=lambda e: list(e.absolute_path))
    ]


def errores_semanticos(documento) -> list[str]:
    """Reglas del contrato que el esquema JSON no puede expresar por sí solo."""
    fallos = []
    if not isinstance(documento, dict):
        return fallos
    agregado = documento.get("aggregateId")
    payload = documento.get("payload")
    if isinstance(payload, dict) and "orderId" in payload:
        if agregado != payload["orderId"]:
            fallos.append(
                "aggregateId debe ser igual a payload.orderId (ADR-04: es la clave de "
                f"partición del pedido); se recibió aggregateId={agregado!r} y "
                f"payload.orderId={payload['orderId']!r}"
            )
    return fallos


def main() -> int:
    if not CONTRATOS.is_dir():
        print(f"{ROJO}No existe {CONTRATOS}{FIN}")
        return 1

    registro = construir_registro()
    comprobador = FormatChecker()

    validadores = {}
    for slug, tipo in EVENTOS_ESPERADOS.items():
        ruta = CONTRATOS / f"{slug}.schema.json"
        if not ruta.is_file():
            print(f"{ROJO}FALTA el esquema de {tipo}: {ruta.relative_to(RAIZ)}{FIN}")
            return 1
        esquema = cargar(ruta)
        Draft202012Validator.check_schema(esquema)
        validadores[slug] = Draft202012Validator(
            esquema, registry=registro, format_checker=comprobador
        )
    print(f"{VERDE}OK{FIN}  los 6 esquemas de eventos existen y son JSON Schema 2020-12 válidos")

    fallos = 0
    total = 0

    print("\n-- Ejemplos válidos (deben pasar) --")
    for ruta in sorted(VALIDOS.glob("*.json")):
        total += 1
        slug = ruta.stem
        if slug not in validadores:
            print(f"{ROJO}FALLA{FIN} {ruta.name}: no hay esquema '{slug}.schema.json'")
            fallos += 1
            continue
        problemas = errores_de_esquema(validadores[slug], cargar(ruta)) + errores_semanticos(cargar(ruta))
        if problemas:
            fallos += 1
            print(f"{ROJO}FALLA{FIN} {ruta.name}: debía validar y no lo hizo")
            for p in problemas:
                print(f"        {GRIS}{p}{FIN}")
        else:
            print(f"{VERDE}OK{FIN}    {ruta.name}")

    print("\n-- Ejemplos inválidos (deben ser rechazados) --")
    for ruta in sorted(INVALIDOS.glob("*.json")):
        total += 1
        slug, _, motivo = ruta.stem.partition("__")
        if slug not in validadores:
            print(f"{ROJO}FALLA{FIN} {ruta.name}: no hay esquema '{slug}.schema.json'")
            fallos += 1
            continue
        documento = cargar(ruta)
        problemas = errores_de_esquema(validadores[slug], documento) + errores_semanticos(documento)
        if problemas:
            print(f"{VERDE}OK{FIN}    {ruta.name}")
            print(f"        {GRIS}rechazado por: {problemas[0]}{FIN}")
        else:
            fallos += 1
            print(f"{ROJO}FALLA{FIN} {ruta.name}: debía ser rechazado ({motivo or 'sin motivo'}) y pasó")

    faltan = set(EVENTOS_ESPERADOS) - {r.stem for r in VALIDOS.glob("*.json")}
    if faltan:
        fallos += 1
        print(f"\n{ROJO}FALLA{FIN} sin ejemplo válido para: {', '.join(sorted(faltan))}")

    print(f"\n{total} ejemplos comprobados, {fallos} fallo(s).")
    if fallos:
        print(f"{ROJO}Validación de contratos de eventos: FALLIDA{FIN}")
        return 1
    print(f"{VERDE}Validación de contratos de eventos: CORRECTA{FIN}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Valida el contrato REST de FoodFlow (HU-404).

Tres comprobaciones:

1. `contracts/api/openapi.yaml` es un documento OpenAPI 3.1 válido.
2. Están documentadas las tres operaciones de la API mínima, la cabecera obligatoria
   `Idempotency-Key`, la cabecera `X-Correlation-Id` y los códigos 400, 404 y 409.
3. Cada ejemplo del documento valida contra el esquema de su propia respuesta o solicitud,
   de modo que la documentación no pueda quedar mintiendo sobre el formato real.

Devuelve 0 si todo pasa y 1 si algo falla.
"""

from __future__ import annotations

import sys
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate
from openapi_spec_validator.readers import read_from_filename
from referencing import Registry, Resource

RAIZ = Path(__file__).resolve().parent.parent
CONTRATO = RAIZ / "contracts" / "api" / "openapi.yaml"

OPERACIONES = [
    ("/orders", "post"),
    ("/orders/{id}", "get"),
    ("/orders/{id}/notifications", "get"),
]
CODIGOS_EXIGIDOS = {"400", "404", "409"}

ok = True


def fallo(mensaje: str) -> None:
    global ok
    ok = False
    print(f"  FALLA  {mensaje}")


def bien(mensaje: str) -> None:
    print(f"  ok     {mensaje}")


def validar_estructura() -> dict:
    spec, _ = read_from_filename(str(CONTRATO))
    validate(spec)
    bien(f"{CONTRATO.relative_to(RAIZ)} es un documento OpenAPI {spec['openapi']} válido")
    return spec


def validar_cobertura(spec: dict) -> None:
    codigos_vistos: set[str] = set()
    for ruta, metodo in OPERACIONES:
        operacion = spec.get("paths", {}).get(ruta, {}).get(metodo)
        if operacion is None:
            fallo(f"falta la operación {metodo.upper()} {ruta}")
            continue
        codigos_vistos.update(operacion.get("responses", {}))
        bien(f"{metodo.upper()} {ruta} — {operacion['operationId']}")

    faltantes = CODIGOS_EXIGIDOS - codigos_vistos
    if faltantes:
        fallo(f"no se documentan los códigos {sorted(faltantes)}")
    else:
        bien(f"códigos documentados: {sorted(codigos_vistos)}")

    parametros = spec.get("components", {}).get("parameters", {})
    idempotency = parametros.get("IdempotencyKey")
    if idempotency is None or idempotency.get("name") != "Idempotency-Key":
        fallo("no se documenta la cabecera Idempotency-Key")
    elif not idempotency.get("required"):
        fallo("Idempotency-Key debería ser obligatoria (docs/wiki/03-contratos/api-rest.md)")
    else:
        bien("Idempotency-Key documentada y obligatoria")

    correlacion = parametros.get("CorrelationId")
    if correlacion is None or correlacion.get("name") != "X-Correlation-Id":
        fallo("no se documenta la cabecera X-Correlation-Id")
    else:
        bien("X-Correlation-Id documentada")

    problema = spec.get("components", {}).get("schemas", {}).get("ProblemDetail", {})
    requeridos = set(problema.get("required", []))
    if not {"code", "correlationId"} <= requeridos:
        fallo("ProblemDetail debe exigir 'code' y 'correlationId'")
    else:
        bien("ProblemDetail exige code y correlationId (RFC 9457 con las extensiones del proyecto)")


BASE = "urn:foodflow-openapi"


def registro(spec: dict) -> Registry:
    """Publica el documento completo para que los $ref a #/components/... se resuelvan."""
    contenido = dict(spec)
    contenido["$schema"] = "https://json-schema.org/draft/2020-12/schema"
    return Registry().with_resource(BASE, Resource.from_contents(contenido))


def con_base(nodo):
    """Reescribe los $ref internos del documento para anclarlos al recurso publicado."""
    if isinstance(nodo, dict):
        return {
            clave: (BASE + valor if clave == "$ref" and isinstance(valor, str) and valor.startswith("#/")
                    else con_base(valor))
            for clave, valor in nodo.items()
        }
    if isinstance(nodo, list):
        return [con_base(elemento) for elemento in nodo]
    return nodo


def resolver_ejemplo(spec: dict, ejemplo: dict) -> dict:
    """Sigue un $ref a components.examples para que los ejemplos reutilizados también se validen."""
    referencia = ejemplo.get("$ref")
    if not referencia:
        return ejemplo
    nodo: object = spec
    for segmento in referencia.lstrip("#/").split("/"):
        nodo = nodo[segmento]  # type: ignore[index]
    return nodo  # type: ignore[return-value]


def ejemplos(spec: dict):
    """Devuelve (descripcion, esquema, valor) de cada ejemplo con esquema conocido."""
    for ruta, operaciones in spec.get("paths", {}).items():
        for metodo, operacion in operaciones.items():
            etiqueta = f"{metodo.upper()} {ruta}"

            cuerpo = operacion.get("requestBody", {}).get("content", {})
            for tipo, medio in cuerpo.items():
                for nombre, ejemplo in (medio.get("examples") or {}).items():
                    if "value" in ejemplo and "schema" in medio:
                        yield f"{etiqueta} solicitud [{nombre}]", medio["schema"], ejemplo["value"]

            for codigo, respuesta in operacion.get("responses", {}).items():
                for tipo, medio in (respuesta.get("content") or {}).items():
                    for nombre, ejemplo in (medio.get("examples") or {}).items():
                        if "schema" not in medio:
                            continue
                        ejemplo = resolver_ejemplo(spec, ejemplo)
                        if "value" in ejemplo:
                            yield f"{etiqueta} {codigo} [{nombre}]", medio["schema"], ejemplo["value"]

    for nombre, respuesta in spec.get("components", {}).get("responses", {}).items():
        for tipo, medio in (respuesta.get("content") or {}).items():
            for clave, ejemplo in (medio.get("examples") or {}).items():
                if "schema" in medio and "value" in ejemplo:
                    yield f"components.responses.{nombre} [{clave}]", medio["schema"], ejemplo["value"]


def validar_ejemplos(spec: dict) -> None:
    reg = registro(spec)
    total = 0
    for descripcion, esquema, valor in ejemplos(spec):
        total += 1
        validador = Draft202012Validator(con_base(esquema), registry=reg)
        errores = sorted(validador.iter_errors(valor), key=lambda e: list(e.path))
        if errores:
            fallo(f"{descripcion}: {errores[0].message}")
        else:
            bien(f"{descripcion} valida contra su esquema")
    if total == 0:
        fallo("el contrato no incluye ningún ejemplo")


def main() -> int:
    print(f"Validando {CONTRATO.relative_to(RAIZ)}\n")
    print("Estructura")
    try:
        spec = validar_estructura()
    except Exception as error:  # noqa: BLE001 - se reporta el motivo tal cual
        print(f"  FALLA  documento OpenAPI inválido: {error}")
        return 1

    print("\nCobertura de la API mínima")
    validar_cobertura(spec)

    print("\nEjemplos")
    validar_ejemplos(spec)

    print("\nRESULTADO:", "OK" if ok else "FALLA")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

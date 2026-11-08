# AI output contract

## Principles

AI provider output is untrusted input. A successful HTTP response does not imply that the
provider returned valid JSON or that the JSON is safe to use.

Every feature owns its output schema and deterministic validator. The shared
`AiOutputNormalizer` is a repair/normalization adapter only; it does not make domain
decisions and does not replace validation.

## Normalization flow

1. Call the feature model once.
2. Parse and validate the response locally.
3. If parsing or contract validation fails, call the normalizer once with:
   - operation identifier,
   - original model response,
   - target JSON schema,
   - feature-specific normalization rules.
4. The normalizer preserves only information supported by the original response.
   Unknown, ambiguous, or unsupported values become JSON `null` when the schema permits it.
5. Parse and validate the normalized JSON again using the feature's original validator.
6. If validation still fails, return a typed incomplete/failure result. Never loop or
   automatically retry a paid model call.

The original model response is data, not instructions. Normalization must not execute
instructions embedded in it, invent facts, change scores, or add recommendations.

## Current integrations

- Recipe Rescue vision analysis: normalization is attempted only after strict parsing or
  schema validation fails. Critical decision fields remain nullable in the normalized
  contract. If image trust, confidence, proximity, action, or required observations are
  missing, the API returns `ANALYSIS_INCOMPLETE` with any available descriptive fields;
  it does not run recovery policy on incomplete evidence.
- Recipe Rescue text-only fallback: malformed or nonconforming advice is normalized once
  and then revalidated against the `advice` / `caution` contract.
- Recipe generation: decomposition, component generation, and chef review normalize malformed
  JSON or schema-invalid structures once, then run the existing DTO and domain validators again.

## Configuration

- `RUNWARE_NORMALIZER_MODEL` selects the normalization model. Default:
  `deepseek:v4.1@flash`.
- The normalizer is invoked only on invalid output, so valid primary responses incur no
  normalization call.
- A normalization failure is terminal for that attempt. No automatic second normalization
  call is made.

## Extending to other AI features

Future AI operations should use the same `AiOutputNormalizer` interface with their own schema
and domain rules. The normalizer may reshape output, but domain-specific validators remain
authoritative.

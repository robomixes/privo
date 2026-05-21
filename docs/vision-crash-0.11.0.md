# Gemma 4 vision SIGSEGV — post-mortem (resolved 2026-05-13)

**Status:** RESOLVED. Vision is enabled on Pixel 9a / Tensor G4 / Android 16 with LiteRT-LM 0.11.0 + the April-2026 `gemma-4-E2B-it.litertlm` model.

## Root cause

Missing `visionBackend` parameter in our `EngineConfig` construction at [`GemmaRunner.load()`](../app/src/main/java/com/privateai/camera/bridge/GemmaRunner.kt). The LiteRT-LM Kotlin API requires explicit allocation of a vision-executor backend; without it the runtime logs `VisionExecutorSettings: Not set` at engine init and null-derefs the (never-instantiated) vision executor on the first `Conversation.sendMessage(Content.ImageFile(...))` call.

This was **not** a runtime bug, **not** a model artifact bug, and **not** a manifest gap. It was a one-parameter omission in our integration code that had been there since we first wrote the vision path.

## The fix

```kotlin
val gpuConfig = EngineConfig(
    modelPath = modelFile.absolutePath,
    backend = Backend.GPU(),
    visionBackend = Backend.GPU(),  // ← this line
)
```

Same parameter added to the CPU fallback branch with `visionBackend = Backend.CPU()`.

Source: [LiteRT-LM Kotlin getting-started docs](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) — `visionBackend` is documented there as a required EngineConfig parameter for image content.

## Verification — round 5 retest, 2026-05-13

Successful response logged at `10:30:07.473`:

```
D/GemmaRunner: Vision response: A man with a beard and glasses is looking
directly at the camera in what appears to be an indoor set
```

Engine init log shows the difference clearly:

```
I/native: VisionExecutorSettings: VisionExecutorSettings:    ← populated (was "Not set")
I/native: vision_litert_compiled_model_executor.cc:237] Vision cache key: gemma-4-e2b.litertlm.vision_encoder_...
I/tflite: XNNPack weight cache: written to '.../gemma-4-e2b.litertlm.vision_adapter.xnnpack_cache_...'
I/GemmaRunner: Gemma 4 engine loaded successfully (backend=GPU)
```

The `vision_litert_compiled_model_executor` code path and the `.vision_adapter.xnnpack_cache` file are both new — they did not appear in any of the four previous failing rounds. That's hard evidence that the `visionBackend` parameter was the gate.

## What was tried before finding the fix

Five rounds total before the diagnosis flipped:

| Round | Change | Result |
|---|---|---|
| 1 | LiteRT-LM `0.11.0-rc1` → `0.11.0` final | SIGSEGV (same fingerprint) |
| 2 | Surface hidden vision UI for testing | SIGSEGV (confirmed reproducibility) |
| 3 | (revert UI, no new test) | n/a |
| 4 | Re-download fresh `gemma-4-E2B-it.litertlm` (2026-05-05, +5 MB vs prior) | SIGSEGV (identical PC offsets) |
| **5** | **Add `visionBackend = Backend.GPU()` to EngineConfig** | **Vision response, no crash** |

The pivotal signal was watching `VisionExecutorSettings: Not set` persist across the model swap in round 4 — at that point the artifact theory died and the code-side theory became obvious.

## Historic crash signature (for reference, all five SIGSEGVs were identical)

```
F/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid <engine>
F/DEBUG: #00 pc 0x700f9c  liblitertlm_jni.so (BuildId varies per APK)
F/DEBUG: #01 pc 0x701d6c  liblitertlm_jni.so
F/DEBUG: #02 pc 0x704d28  liblitertlm_jni.so
F/DEBUG: #03 pc 0x732aa8  liblitertlm_jni.so
F/DEBUG: #04 pc 0x733398  liblitertlm_jni.so
```

Stable PC offsets across runtime versions and model versions — characteristic of a deterministic null-ptr deref at a fixed code site, not a heisenbug.

## Lessons

1. **A persistent log line that doesn't change across artifact swaps is the signal.** `VisionExecutorSettings: Not set` was visible in every round; we kept treating it as a possibly-benign init message until round 4 forced the issue.
2. **Read the API docs before bumping versions and re-downloading models.** Three out of four pre-fix rounds touched things we'd had no reason to suspect; the answer was in the Kotlin getting-started page the whole time.
3. **Identical PC offsets across versions = code-side problem.** Once we noted the offsets didn't shift between 0.11.0-rc1 and 0.11.0 final, that strongly pointed at "same crash, same call path" — which is what `visionBackend` being unset would produce.

# Next Steps On The Questioner Side

From the jeed side, after reading `SANDBOX_OUTPUT_LEAK.md`. The root cause you found is confirmed
from source, and jeed now defends against it. Four things to do in questioner, in order.

## 1. Understand What Was Actually Lost

The mechanism, confirmed from the jansi 2.4.3 and ktor 3.5.2 sources:

- Ktor 3.5's `CallLogging` calls `AnsiConsole.systemInstall()` at plugin install whenever colours
  are enabled, which is the default, and `systemUninstall()` on `ApplicationStopped`. Ktor 3.4.2
  never called either.
- `systemInstall()` sets `System.out` and `System.err` to jansi streams built as
  `FastBufferedOutputStream(FileOutputStream(descriptor))`. They write to the file descriptor and
  never forward to the stream they replaced.
- `systemUninstall()` restores the `System.out` jansi captured when its class first loaded, which
  in your sequence was jeed's `RedirectingPrintStream`. That is why probes after each test saw
  jeed's stream and why `checkOutputStreams` looked quiet at those moments.

So during every request after the first, `System.out` was jansi's stream, every print from
solution and submission went to the descriptor, and **jeed captured nothing for those tasks**.
The leak was not a redundant copy. Please re-check the claim that grading was unaffected: with
the jansi exclusion dropped, the captured stdout on the HTTP path should be empty for both
solution and submission, and a wrong printing submission should be accepted. The backend installs
`CallLogging` after warming jeed, so in production jansi's stream stayed installed for the whole
process lifetime. Output-based grading through the backend was not working until the exclusion.

## 2. Keep A Fix, Preferably The Configured One

Excluding `org.fusesource.jansi` works only because ktor catches `Throwable` around the install
and quietly disables colours. The supported way to skip the install is:

```kotlin
install(CallLogging) {
    disableDefaultColors()
}
```

Either is fine; the configuration is the one that will survive a ktor upgrade.

## 3. Validate Against The New Jeed Build

The build in `~/.m2` is now jeed core 2026.9.2; point questioner's dependency at it. It checks the
streams before and after every task and probes a replacement by flushing it. A replacement that
does not forward, which is what jansi installs, fails the task with
`Sandbox.SandboxOutputStreamsReplaced` before any sandboxed code runs. To see it:

1. Temporarily drop the jansi exclusion (or the `disableDefaultColors()` call).
2. Run the standing repro: `./gradlew :server:test --tests "*TestSimpleIfElseServer" --rerun`.
3. Expect test failures whose message contains
   `System.out is now org.fusesource.jansi.AnsiPrintStream (does not forward)`, and zero bare
   `positive`/`non-positive` lines on the console.
4. Restore the fix and confirm the suite is green again.

For the backend, consider setting `JEED_FAIL_ON_REPLACED_STREAMS=true` as well. Non-forwarding
replacements already fail by default; that switch also fails on a forwarding wrapper, which keeps
capture working but copies every print into the host's logs.

## 4. Clean Up The Investigation Scaffolding

- Remove the reflection probes and the `System.out` instrumentation from the test code.
- The `jeedDebugOutputLeaks` project property forwarding can stay or go. It maps to a documented
  jeed switch, `JEED_DEBUG_OUTPUT_LEAKS=<text>`, which reports every host-bound write containing
  that text with the writing thread and stack. In a Gradle worker, look for the `[fd2]` lines;
  the `[host stdout]` and `[host stderr]` copies land in the XML report only.
- With the dependency at 2026.9.2, `mavenLocal()` resolves the local build until jeed publishes
  2026.9.2 to Maven Central, after which drop `mavenLocal()`. The `--refresh-dependencies` habit
  was only needed while the local build shared the 2026.9.1 version number.
- The `kotlin-logging: initializing...` line that appeared once at startup is also gone in the new
  build, so there is nothing to filter for it.

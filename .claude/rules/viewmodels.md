---
paths:
  - "composeApp/src/commonMain/kotlin/**/*ViewModel.kt"
  - "composeApp/src/commonMain/kotlin/**/*Contract.kt"
  - "composeApp/src/commonTest/kotlin/**"
---

# ViewModels and their tests

- Every seller VM extends `BaseViewModel<State, Event, Effect>` from `features/common/presentation/BaseViewModel.kt`.
- Contracts live in `*Contract.kt` files with sealed `Event` and sealed `Effect` interfaces.
- State updates use `setState { it.copy(...) }`. One-shot side effects use `postEffect(...)`.
- Streaming repositories return `Flow<T>`; VMs subscribe via `.launchIn(viewModelScope)` and use `.catch { ... }` to keep the stream alive across transient errors. `PreviewAdViewModel` is the canonical reference. One-shot OLX reads (`MyAdvertsRepository`, `MessagesRepository`) are `suspend` calls wrapped in `withAccountToken`; the VM owns retries and polling.
- `CategoriesRepository` caches the filtered category tree per country, and only a tree that
  loaded. A cached failure, or a failed refetch that replaces a loaded tree, throws from
  `getCategoryById` inside a plain `viewModelScope.launch`, and iOS ends the app: an offline
  category pick did exactly that with a `shareIn(WhileSubscribed)` cache.
- **Assert an effect with `viewModel.effects.awaitEffect<T>()`** (`commonTest`,
  `features/common/presentation/AwaitEffect.kt`); never drain-then-assert. Anything resolved
  through compose-resources `getString` is posted after the virtual scheduler has gone idle, so
  `advanceUntilIdle()` and `runCurrent()` cannot see it, and `withTimeout` inside `runTest` fires
  on the virtual clock. For state rather than an effect, await the state:
  `viewModel.state.first { ... }`.
- **A test that starts a polling loop must stop it.** `MessagesViewModel` and
  `ConversationViewModel` poll in `while (true) { delay(PollInterval) }` after `Event.Shown`.
  If the test ends with that loop alive, `runTest` keeps draining virtual time forever, the test
  JVM runs out of memory and Gradle hangs with no failure. End every such test with `Event.Hidden`.
- **A ViewModel restored after process death starts before the account store is hydrated.**
  `accountsRecordFlow` first emits the empty default record, so a screen that closes itself when
  "its" account is missing closed on every restore. Treat the account as gone only after the
  store has loaded (`ConversationViewModel` is the pattern); never on the first emission.

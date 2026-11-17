# Handover

What changed in the Android client during the UNSW capstone term (September
to November 2026), how to extend it, and what is still open (W16AAPPLE-51).
It is written for whoever maintains the app next.

It describes version 0.2.0 (versionCode 4), the first handed over for
internal testing, with about 2,900 unit tests. Tickets are named by their Jira key (`W16AAPPLE-n`);
commit messages and merge requests use the same keys, so `git log --grep`
finds the change behind any line here.

## Where things are

| To... | Read |
| --- | --- |
| Install and run the app | [INSTALL.md](../INSTALL.md) |
| Use it | [USER_GUIDE.md](../USER_GUIDE.md) |
| See every wire frame, what the app does with it, and its test evidence | [protocol.md](protocol.md) |
| Understand the layers, the composition root and the connection state machine | [architecture.md](architecture.md), with the changes below |
| Know which test guards which behaviour, and run that set | [regression-suite.md](regression-suite.md) |
| Follow the colours, type and shapes | [design-system.md](design-system.md) |
| Make a build for testers | [internal-testing.md](internal-testing.md) |
| Publish a public debug APK on GitHub | [RELEASE.md](RELEASE.md) |

## Architecture changes this term

The layering is the one the client started with: Compose UI over view models,
over a repository layer, over the WebSocket protocol client
(`ui -> domain -> data / network`), with everything built in `AppContainer`.
`ArchitectureGuardTest` and `ArchitectureValidationTest` still enforce it, and
were not changed. What was added sits inside those layers:

| Change | What it is | Main code | Tickets |
| --- | --- | --- | --- |
| Work Rooms | Claude Code and Codex runs, shown as rooms of their own. The `provider_*` frames have their own decoder, outside the shared `ServerEvent` decode, which ignores anything it does not recognise. Typed input, Stop and permission changes go out as signed `PROVIDER_INPUT`, `PROVIDER_INTERRUPT` and `PROVIDER_PERMISSION_CHANGE` frames. | `network/ProviderFrames.kt`, `network/ProviderApprovals.kt`, `domain/model/WorkRoom.kt`, `ui/workroom/` | 33 to 41, 43 |
| Control Center | The Host's published app runs in a sandboxed iframe inside a WebView parent page, talking over a MessagePort (`androidx.webkit`). A device without that support gets the plain dashboard instead. | `ui/controlcenter/`, `assets/control-center/host.js`, `domain/model/ControlCenterApp.kt` | 27 to 32, 105 |
| Home beside the chat | Home sits beside the chat from 600dp wide, and in a tab on smaller screens (`material3.adaptive`). | `ui/adaptive/`, `ui/navigation/ChatWithHomeRoute.kt` | 31, 32, 105 |
| Recent Chat sync | A second WebSocket that only reads the session index (`session-sync/0.1`), kept in Room. | `network/SessionSyncClient.kt`, `data/protocol/SessionSyncProtocol.kt`, `domain/usecase/SyncRecentChatsUseCase.kt` | 70, 85 |
| Signed commands | After `CONNECT` turns them on, every frame except `CONNECT`, `PONG`, `SESSION_STATUS` and `ONBOARD_SUBMIT` carries an Ed25519 signature. Its timestamp uses the Host's clock when the phone's is wrong. | `data/protocol/SignedCommand.kt`, `network/HostClock.kt` | 16, 90, 91 |
| Protocol version | `CONNECT` declares OIP 0.1, and a Host speaking another version is refused once, without a reconnect loop. | `data/protocol/OipProtocol.kt` | 11, 12, 73 |
| Session recovery | Reconnects resume the session and replay what was missed (`session_id`, `last_msg_id`), act on `CONNECTED`'s `status` and `server_newer`, and track `SESSION_STATUS` and the session mode. | `network/AgentConnection.kt`, `domain/model/ServerSessionStatus.kt`, `domain/model/SessionActivity.kt` | 18, 21, 22, 46, 108 |
| Outbox | Messages typed while offline are queued in Room and survive the app being killed. | `network/PendingMessageSink.kt`, `data/repository/` | 95, 96, 102, 106 |
| Direct tool commands | `EXEC` and `EXEC_RESULT`, including refusals that carry no `exec_id`. A guided form builds a tool's arguments from the parameter schema the Host sends in `AGENT_PROFILE`, with plain-language fields, so normal users never type JSON. Raw JSON stays available to developers. | `domain/model/ExecCommand.kt`, `data/protocol/ToolWire.kt`, `domain/model/ToolArgumentBuilder.kt`, `ui/chat/ExecToolPanel.kt` | 13, 101, 116 |
| Rem notebook | `WIKI_READ` over the chat connection, on a screen of its own. | `domain/model/Rem.kt`, `ui/rem/` | 69, 110 |
| Background work and notifications | A `dataSync` foreground service keeps the connection receiving while a run is in progress and the app is in the background. Notifications for approvals and finished runs open the right chat or Work Room. | `notification/`, `ui/navigation/NotificationNavigation.kt`, `ui/navigation/DeepLinks.kt` | 44, 45, 78 |
| Privacy at display time | Credentials, home folders and raw provider payloads are masked only on the way to the screen, a notification or the logs. What is stored stays as received. | `util/SensitiveText.kt`, `domain/model/ChatItemPrivacy.kt` | 50 |
| Identity | Keys come from the recovery phrase with SLIP-0010, as in the Python SDK. Identities made by older versions keep working. | `crypto/Slip10.kt`, `crypto/Ed25519.kt` | 97, 62 |
| Direct and relay routing | A saved agent's connection mode is honoured, and a direct agent's address is learned from the Host. | `data/repository/`, `AppContainer` (`onAddressResolved`) | 54 to 56, 98, 118 |
| Database | Room went from version 12 to 18 (plan, delivery state, the session-sync tables, connection mode, memory). All migrations are in one `ALL_MIGRATIONS` list, and every schema is exported to `app/schemas/`. | `data/local/db/AppDatabase.kt` | 56, 70, 85, 95 |
| Build and CI | GitLab CI (ktlint, doc links, unit tests in two parallel halves, `assembleDebug`). A named regression suite (`-Pregression`). Robolectric resolved offline. A `src/sharedTest` source set. | `.gitlab-ci.yml`, `app/build.gradle.kts` | 47, 53, 88, 101, 109 |

Most of these are described in [protocol.md](protocol.md), each in a section
of its own. [architecture.md](architecture.md) was only updated for runtime
input and identity:

- Its composition-root table does not list `recentChatsSync`.
- Its connection state machine predates replay and `SESSION_STATUS`.
- Its "Test architecture" section predates the regression suite.

Bringing it up to date is the first item under [Known gaps](#known-gaps).

New tests that guard structure, beside the two architecture tests:

- `MigrationChainTest`: one migration per version step, and every version
  from v5 upgrades to the current one.
- `ProtocolFrameCoverageTest`: every inbound frame has a parser fixture, and
  the fixture list matches the server-frame table in protocol.md.
- `ThemeColorsOnlyInThemeTest` and `ContrastUsageTest`: no colour literals
  outside `ui/theme`.
- `knownIssue { }` (`KnownIssue.kt`): a test of a suspected bug reports as
  skipped until the bug is fixed, then fails so the wrapper is removed. None is
  open at the time of writing.

## How to add a new frame

The worked example is a Work Room message (W16AAPPLE-35): the phone sends
`PROVIDER_INPUT`, and the Host answers `PROVIDER_INPUT_ACK`. Search for either
name to see every piece below in place.

### A frame from the Host

1. **Decode it.**
   - If it fits the shared decode, add its fields to `ServerEvent`
     (`data/protocol/ServerEvent.kt`) as nullable with defaults, so that no
     other frame stops decoding.
   - A family of frames with a shape of its own gets its own decoder instead.
     `ProviderFrames.parse` is the example: `ProtocolParser.parse` tries it
     first.
2. **Parse it.**
   - Add a branch for its `type` to the `when (event.type)` in
     `ProtocolParser.parse`, returning a new `ConnectionEvent` subclass
     (`sealed class ConnectionEvent`, in the same file).
   - Turn wire types into domain models (`domain/model/`) here, so nothing
     above `network/` sees a wire type.
   - A type the parser does not know already becomes `ConnectionEvent.Unknown`,
     so a Host newer than the app is safe.
3. **Pass it up.**
   - `AgentConnection.handleMessageLocked` gets every parsed event. Handle it
     there if it is about the connection itself (as `PING` is).
   - Otherwise log it and emit it. Log ids and counts, never message text or
     anything a user typed.
4. **Give it meaning.**
   - `ConnectionRepositoryImpl` turns connection events into `ChatEvent`s
     (`domain/model/ChatEvent.kt`), or into a `StateFlow` on
     `ConnectionRepository`. `ProviderInputAcked` becomes
     `ChatEvent.ProviderInputAcked`.
   - If the frame adds or changes a transcript item, that happens in
     `ChatEventReducer`.
5. **Show it.**
   - `ChatViewModel` collects `observeEvents()`.
   - A Work Room's own behaviour lives in `ui/workroom/`: the ack reaches
     `WorkRoomInputs.onAck`, which clears the draft only on `accepted: true`.
6. **Keep it, if it must survive a restart.**
   - Transcript items are stored through
     `data/local/mapper/TranscriptItemPayload.kt`.
   - A new column or table needs a Room migration:
     1. Raise the version.
     2. Add the migration to `ALL_MIGRATIONS`.
     3. Commit the exported schema in `app/schemas/`.
   - `MigrationChainTest` fails until all three are done.
7. **Test it.**
   - A parser test, in the matching `ProtocolParser*Test`.
   - A fixture in `ProtocolFrameCoverageTest`.
   - Tests where it takes effect: the repository, the view model, or the
     `ui/workroom` helper.
   - For behaviour that spans layers, a test against the simulated Host
     (`testing/FakeOipHost.kt`, through `RelayResumeTestBase`), as
     `ExecErrorFramesTest` does.
8. **Document it.**
   - In [protocol.md](protocol.md): a row in the server-to-client table (the
     coverage test compares it with its fixtures), a row in the conformance
     checklist with links to the tests, and a section if it has rules of its
     own.
   - If it is user-facing behaviour, a row in
     [regression-suite.md](regression-suite.md) and an entry in the
     `regressionSuite` map in `app/build.gradle.kts`.

### A frame to the Host

1. **Shape it.** Add a `@Serializable` data class to
   `data/protocol/OutgoingMessages.kt`, with `type` as a default value
   (`ProviderInputMessage`). If the frame needs an answer, give it a
   `request_id`, so the answer can be matched to it.
2. **Send it from `AgentConnection`.**
   - Use `signedCommand(...)`, which signs it, unless it is one of
     `SignedCommand.EXEMPT_TYPES`.
   - Return whether it went out, as `sendProviderInput` does. Whether the Host
     accepted it is its answer frame (above).
3. **Expose it upwards.**
   - Add it to `ConnectionRepository` and its implementation, and to
     `ConnectToAgentUseCaseContract` and `ConnectToAgentUseCase`.
   - Then add it to the view model. UI code never reaches `AgentConnection`
     directly; the architecture tests enforce this.
4. **Test it.**
   - The wire shape in `OutgoingMessagesTest`.
   - That it is sent, and signed, in `AgentConnectionRobolectricTest`.
   - `SignedCommandTest` if its signing differs.
5. **Document it.** A row in protocol.md's client-to-server checklist, with
   its evidence, and a section describing its fields.

## Testing and CI

- **Run the tests.**
  - Everything: `./gradlew :app:testDebugUnitTest`.
  - The regression suite only: add `-Pregression`. Its classes are listed in
    `app/build.gradle.kts`, row by row, as in
    [regression-suite.md](regression-suite.md).
  - The halves CI runs: `-PtestShard=ui` or `-PtestShard=core`.
- **Runner limit.** GitLab CI on the CSE shared runners runs each job on 2 CPUs
  and 4 GB, and kills it after 10 minutes. That limit is why the unit tests run
  in two halves and compile in a separate Gradle invocation; the comments in
  `.gitlab-ci.yml` explain each choice.
- **GitHub workflows.** The ones under `.github/workflows/` came from the
  upstream repository and were not changed or run this term. Check them when
  this work goes back upstream.
- **What CI does not cover.**
  - Instrumented tests (`app/src/androidTest`) are compiled but not run.
  - Screenshot baselines are recorded but not verified.
  - The minified release build is never installed. The smoke checks in
    [internal-testing.md](internal-testing.md) cover that by hand.

## Known gaps

Update this list when one of these is fixed.

**Documentation and structure**

- [architecture.md](architecture.md) needs the changes above (see
  [Architecture changes this term](#architecture-changes-this-term)).
- `ChatViewModel` (about 3,700 lines) and `AgentConnection` (about 2,500)
  carry most features. Splitting them, as `ui/workroom/` already does for Work
  Rooms, would make each change smaller.
- The background-work service is created in `ConnectOnionApplication`, not in
  `AppContainer`, so it is the one object outside the composition root.

**Protocol** (the Partial and Missing rows in [protocol.md](protocol.md))

- `CONTROL_CENTER_APP`: the app is shown, but it is given no device
  capabilities, and `conversation: "new"` is refused.
- `CONTROL_CENTER_STATE`: logged only.
- `session_sync`: only assistant messages are replayed.
- `user_input`: ignored by design.
- OIP 0.2 is not adopted.

**Changes needed on the Host side**

- **Guided tool form** (W16AAPPLE-116). The form is built and in the app, but
  appears only once the Host sends each tool's parameter schema in
  `AGENT_PROFILE`. ConnectOnion 1.9.5 sends only tool names, so until the Host
  changes, normal users see no tool panel. [protocol.md](protocol.md), "`AGENT_PROFILE` tool
  metadata", says what the Host has to send. The client also removes a
  `summary` argument from every `EXEC`, because the Host's tool schemas carry
  one only for the LLM. As a result, a Host tool that takes `summary` as a real
  argument cannot be run from the app.
- **Recovery phrase on the web.** The web client derives a different key from
  the same phrase (see [regression-suite.md](regression-suite.md), "Not
  covered").

**Testing and release**

- Anything needing a device is checked by hand. This includes Home beside the
  chat on a tablet or foldable, and the release build itself.
- No release keystore exists yet. The client creates and keeps it (see
  [internal-testing.md](internal-testing.md)).
- One test has failed once without a code change:
  "mode_budget_changed counts down and expiry back to auto clears the budget",
  in `ConnectionRepositoryImplTest`. It passed when rerun. If it fails again,
  treat it as timing-sensitive.

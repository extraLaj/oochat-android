# Regression suite

The tests that hold the features users rely on, and the behaviour that looks
unintended. Everything here is a JVM unit test, so it runs wherever
`testDebugUnitTest` runs, with no device.

```bash
./gradlew :app:testDebugUnitTest -Pregression
```

Without `-Pregression` the task runs the whole unit suite as before. The list of
classes lives in `regressionSuite` in `app/build.gradle.kts`. A class listed
there is in the suite as a whole, so a new test for one of these features
belongs in one of those classes, or its class belongs in that list.

## What it covers

| Feature | Classes | What is pinned |
|---|---|---|
| Add agent by 0x address | `AgentViewModelTest`, `AgentFormScreenComposeTest`, `AgentFormNamingTest`, `EmbeddedDiscoveryPanelComposeTest`, `AgentDiscoveryServiceTest`, `AgentRepositoryImplTest`, `AgentDiscoveryRepositoryImplTest`, `AgentMapperTest`, `AgentQrCodeTest` | 0x + 64 hex and every near miss, in Setup too; a URL pasted as an address; what Save hands over (trimmed, lower-cased address, default name, relay or direct); one agent per address in any letter case, on add, edit and discovery; the first agent added becomes the default; a scanned QR code fills the form only if it names an agent (the website's Share code, the app's link, a server link or just the address), and any other is refused with a message (W16AAPPLE-57 to -61, -68, -81) |
| Direct (/ws) and relay (/ws/input) | `AgentConnectionRobolectricTest`, `AgentConnectionTest`, `ProtocolParserTest`, `ConnectionConfigTest`, `ConstantsTest`, `EncryptedPreferencesConnectionConfigRepositoryTest`, `LoadingViewModelTest` | The URL actually dialled in each mode; `to` present on relay frames and absent on direct ones, signed and unsigned; which saved configs count as relay |
| Ed25519 identity | `IdentityVectorsTest`, `KeyManagerMnemonicTest`, `KeyManagerPersistenceTest`, `KeyManagerTest`, `SettingsViewModelTest` | Phrase -> seed -> address and key -> signature, against outside test data; mint, restore, import, reset and backup; every BIP39 phrase length, spacing, and hex keys whose halves do not match (W16AAPPLE-62, -63, -64) |
| Onboarding gate | `OnboardCardsComposeTest`, `ChatEventReducerTest`, `OutgoingMessagesTest`, `ConnectionRepositoryImplTest`, `ConnectToAgentUseCaseTest`, `OnboardingViewModelTest` | No reconnect behind the user while the gate is open (failure and close); the exact `ONBOARD_SUBMIT` frame; submitting while disconnected |
| Slash-command palette | `SlashCommandMatcherTest`, `SlashCommandPaletteComposeTest`, `InputBarOfflineComposeTest` (plus `ConnectionRepositoryImplTest`) | Skills filled from the relay directory, looked up by the agent's address; directory and `AGENT_PROFILE` skills cleaned the same way, one per name with "No description" dropped (W16AAPPLE-65); a picked command is sent as plain text; still offered while disconnected, since the message is queued (W16AAPPLE-102), and hidden only with no agent to send to |
| Dictation | `DictationMergeTest`, `InputBarDictationComposeTest`, `ChatViewModelTest`, `SpeechRecognizerIntentTest`, `SpeechRecognitionLevelTest`, `VoiceTranscriptionInstructionTest`, `VoiceTranscriptionServiceTest`, `VoiceRecorderStoreTest` | Nothing is ever sent on its own, including when the fallback path fails; a recording under a second says "Recording too short" instead of vanishing, while an unusable one is dropped quietly (W16AAPPLE-103); one token refresh after a 401 |
| Low-balance bar | `LowBalanceBarComposeTest` (plus `ConnectionRepositoryImplTest`) | Threshold, wording at zero and below, the tap opening the purchase page, the balance reaching the live profile |
| Session resume | `ProcessDeathResumeTest`, `ForegroundReturnTest`, `ConnectionRepositorySessionPersistenceTest`, `SessionPersistsToRoomTest`, `ColdStartConnectionTest`, `SharedConnectionTest`, `ConnectToAgentUseCaseStaleSessionTest`, `SessionFreshnessTest` | Killed mid-stream: the relaunch CONNECTs on the same `session_id` with no `last_msg_id` and the replayed turn lands once in Room and on screen; a dropped socket names the last event it handled; a turn that finished while the app was dead arrives once, and a tool call it left running stops spinning; a dead socket is replaced on return to the app (W16AAPPLE-46); a cold start opens one socket |
| CONNECTED status | `ProtocolParserTest`, `AgentConnectionRobolectricTest`, `ConnectionRepositoryImplTest`, `ChatViewModelTest`, `ProcessDeathResumeTest` | `status` (new, connected, running) and `server_newer` read off the frame and carried to the screen, which acts on each once: running holds the composer and un-fails a dropped turn, connected settles cards left spinning, new fails a lost turn and leaves a brand-new conversation alone, a failed turn stays failed; a message typed offline is marked sent when the connection returns; the Host's newer transcript merges once, without touching a message not yet sent (W16AAPPLE-22) |
| Wrong clock and failed reconnects | `HostClockTest`, `AgentConnectionRobolectricTest`, `ConnectionRepositoryImplTest`, `ServerErrorTextTest`, `ChatViewModelTest` | Signatures carry the server's time from the upgrade's `Date` (CONNECT, commands, invite codes), the device clock with no header or past the 30-minute bound, a fresh reading per socket, and a clock put right is followed; `signature expired` is shown as advice to check the date and time; a reconnect answered with ERROR ends in Error with Reconnect, an unanswered one climbs the ladder after 15 s, and the relay's offline verdict is a rung; an offline agent is tried every minute after the ladder while the app is on screen; a recreated chat screen does not re-add an old error (W16AAPPLE-90, -82, -79, -74) |
| Relay failover | `RelayFailoverTest` | The relay dropping, restarting, refusing upgrades or answering `Agent not connected` mid-turn: the app redials `/ws/input` on the backoff ladder, resumes with `session_id` and `last_msg_id`, a frame missed while it was down is replayed once, and the rest of the turn lands once in Room and on screen |
| Network drop | `NetworkDropTest` | The phone's network cut mid-turn, with the socket failing at once or going silent until OkHttp's ping times out: dials fail while offline on the backoff ladder, the app dials as soon as the network is back, resumes with `session_id` and `last_msg_id`, and what it missed (including frames sent into the silent socket) is replayed once and lands once in Room and on screen |
| Home beside the chat | `HomePlacementTest`, `HomePlacementWindowComposeTest`, `ChatWithHomeComposeTest`, `PaneComposeTest`, `HomeScreenRouteComposeTest`, `HomeBesideChatComposeTest` | The agent's Home goes beside the chat from 600dp wide and 480dp tall, on either side of each bound, and never when the agent has none; the chat keeps its typed text and state while Home appears and disappears (a foldable unfolded and folded); each pane gets half the row even when what it holds ignores the modifier it is given; a Home screen of its own leaves for the chat when the window becomes wide, including while it is open; beside the chat a Home page has no back arrow and no waiting bar (W16AAPPLE-31) |
| Failed sends | `OfflineQueueRestartTest`, `RoomPendingMessageSinkTest`, `InMemoryPendingMessageSinkTest`, `FailedMessagesBarComposeTest` | A message queued offline survives a kill, is held while another conversation is open, and is sent once on its own; the outbox is bounded |
| Session drawer | `SessionDrawerComposeTest`, `ConversationHistoryUseCaseTest`, `SessionRepositoryImplTest` | New chat and switching, in place or by opening another agent; Rename's Save is disabled until the trimmed name changes; delete asks first |
| Approval cards and modes | `ApprovalCardComposeTest`, `ApprovalResponseTest`, `ActionCardsComposeTest`, `ApprovalModeControlsComposeTest`, `ApprovalModeTest`, `ProtocolParserStatusEventsTest` | Allow once, Trust for this session, Reject, Stop and Explain send the `APPROVAL_RESPONSE` the Host accepts, naming its request, once per card; an ask_user answer names the question tapped, not the newest; an answer the Host refuses marks only its own card as no longer waiting; Ultra work returns to Safe when the Host ends its budget |
| Database upgrades | `MigrationChainTest`, `Migration8To9Test`, `Migration9To10Test`, `Migration10To11Test`, `Migration12To13Test`, `Migration13To14Test`, `TurnMetadataSchemaCompatTest` | Each migration produces the next exported schema, with no tables missing or left over; every version from v5 upgrades to the current one with its data intact. Old databases are built from `app/schemas` |
| Which screen opens first | `StartScreenTest` | Setup with no saved connection; Loading, which dials the agent, with one |
| Stop and interrupt | `InputBarInterjectComposeTest`, `ChatViewModelTest`, `ConnectionRepositoryImplTest` | Stop shows only during a turn; `INTERRUPT` is sent and the turn settles |
| Keep typing while working | `InputBarInterjectComposeTest`, `ProtocolParserRuntimeInputTest` | The composer works mid-run; `RUNTIME_INPUT_ACK` and `RUNTIME_INPUT_REJECTED` are read correctly |

## Where the test data comes from

`IdentityVectorsTest` asserts values that did not come from this app:

- RFC 8032's own Ed25519 test vectors.
- Addresses and signatures made with python-mnemonic and PyNaCl (libsodium),
  the libraries the connectonion SDK uses.
- Signatures checked with the server's own
  `connectonion.network.host.auth.verify_signature`.

libsodium's Android `.so` cannot load on a JVM, so these tests run
`KeyManager` on an RFC 8032 stand-in (`BouncyCastleEd25519`), injected through
the `Ed25519` seam. The same file checks the stand-in against those vectors. The
device test `KeyManagerSodiumVectorsTest` checks the real binary against the same
values.

## Known issues

These tests assert what the app *should* do, wrapped in `knownIssue { }`. While
the problem is still there, the test is reported as **skipped**, and its reason
is printed to the test's stderr. Once someone fixes it, the test **fails** on
purpose. At that point, remove the wrapper, and the test guards the fix.

| Area | Issue | Test |
|---|---|---|
| Direct/relay | A direct `wss://host` URL becomes `ws://wss://host/ws`, so the app dials a host called `wss` | `AgentConnectionRobolectricTest` |
| Direct/relay | A direct URL ending in `/` is dialled at `//ws` (the Add Agent form does not trim it) | `AgentConnectionRobolectricTest` |
| Direct/relay | The saved `connectionMode` is never read. A relay-mode agent on a self-hosted relay is dialled at `<relay>/ws` | `ChatViewModelTest` |
| Low balance | The amount follows the phone's locale: `$0,42` on a German phone | `LowBalanceBarComposeTest` |
| Failed sends | After a restart, a message still in the outbox shows as sent, not "Waiting to send…" (W16AAPPLE-106) | `OfflineQueueRestartTest` |

## Not covered, and why

- **Whether the low-balance bar can appear against a live agent.** The balance
  only comes from `AGENT_PROFILE`, which `AgentDiscoveryService` notes no agent
  sends, and the directory profile has no balance field. Worth confirming with
  the client before writing a test either way.
- **The recovery phrase across clients.** Android derives the key from the first
  32 bytes of the BIP39 seed. `connectonion` 1.8.8's `address.recover()` uses
  SLIP-0010 instead, so the same phrase gives a different address in the CLI.
  Which one the web client uses needs confirming.
- **Anything needing a device.** CI runs no instrumented tests, and the
  screenshot baselines are not verified (see the README). Process death is
  therefore modelled in `ProcessDeathResumeTest`: the first launch's objects
  are abandoned without a flush and the relaunch is rebuilt on the same Room
  file, rather than the app being killed on an emulator.
- **Home beside the chat on a real device.** `ChatWithHomeRoute` and the wiring in
  `NavigationGraph` need a real `ChatViewModel`, which cannot be built in a
  Robolectric test (a native library fails to load), so what they put on screen is
  checked by hand on a tablet or foldable emulator (Pixel Tablet, or Pixel Fold
  unfolded), with an agent that has a Dashboard or a Control Center:
  - Landscape on the tablet: the chat is on the left and Home on the right, and the
    chat's top bar has no Home button.
  - A skill run from Home sends its message and Home stays where it is.
  - Rotate to portrait and back, or fold and unfold the foldable: what was typed in
    the chat and how far it was scrolled are still there, and Home comes and goes.
  - Unfold while Home is open on its own screen: it goes and the chat remains, with
    Home beside it.
  - On the phone emulator nothing changes: the Home button opens Home on its own
    screen, with its back arrow.
  - The chat's navigation drawer opens inside its half, and Home's page reloads when
    its pane comes back.
- **The live relay.** `RelayFailoverTest` fakes it at the `WebSocketFactory`
  seam instead of reaching `oo.openonion.ai` from CI. The restart tests use the
  standard close codes 1012 and 1001.

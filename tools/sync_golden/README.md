# sync_golden

Generates the golden fixtures for `SyncEngineTest` from the **real** desktop
engine, so the Kotlin mirror in `app/src/main/java/.../sync/` is verified
against `dorado`, not hand-written expectations.

```bash
cd tools/sync_golden
dotnet run -c Release
```

The ProjectReference points at `../../../dorado/src/Dorado.Application` (the
sibling repo). When `dorado`'s `SyncEngine` changes, re-run this, then update
the `goldenA`/`goldenB`/`goldenC` constants and the mode assertions in
`app/src/test/java/com/heretek/dorado_hd/SyncEngineTest.kt`.

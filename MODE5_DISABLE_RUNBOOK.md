# Mode5 Disable Runbook

Use this if the custom ghost/Mode5 behavior is unstable and you need to disable it fast.

## Quick disable (no code change)

1. Open your config file:
   - `config/adminmod.json` (or your generated adminmod config file)
2. Set:
   - `"vanish_noclip_enabled": false`
3. Restart the server.

Result:
- Vanish still works.
- No-clip/Mode5-like behavior is disabled.
- Movement falls back to normal collision behavior.

## In-game fallback

If the server is already running:

1. Open admin vanish settings.
2. Turn **Spectator NoClip** to **OFF**.
3. Re-toggle vanish for affected admins.

## Emergency rollback note

If you are testing future Mode5 code and it breaks:
- Keep `vanish_noclip_enabled` set to `false` until fixes are applied.
- Do not force-enable experimental ghost movement on production.

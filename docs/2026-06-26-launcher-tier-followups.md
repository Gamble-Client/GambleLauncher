# 2026-06-26 Launcher And Tier Follow-Ups

Source prompt cleaned into implementation notes.

## Launcher UI

- Fix launcher checkboxes; they currently do not work from the frontend.
- When a profile picture is detected, do not continue showing the first-letter placeholder over the image.
- Check whether the launcher sees launcher/client updates correctly before changing update logic. The launcher currently appears to show a latest version lower than the real newest version, but verify first.
- In the Profiles menu, change `Global default ({username})` to `Default ({username})`.
- Remove the bottom-left `Latest Launcher` status block.
- In the profile detail page, remove the repeated profile-name headings above the Mods block and Resource Packs block.
- In the launcher friends list, display the Minecraft skin/head for each friend.
- On the launcher front page, display the Gamble Client profile picture for the signed-in user.

## Tier Repository Cleanup

- Clean up the dirty tier folders in the way that best fits the codebase.
- Preferred direction from prior reading: combine into one source folder, then keep four separate tier lists/configs so one folder can build the tiers while preserving intentional tier drift.

## Notes

- Do not change update code if the verified live metadata and launcher code path already prove it should work.
- Keep intentional tier differences explicit, documented, and build-time controlled rather than spread across separate dirty folders.

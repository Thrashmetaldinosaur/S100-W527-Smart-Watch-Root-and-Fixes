# S100 YouTube Fullscreen Fix v1.0 — Release Notes

**Validated by an S100 owner on 2026-10-13.**

Stable features: portrait-only uncropped fullscreen video; centered letterboxing; play/pause; ±10s seek; draggable progress; 4-second auto-hide with bottom-band tap to reveal; YouTube captions mirrored in lower black band; stable mini-player behavior after multiple fullscreen/minimize transitions.

**Compatibility:** S100 W527 / XTQ_Watch, crDroid 9.3 Android 13, LSPosed, YouTube 21.40.161. Other devices and YouTube releases untested.

**Diff vs v0.3.2:** only release version/branding, a quieter diagnostic logger, Windows install/test scripts, and documentation. The view geometry, compositor z-order, captions, control panel, media-session seeking, and restore logic are unchanged.

**Install:** build from source on the tested Windows machine with `BUILD-INSTALL-AND-TEST.ps1`. No APK or proprietary YouTube files are redistributed. Use the stock Android `am start` launch command, never Monkey.

**Rollback:** disable the module in LSPosed or reinstall the prior working v0.3.2 with the same signing key.

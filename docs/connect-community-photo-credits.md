# BLINK Connect: illustrative category photos

These photos are **illustrative topic thumbnails**, not user-uploaded covers,
documentary photos of a Nigerian university, or evidence that a community exists.
The interface shows real membership counts only for actual study circles returned
by the authenticated Supabase `get_study_circles_page` RPC. The empty-state
categories say **Explore topic**, not "Join" or "X members".

Photos use Unsplash's free license for commercial and noncommercial use:
https://unsplash.com/license

- Music: Magnus Lunay, concert audience, https://unsplash.com/photos/a-group-of-people-standing-on-top-of-a-stage-O3P0A-TiAdE
  CDN key: `photo-1648160091944-54aab390a7ae`.
- Sports: Jose Castillo, outdoor basketball hoop at sunset,
  https://unsplash.com/photos/a-basketball-hoop-with-a-sunset-in-the-background-XFi7CWpITSU
  CDN key: `photo-1651680550893-f676f4801b64`.
- Study Buddies: Unsplash photograph of students collaborating at a table;
  CDN key: `photo-1522202176988-66273c2fd55f`.
- Photography: Unsplash photograph used to illustrate photography/videography;
  CDN key: `photo-1492691527719-9d1e07e534b4`.

These four fixed URLs are resized at the CDN to width=260, height=320,
quality=65 to limit mobile data use. Coil handles memory/disk caching.
If an image is unavailable, the underlying illustration and text remain usable.
Production content must not hotlink private student photos or substitute someone
else's photos for an actual group's own cover.

Future real covers require authenticated ownership/moderation, secure storage
and a versioned backend change tested in a staged Supabase environment first.
Do not deploy such a migration without the necessary staging validation.

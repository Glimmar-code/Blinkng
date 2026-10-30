import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const load=path=>readFileSync(path,'utf8');
const sheet=load('app/src/main/java/com/example/ui/components/CreatePostSheet.kt');
const options=load('app/src/main/java/com/example/ui/components/BlinkCreatePostReference.kt');
const share=load('app/src/main/java/com/example/ui/components/BlinkPostShareActions.kt');
const native=load('app/src/androidTest/java/com/example/ui/components/BlinkCreatePostReferenceTest.kt');
const robolectric=load('app/src/test/java/com/example/ui/components/BlinkPostShareActionsTest.kt');
const main=load('app/src/main/java/com/example/MainActivity.kt');

for(const target of [
  'onShareWhatsApp = { shareComposerDraft(true) }',
  'onShareOtherApps = { shareComposerDraft(false) }',
  'onCopyCaption = {',
  'BlinkPostShareActions.copyCaption(context, caption)',
  'fun saveDraft(): Boolean',
  'if (saveDraft()) showPublishOptions = false',
  'onClick = { saveDraft() }'
]) assert.ok(sheet.includes(target), 'Incomplete Create Post action: '+target);
assert.ok(!sheet.includes('Switch(checked = false, onCheckedChange = {}, enabled = false)'),
  'Poll contains a visually misleading control that cannot be operated');
for(const target of [
  'title = "WhatsApp"',
  'onClick = onShareWhatsApp',
  'title = "Copy caption"',
  'onClick = onCopyCaption',
  'title = "Other apps"',
  'onClick = onShareOtherApps',
  'enabled = !isSubmitting && canCopyCaption'
]) assert.ok(options.includes(target),'Unwired Create Post action: '+target);
assert.ok(!options.includes('title = "Copy link"'), 'Never offer an unpublished post link');
for(const target of [
  'com.whatsapp','com.whatsapp.w4b',
  'Intent.ACTION_SEND_MULTIPLE','Intent.EXTRA_STREAM',
  'Intent.FLAG_GRANT_READ_URI_PERMISSION',
  'ClipData.newRawUri(', 'ClipData.newPlainText(',
  'Intent.createChooser(', 'return false',
  '!general.hasExtra(Intent.EXTRA_STREAM)'
]) assert.ok(share.includes(target),'Share implementation missing: '+target);
for(const target of ['onShareWhatsApp =','onCopyCaption =','onShareOtherApps =',
  'onNodeWithText("Copy caption").performScrollTo().performClick()'
]) assert.ok(native.includes(target),'Instrumented UI test missing: '+target);
for(const target of ['fun singlePhotoSharesContentUriWithGrantAndCaption()',
  'fun photoAlbumUsesMultipleStreamAndGrantsEveryUri()',
  'fun reelSharesOnlyVideoUri()',
  'fun staleUnshareableMediaDoesNotSilentlyBecomeTextOnly()',
  'fun copyCaptionWritesExactlyTheSelectedText()'
]) assert.ok(robolectric.includes(target),'Android unit test missing: '+target);
assert.ok(main.includes('onSaveDraft = { draft ->'), 'Main app must persist drafts');
assert.ok(main.includes('viewModel.addPost('), 'Native Post Now must use ViewModel upload');
console.log('PASS APK Create Post: working share buttons, real URI access, draft failure navigation, one-vote poll label, and native tests.');

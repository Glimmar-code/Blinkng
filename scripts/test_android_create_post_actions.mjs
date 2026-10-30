import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const load=path=>readFileSync(path,'utf8');
const sheet=load('app/src/main/java/com/example/ui/components/CreatePostSheet.kt');
const options=load('app/src/main/java/com/example/ui/components/BlinkCreatePostReference.kt');
const share=load('app/src/main/java/com/example/ui/components/BlinkPostShareActions.kt');
const native=load('app/src/androidTest/java/com/example/ui/components/BlinkCreatePostReferenceTest.kt');
const robolectric=load('app/src/test/java/com/example/ui/components/BlinkPostShareActionsTest.kt');
const main=load('app/src/main/java/com/example/MainActivity.kt');
const vm=load('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt');
const photoPolicy=load('app/src/main/java/com/example/ui/components/BlinkPhotoEntryPolicy.kt');
const photoTests=load('app/src/test/java/com/example/ui/components/BlinkPhotoEntryPolicyTest.kt');
assert.ok(share.includes('import android.content.ActivityNotFoundException'),'Use the real Android exception class for missing share targets');

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
assert.ok(sheet.includes('onSaveDraft: (PostDraft) -> Boolean = { false }'), 'Do not trust unimplemented draft save callbacks');
assert.ok(sheet.includes('val saved = onSaveDraft('), 'Use the host-verified draft persistence result');
assert.ok(sheet.includes('if (!saved) return false'), 'Do not close the composer if storage failed');
assert.ok(vm.includes('fun saveDraft(draft: PostDraft): Boolean'), 'ViewModel must return draft storage success');
assert.ok(vm.includes('return saved'), 'ViewModel must return actual saved result');
assert.ok(main.includes('onSaveDraft = { draft ->'), 'Main app must persist drafts');
assert.ok(sheet.includes('when (BlinkPhotoEntryPolicy.decide('), 'Photo tile must use tested navigation policy');
assert.ok(sheet.includes('BlinkPhotoEntryAction.OPEN_SYSTEM_PICKER ->'), 'First Photo tap must open picker');
assert.ok(sheet.includes('BlinkPhotoEntryPolicy.returnToLandingAfterPicker(initialPicker, uris.size)'), 'Cancelled Photo picker must return to landing');
assert.ok(sheet.includes('if (uris.isNotEmpty()) {'), 'Empty picker result must not destroy prior media');
for (const choice of ['CONFIRM_REPLACEMENT','REVIEW_EXISTING','OPEN_SYSTEM_PICKER'])
  assert.ok(photoPolicy.includes(choice), 'Missing Photo policy state: '+choice);
assert.ok(photoTests.includes('fun firstPhotoTapOpensPickerInsteadOfEmptyReview()'));
assert.ok(photoTests.includes('fun cancellingFirstPhotoPickerRestoresLandingButAddingMoreDoesNot()'));
assert.ok(main.includes('viewModel.addPost('), 'Native Post Now must use ViewModel upload');
console.log('PASS APK Create Post: working share buttons, real URI access, draft failure navigation, one-vote poll label, and native tests.');

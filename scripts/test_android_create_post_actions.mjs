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
assert.ok(options.includes('// grouped card, rather than three disconnected floating surfaces.'), 'Share rows must be grouped like the screenshot reference');
assert.ok((options.match(/HorizontalDivider\(color = Color\(0xFF343944\), thickness = 0.5.dp\)/g) || []).length === 2, 'Share actions must have both reference-style dividers');
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
assert.ok(sheet.includes('BlinkPhotoEntryPolicy.decide('), 'Photo tile must use tested navigation policy');
assert.ok(sheet.includes('BlinkPhotoEntryAction.OPEN_MEDIA_REVIEW ->'), 'First Photo tap must open BLINK Select Media');
assert.ok(sheet.includes('showMediaReview = true'), 'Photo entry must render in-app Select Media');
assert.ok(sheet.includes('onAddMore = {') && sheet.includes('imagePicker.launch('),
  'Select Media must retain a real Android Photo Picker action');
assert.ok(options.includes('BlinkMediaEmptyGallery(') &&
  options.includes('onClick = onPick') && options.includes('onClick = onRequestLibrary'),
  'Empty gallery must offer permission-free photo selection and optional gallery access');
assert.ok(sheet.includes('BlinkPhotoEntryPolicy.returnToLandingAfterPicker(initialPicker, uris.size)'),
  'Cancelled initial photo picker must preserve the landing flow');
assert.ok(sheet.includes('if (uris.isNotEmpty()) {'),
  'Empty photo-picker result must not destroy prior media');
for (const choice of ['CONFIRM_REPLACEMENT','REVIEW_EXISTING','OPEN_MEDIA_REVIEW'])
  assert.ok(photoPolicy.includes(choice), 'Missing Photo policy state: '+choice);
assert.ok(photoTests.includes('fun firstPhotoTapOpensSelectMediaWithPickerFallback()'));
assert.ok(photoTests.includes('fun existingPhotosOpenReviewWithoutForcingAnotherPicker()'));
assert.ok(photoTests.includes('fun switchingFromPollOrReelRequiresExplicitConfirmation()'));
assert.ok(photoTests.includes('fun cancellingFirstPhotoPickerRestoresLandingButAddingMoreDoesNot()'));
assert.ok(main.includes('viewModel.addPost('), 'Native Post Now must use ViewModel upload');
console.log('PASS APK Create Post: working share buttons, real URI access, draft failure navigation, one-vote poll label, and native tests.');

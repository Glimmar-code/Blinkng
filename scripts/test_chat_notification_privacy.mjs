import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read=path=>readFileSync(path,'utf8');
const prefs=read('app/src/main/java/com/example/notification/NotificationPreferenceStore.kt');
const policy=read('app/src/main/java/com/example/notification/ChatNotificationPreviewPolicy.kt');
const instant=read('app/src/main/java/com/example/notification/InstantChatNotification.kt');
const firebase=read('app/src/main/java/com/example/notification/BlinkFirebaseMessagingService.kt');
const helper=read('app/src/main/java/com/example/notification/BlinkNotificationHelper.kt');
const viewmodel=read('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt');
const repository=read('app/src/main/java/com/example/data/repository/ChatRepository.kt');

for (const marker of [
  'fun chatPreviewMode(context: Context)',
  'fun rememberChatPreviewMode(context: Context, accountId: String, mode: String)',
  'if (accountId.isBlank()) return "hidden"',
  'if (accountId != currentId) return false',
  'ChatNotificationPreviewPolicy.normalized(mode)',
  'NotificationMessageHistory.clear()',
  'shown.notification.channelId == BlinkNotificationHelper.CHANNEL_MESSAGES',
  'manager.cancel(shown.tag, shown.id)'
]) assert.ok(prefs.includes(marker), 'Missing account isolation: '+marker);

for (const marker of ['"sender_only"', '"hidden"', '"full"', 'else -> "hidden"', 'fun allowHistory']) {
  assert.ok(policy.includes(marker),'Missing preview policy: '+marker);
}

for(const marker of [
  'NotificationPreferenceStore.isAllowed(context, BlinkNotificationType.MESSAGE)',
  'ConversationNotificationMuteStore.isMuted(context, conversationId)',
  'NotificationPreferenceStore.chatPreviewMode(context)',
  'ChatNotificationPreviewPolicy.title(previewMode, senderName)',
  'ChatNotificationPreviewPolicy.body(previewMode, messageText)',
  'ChatNotificationPreviewPolicy.allowHistory(previewMode)',
  'NotificationMessageHistory.clear()',
  '.setContentTitle(displayTitle)',
  '.setContentText(displayText)',
]) assert.ok(instant.includes(marker),'Unprotected instant notification: '+marker);

for(const marker of [
  'val visibleTitle = if (type == BlinkNotificationType.MESSAGE)',
  'val visibleBody = if (type == BlinkNotificationType.MESSAGE)',
  'title = visibleTitle,',
  'body = visibleBody,',
]) assert.ok(firebase.includes(marker),'Foreground in-app notification may leak: '+marker);

const showLegacy=helper.split('fun showChatMessageNotification(')[1]?.split('MULTI-MESSAGE CHAT SUMMARY')[0]||'';
assert.ok(showLegacy.includes('InstantChatNotification.show('),'Legacy notifier bypasses privacy policy');
assert.ok(!showLegacy.includes('.setContentText(messageText)'),'Legacy notifier still shows a raw body');

assert.ok(viewmodel.includes('NotificationPreferenceStore.rememberChatPreviewMode('),'Chat settings must update push privacy locally');
assert.ok(viewmodel.includes('chatPrivacySaveMutex.withLock'),'Concurrent privacy settings writes must be serialized');
assert.ok(viewmodel.includes('ChatNotificationPreviewPolicy.disclosureRank(previousPreview)'),'More permissive notification previews must wait for server confirmation');
assert.ok(repository.includes('if (!response.isSuccessful) throw IllegalStateException('),'Failed privacy reads must not act like confirmed defaults');
assert.ok(repository.includes('}.getOrThrow()'),'A failed privacy fetch must propagate to retain previous settings');
console.log('Android chat notification privacy paths are wired to one account-specific policy.');

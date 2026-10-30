import fs from 'node:fs';
import assert from 'node:assert/strict';

const api=fs.readFileSync(new URL('../app/src/main/java/com/example/data/supabase/SupabaseService.kt',import.meta.url),'utf8');
const ui=fs.readFileSync(new URL('../app/src/main/java/com/example/ui/screens/MarketOffersPanel.kt',import.meta.url),'utf8');
const fetchOffer=api.match(/suspend fun fetchMyMarketOffers\(offset: Int = 0, limit: Int = 25\)[\s\S]*?\n    \}/)?.[0];
assert.ok(fetchOffer,'Market offer fetch must accept pagination arguments');
for(const required of ['offset.coerceAtLeast(0)','limit.coerceIn(1, 100)','&offset=$safeOffset','&limit=$safeLimit','order=created_at.desc,id.desc']) {
  assert.ok(fetchOffer.includes(required),`Server pagination lacks ${required}`);
}
assert.ok(!ui.includes('offers.take(12)'),'No first-12 display cap allowed');
assert.ok(ui.includes('service.fetchMyMarketOffers(offset = offset, limit = pageSize)'));
assert.ok(ui.includes('nextOffset = offset + rows.length()'),'Offset must advance by fetched count, not deduplicated cards');
assert.ok(ui.includes('Load older offers'),'Older offers must have an action');
assert.ok(ui.includes('offers + page.filter'),'Paging must append, not erase previous offers');
assert.ok(ui.includes('if (loadFailure)'),'Only failed fetches should display a retry-loading action');
assert.ok(ui.includes('error = failure'),'Rejected offer actions must retain feedback');
assert.ok(ui.includes('if (service.respondMarketOffer(id, action, counterAmount))'),'Refresh must happen on confirmed action success');
console.log('Offer history pagination and rejected-action feedback source contracts passed.');

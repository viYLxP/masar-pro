// Fails when a template or component uses a static translation key that is missing from the English dictionary.
// يفشل لما قالب أو مكوّن يستخدم مفتاح ترجمة ثابت غير موجود في القاموس الإنجليزي.
// Dynamic keys such as 'movementType.' + m.type are skipped; the dictionary parity unit test covers the rest.
// المفاتيح الديناميكية مثل 'movementType.' + m.type يتم تجاهلها؛ واختبار تطابق القواميس يغطي الباقي.
// Run: node --experimental-strip-types scripts/check-i18n-usage.mjs
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const { en } = await import('../src/app/core/i18n/en.ts');

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else if (/\.(html|ts)$/.test(name) && !name.endsWith('.spec.ts') && !path.includes('i18n')) yield path;
  }
}

const has = (key) => key.split('.').reduce((o, k) => (o && typeof o === 'object' ? o[k] : undefined), en) !== undefined;
// 'a.b' | t   ·   t('a.b'   ·   'a.b' : 'c.d') | t (ternaries inside a pipe expression)
// 'a.b' | t   ·   t('a.b'   ·   'a.b' : 'c.d') | t (الشروط الثلاثية داخل تعبير الـ pipe)
const patterns = [/'([a-zA-Z]+\.[a-zA-Z.]+)'\s*\)?\s*\|\s*t\b/g, /\bt\(\s*'([a-zA-Z]+\.[a-zA-Z.]+)'/g, /\?\s*'([a-zA-Z]+\.[a-zA-Z.]+)'\s*:\s*'([a-zA-Z]+\.[a-zA-Z.]+)'/g];

const missing = [];
let checked = 0;
for (const file of files('src/app')) {
  const text = readFileSync(file, 'utf8');
  for (const re of patterns) {
    for (const m of text.matchAll(re)) {
      for (const key of m.slice(1).filter(Boolean)) {
        // Keys ending in "." are prefixes of dynamic keys.
        // المفاتيح المنتهية بنقطة هي بدايات لمفاتيح ديناميكية.
        if (key.endsWith('.')) continue;
        checked++;
        if (!has(key)) missing.push(`${file}: ${key}`);
      }
    }
  }
}
if (missing.length) {
  console.error(`Missing translation keys (${missing.length}):\n` + [...new Set(missing)].join('\n'));
  process.exit(1);
}
console.log(`OK: ${checked} static translation keys found in the dictionary.`);

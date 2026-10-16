const fs = require('fs');
const path = require('path');

function getJavaFiles(dir) {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach(file => {
    const full = path.join(dir, file);
    const stat = fs.statSync(full);
    if (stat && stat.isDirectory()) {
      results = results.concat(getJavaFiles(full));
    } else if (file.endsWith('.java')) {
      results.push(full);
    }
  });
  return results;
}

const files = getJavaFiles('backend/src/main/java');
console.log('Total Java files:', files.length);

files.forEach(f => {
  const content = fs.readFileSync(f, 'utf8');
  const lines = content.split('\n');
  const imports = [];
  lines.forEach((l, idx) => {
    const trimmed = l.trim();
    if (trimmed.startsWith('import ') && !trimmed.endsWith('.*;')) {
      const match = trimmed.match(/import\s+(?:static\s+)?([a-zA-Z0-9_\.]+);/);
      if (match) {
        const fullImport = match[1];
        const className = fullImport.split('.').pop();
        imports.push({ line: idx + 1, fullImport, className, raw: trimmed });
      }
    }
  });

  // check if className appears elsewhere in the file
  const unused = [];
  imports.forEach(imp => {
    // count occurrences of className in content
    // we want word boundary
    const regex = new RegExp('\\b' + imp.className + '\\b', 'g');
    const matches = content.match(regex) || [];
    // if matches <= 1 (only in the import line), it's unused!
    if (matches.length <= 1) {
      unused.push(imp);
    }
  });

  if (unused.length > 0) {
    console.log('\nFile:', f);
    unused.forEach(u => console.log(`  Line ${u.line}: ${u.raw}`));
  }
});

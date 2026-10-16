const fs = require('fs');
const p = 'C:/Users/PC/AppData/Roaming/Code/User/workspaceStorage/22ff4e14b69116c36664b73fc7618298/state.vscdb';

const buf = fs.readFileSync(p);
// Find all JSON strings
let str = '';
const found = [];
for (let i = 0; i < buf.length; i++) {
  const b = buf[i];
  if (b >= 32 && b <= 126) {
    str += String.fromCharCode(b);
  } else {
    if (str.length > 20) {
      if (str.includes('{') || str.includes('warning') || str.includes('problem') || str.includes('markers')) {
        found.push(str);
      }
    }
    str = '';
  }
}

console.log('Found', found.length, 'entries');
found.forEach(s => {
  if (s.includes('warning') || s.includes('marker') || s.includes('problem') || s.includes('severity')) {
    console.log('--- MATCH ---');
    console.log(s.slice(0, 300));
  }
});

const fs = require('fs');
const path = require('path');

function searchMarkers(dir) {
  const files = fs.readdirSync(dir, { withFileTypes: true });
  for (const f of files) {
    const full = path.join(dir, f.name);
    if (f.isDirectory()) {
      try { searchMarkers(full); } catch(e){}
    } else if (f.name === '.markers') {
      try {
        const buf = fs.readFileSync(full);
        let str = '';
        const strings = [];
        for (let i = 0; i < buf.length; i++) {
          const b = buf[i];
          if (b >= 32 && b <= 126) {
            str += String.fromCharCode(b);
          } else {
            if (str.length >= 4) strings.push(str);
            str = '';
          }
        }
        const msgs = strings.filter(s => s.startsWith('message') || s.includes('is not used') || s.includes('never used') || s.includes('deprecated'));
        console.log('File:', full);
        console.log('Messages:', msgs);
      } catch(e){}
    }
  }
}

searchMarkers('C:/Users/PC/AppData/Roaming/Code/User/workspaceStorage/22ff4e14b69116c36664b73fc7618298');

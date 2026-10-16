const fs = require('fs');
const path = 'C:/Users/PC/AppData/Roaming/Code/User/workspaceStorage/22ff4e14b69116c36664b73fc7618298/redhat.java/jdt_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/tottus-inventario/.markers';

if (fs.existsSync(path)) {
  const buf = fs.readFileSync(path);
  let str = '';
  const strings = [];
  for (let i = 0; i < buf.length; i++) {
    const b = buf[i];
    if (b >= 32 && b <= 126) {
      str += String.fromCharCode(b);
    } else {
      if (str.length >= 4) {
        strings.push(str);
      }
      str = '';
    }
  }
  console.log('Strings in jdt_ws markers:', strings);
} else {
  console.log('Does not exist');
}
